package com.kr.rider.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.kr.rider.model.RideModel
import com.kr.rider.utils.DistanceUtils

class DriverPendingRidesViewModel : ViewModel() {

    private val TAG = "PendingRidesVM"
    private val db = FirebaseFirestore.getInstance()

    private val _rides = MutableLiveData<List<RideModel>>(emptyList())
    val rides: LiveData<List<RideModel>> = _rides

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null

    // ✅ Load rides with real-time listener
    fun loadPendingRides(driverId: String) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "Driver ID is empty"
            return
        }

        _isLoading.value = true
        Log.d(TAG, "🔄 Loading rides for: $driverId")

        listener?.remove()
        listener = db.collection("rides")
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf(
                "DRIVER_ASSIGNED",
                "ACCEPTED",
                "ARRIVED_PICKUP",
                "ON_THE_WAY",
                "DESTINATION_REACHED",
                "STARTED"
            ))
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val rides = snapshots?.documents?.mapNotNull { document ->
                    val ride = document.toObject<RideModel>()
                    ride?.copy(rideId = document.id)
                } ?: emptyList()

                Log.d(TAG, "📋 Rides: ${rides.size}")
                _rides.value = rides.sortedByDescending { it.createdAt?.toDate() }
                _isLoading.value = false
            }
    }

    // ✅ Update ride status
    fun updateRideStatus(rideId: String, status: String, callback: (Boolean) -> Unit) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "✅ Ride updated: $rideId → $status")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Update failed: ${e.message}")
                _errorMessage.value = "Failed to update: ${e.message}"
                callback(false)
            }
    }

    // ✅ Calculate Fare - FIXED: No hardcoded fallback
    fun calculateFareForRide(
        rideId: String,
        driverId: String,
        areaId: String,
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty() || driverId.isEmpty() || areaId.isEmpty()) {
            _errorMessage.value = "Missing required data"
            callback(false)
            return
        }

        db.collection("driver_locations").document(driverId)
            .get()
            .addOnSuccessListener { driverDoc ->
                if (!driverDoc.exists()) {
                    _errorMessage.value = "Driver location not found"
                    callback(false)
                    return@addOnSuccessListener
                }

                val currentLocation = driverDoc.getGeoPoint("currentLocation")
                if (currentLocation == null) {
                    _errorMessage.value = "Driver current location not available"
                    callback(false)
                    return@addOnSuccessListener
                }

                val driverLat = currentLocation.latitude
                val driverLng = currentLocation.longitude

                val pickupDistance = DistanceUtils.calculateDistance(
                    driverLat, driverLng, pickupLat, pickupLng
                )
                val tripDistance = DistanceUtils.calculateDistance(
                    pickupLat, pickupLng, destLat, destLng
                )
                val totalDistance = pickupDistance + tripDistance

                db.collection("areas").document(areaId)
                    .get()
                    .addOnSuccessListener { areaDoc ->
                        if (!areaDoc.exists()) {
                            _errorMessage.value = "Area not found"
                            callback(false)
                            return@addOnSuccessListener
                        }

                        // ✅ Firestore se vehicle rates fetch karein — NO HARDCODED FALLBACK
                        val vehicleRates = areaDoc.get("vehicleRates") as? Map<String, Any>
                        val bikeRates = vehicleRates?.get("bike") as? Map<String, Any>

                        val perKmRate = (bikeRates?.get("perKmRate") as? Number)?.toDouble() ?: 0.0
                        val basePrice = (bikeRates?.get("basePrice") as? Number)?.toDouble() ?: 0.0

                        Log.d(TAG, "📊 Firestore: basePrice=$basePrice, perKmRate=$perKmRate")

                        if (basePrice == 0.0 || perKmRate == 0.0) {
                            _errorMessage.value = "Vehicle rates not configured for this area"
                            callback(false)
                            return@addOnSuccessListener
                        }

                        val distanceFare = totalDistance * perKmRate
                        val totalFare = basePrice + distanceFare

                        val updates = mapOf(
                            "pickupDistance" to pickupDistance,
                            "tripDistance" to tripDistance,
                            "totalDistance" to totalDistance,
                            "perKmRate" to perKmRate,
                            "basePrice" to basePrice,
                            "distanceFare" to distanceFare,
                            "totalFare" to totalFare,
                            "fareCalculated" to true,
                            "updatedAt" to Timestamp.now()
                        )

                        db.collection("rides").document(rideId)
                            .update(updates)
                            .addOnSuccessListener {
                                Log.d(TAG, "✅ Fare calculated: ₹$totalFare (base=$basePrice, perKm=$perKmRate, dist=$totalDistance)")
                                callback(true)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "❌ Save failed: ${e.message}")
                                _errorMessage.value = "Failed to save fare: ${e.message}"
                                callback(false)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Area fetch failed: ${e.message}")
                        _errorMessage.value = "Failed to fetch area: ${e.message}"
                        callback(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Driver location fetch failed: ${e.message}")
                _errorMessage.value = "Failed to fetch driver location: ${e.message}"
                callback(false)
            }
    }

    // ✅ Fetch Driver Details from drivers collection
    fun fetchDriverDetails(
        driverId: String,
        callback: (String, String, String, String) -> Unit
    ) {
        db.collection("drivers").document(driverId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: "Driver"
                    val phone = document.getString("phone") ?: "N/A"
                    val vehicleType = document.getString("vehicleType") ?: "Car"
                    val vehicleModel = document.getString("vehicleModel") ?: ""
                    val vehicleNumber = document.getString("vehicleNumber") ?: "N/A"

                    val vehicle = if (vehicleModel.isNotEmpty()) {
                        "$vehicleType $vehicleModel"
                    } else {
                        vehicleType
                    }

                    Log.d(TAG, "✅ Driver Details: $name, $phone, $vehicle, $vehicleNumber")
                    callback(name, phone, vehicle, vehicleNumber)
                } else {
                    Log.e(TAG, "❌ Driver document not found")
                    callback("Driver", "N/A", "Car", "N/A")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to fetch driver: ${e.message}")
                callback("Driver", "N/A", "Car", "N/A")
            }
    }

    // ✅ Update Ride with Driver Details
    fun updateRideWithDriverDetails(
        rideId: String,
        status: String,
        driverName: String,
        driverPhone: String,
        driverVehicle: String,
        driverVehicleNumber: String,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        val updates = mapOf(
            "status" to status,
            "driverName" to driverName,
            "driverPhone" to driverPhone,
            "driverVehicle" to driverVehicle,
            "driverVehicleNumber" to driverVehicleNumber,
            "updatedAt" to Timestamp.now()
        )

        db.collection("rides").document(rideId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Ride updated with driver details")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update: ${e.message}")
                _errorMessage.value = "Failed to update ride: ${e.message}"
                callback(false)
            }
    }

    // ✅ Update ride with PIN
    fun updateRideWithPin(
        rideId: String,
        status: String,
        pickupPin: String,
        pickupTime: Timestamp,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        val updates = mapOf(
            "status" to status,
            "pickupPin" to pickupPin,
            "pickupTime" to pickupTime,
            "updatedAt" to Timestamp.now()
        )

        db.collection("rides").document(rideId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Arrived updated: $rideId")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed: ${e.message}")
                _errorMessage.value = "Failed to update: ${e.message}"
                callback(false)
            }
    }

    // ✅ Update ride with Complete PIN
    fun updateRideWithCompletePin(
        rideId: String,
        status: String,
        completePin: String,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        val updates = mapOf(
            "status" to status,
            "completePin" to completePin,
            "updatedAt" to Timestamp.now()
        )

        db.collection("rides").document(rideId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Destination Reached updated: $rideId")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed: ${e.message}")
                _errorMessage.value = "Failed to update: ${e.message}"
                callback(false)
            }
    }

    // ✅ Complete Ride with PIN
    fun completeRideWithPin(
        rideId: String,
        enteredPin: String,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        db.collection("rides").document(rideId)
            .get()
            .addOnSuccessListener { document ->
                val savedPin = document.getString("completePin")

                if (savedPin == enteredPin) {
                    val updates = mapOf(
                        "status" to "COMPLETED",
                        "completedAt" to Timestamp.now(),
                        "completeTime" to Timestamp.now(),
                        "updatedAt" to Timestamp.now()
                    )

                    db.collection("rides").document(rideId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ Ride Completed: $rideId")
                            callback(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ Complete failed: ${e.message}")
                            _errorMessage.value = "Failed to complete: ${e.message}"
                            callback(false)
                        }
                } else {
                    Log.d(TAG, "❌ Invalid PIN")
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Fetch failed: ${e.message}")
                _errorMessage.value = "Failed to fetch ride: ${e.message}"
                callback(false)
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}