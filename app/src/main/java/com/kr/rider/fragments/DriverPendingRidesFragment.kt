package com.kr.rider.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.kr.rider.R
import com.kr.rider.adapter.DriverPendingRideAdapter
import com.kr.rider.model.RideModel
import com.kr.rider.viewmodel.DriverPendingRidesViewModel
import com.kr.rider.utils.DistanceUtils

class DriverPendingRidesFragment : Fragment() {

    private val TAG = "DriverPendingRidesFrag"
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: DriverPendingRideAdapter
    private val viewModel: DriverPendingRidesViewModel by viewModels()
    private var driverId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_pending_rides, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)

        if (driverId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "✅ Driver ID: $driverId")

        setupRecyclerView()
        setupObservers()

        viewModel.loadPendingRides(driverId!!)
    }

    override fun onResume() {
        super.onResume()
        driverId?.let { viewModel.loadPendingRides(it) }
    }

    private fun setupRecyclerView() {
        adapter = DriverPendingRideAdapter(
            rides = emptyList(),
            onCalculateFare = { ride ->
                Log.d(TAG, "💰 Calculate Fare: ${ride.rideId}")
                calculateFareForRide(ride)
            },
            onAccept = { ride ->
                Log.d(TAG, "✅ Accept: ${ride.rideId}")
                acceptRide(ride)
            },
            onReject = { ride ->
                Log.d(TAG, "❌ Reject: ${ride.rideId}")
                viewModel.updateRideStatus(ride.rideId, "CANCELLED") { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "❌ Ride Rejected", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onArrivedPickup = { ride ->
                Log.d(TAG, "📍 Arrived at pickup: ${ride.rideId}")
                val pickupPin = (1000..9999).random().toString()
                viewModel.updateRideWithPin(
                    rideId = ride.rideId,
                    status = "ARRIVED_PICKUP",
                    pickupPin = pickupPin,
                    pickupTime = Timestamp.now()
                ) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "📍 Arrived! PIN: $pickupPin", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSubmitPin = { ride, enteredPin ->
                Log.d(TAG, "🔑 Submit PIN: ${ride.rideId}, PIN: $enteredPin")
                if (ride.pickupPin == enteredPin) {
                    viewModel.updateRideStatus(ride.rideId, "ON_THE_WAY") { success ->
                        if (success) {
                            Toast.makeText(requireContext(), "✅ PIN Verified! Ride Started!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "❌ Invalid PIN! Please try again.", Toast.LENGTH_SHORT).show()
                }
            },
            onArrivedDestination = { ride ->
                Log.d(TAG, "📍 Destination Reached: ${ride.rideId}")
                val completePin = (1000..9999).random().toString()
                viewModel.updateRideWithCompletePin(
                    rideId = ride.rideId,
                    status = "DESTINATION_REACHED",
                    completePin = completePin
                ) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "📍 Destination Reached! Complete PIN: $completePin", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSubmitCompletePin = { ride, enteredPin ->
                Log.d(TAG, "🔑 Complete PIN: ${ride.rideId}, PIN: $enteredPin")
                viewModel.completeRideWithPin(
                    rideId = ride.rideId,
                    enteredPin = enteredPin
                ) { success ->
                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            "✅ Ride Completed! Fare: ₹${DistanceUtils.formatFareInt(ride.totalFare)}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), "❌ Invalid PIN! Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun calculateFareForRide(ride: RideModel) {
        val driverId = driverId ?: ""
        val areaId = ride.areaId
        val pickupLat = ride.pickup?.lat ?: 0.0
        val pickupLng = ride.pickup?.lng ?: 0.0
        val destLat = ride.destination?.lat ?: 0.0
        val destLng = ride.destination?.lng ?: 0.0

        if (areaId.isEmpty() || pickupLat == 0.0 || destLat == 0.0) {
            Toast.makeText(requireContext(), "❌ Missing location data", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.calculateFareForRide(
            rideId = ride.rideId,
            driverId = driverId,
            areaId = areaId,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            destLat = destLat,
            destLng = destLng
        ) { success ->
            if (success) {
                Toast.makeText(
                    requireContext(),
                    "✅ Fare calculated: ₹${DistanceUtils.formatFareInt(ride.totalFare)}",
                    Toast.LENGTH_LONG
                ).show()
                // ✅ Refresh list immediately (Yahan sahi hai, kyunki calculate par naya data chahiye)
                driverId?.let { viewModel.loadPendingRides(it) }
            } else {
                Toast.makeText(requireContext(), "❌ Failed to calculate fare", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun acceptRide(ride: RideModel) {
        val driverId = driverId ?: ""

        if (!ride.fareCalculated || ride.totalFare <= 0) {
            Toast.makeText(requireContext(), "⚠️ Please calculate fare first!", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Store fare value locally
        val acceptedFare = ride.totalFare

        viewModel.fetchDriverDetails(driverId) { name, phone, vehicle, vehicleNumber ->
            viewModel.updateRideWithDriverDetails(
                rideId = ride.rideId,
                status = "ACCEPTED",
                driverName = name,
                driverPhone = phone,
                driverVehicle = vehicle,
                driverVehicleNumber = vehicleNumber
            ) { success ->
                if (success) {
                    // ❌ REMOVED: driverId?.let { viewModel.loadPendingRides(it) } (No Firebase fetch)

                    // ✅ Update Local List (So UI stays perfect without flickering)
                    val currentList = adapter.rides.toMutableList()
                    val index = currentList.indexOfFirst { it.rideId == ride.rideId }
                    if (index != -1) {
                        val updatedRide = currentList[index].copy(
                            status = "ACCEPTED",
                            driverName = name,
                            driverPhone = phone,
                            driverVehicle = vehicle,
                            driverVehicleNumber = vehicleNumber,
                            // Fare values remain exactly the same as calculated!
                            totalFare = acceptedFare,
                            fareCalculated = true
                        )
                        currentList[index] = updatedRide
                        adapter.updateRides(currentList) // Update adapter with local data
                    }

                    Toast.makeText(
                        requireContext(),
                        "✅ Ride Accepted! Fare: ₹${DistanceUtils.formatFareInt(acceptedFare)}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "❌ Failed to update ride", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.rides.observe(viewLifecycleOwner, Observer { rides ->
            Log.d(TAG, "📋 LiveData update: ${rides.size} rides")
            rides.forEach {
                Log.d(TAG, "   - ${it.rideId}: ${it.status}, Fare: ₹${it.totalFare}")
            }
            adapter.updateRides(rides)
            tvEmpty.visibility = if (rides.isEmpty()) View.VISIBLE else View.GONE
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Log.e(TAG, "❌ Error: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearError()
    }
}