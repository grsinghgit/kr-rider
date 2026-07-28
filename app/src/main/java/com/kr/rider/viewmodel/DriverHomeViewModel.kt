package com.kr.rider.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.kr.rider.model.DriverModel

class DriverHomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "DriverHomeViewModel"
    }

    private val db = FirebaseFirestore.getInstance()

    // LiveData
    private val _driverData = MutableLiveData<DriverModel?>(null)
    val driverData: LiveData<DriverModel?> = _driverData

    private val _walletBalance = MutableLiveData(0.0)
    val walletBalance: LiveData<Double> = _walletBalance

    private val _totalRides = MutableLiveData(0)
    val totalRides: LiveData<Int> = _totalRides

    private val _totalEarnings = MutableLiveData(0.0)
    val totalEarnings: LiveData<Double> = _totalEarnings

    private val _rating = MutableLiveData(0.0)
    val rating: LiveData<Double> = _rating

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * ✅ Load driver data from Firestore with real-time listener
     */
    fun loadDriverData(driverId: String) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "Driver ID is empty"
            return
        }

        _isLoading.value = true

        listener?.remove()
        listener = db.collection("drivers")
            .document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    _isLoading.value = false
                    Log.e(TAG, "❌ Error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val driver = snapshot.toObject<DriverModel>()
                    _driverData.value = driver

                    driver?.let {
                        _walletBalance.value = it.walletBalance ?: 0.0
                        _totalRides.value = it.totalRides ?: 0
                        _totalEarnings.value = it.totalEarnings ?: 0.0
                        _rating.value = it.rating ?: 0.0
                        Log.d(TAG, "✅ Driver data loaded: ${it.name}")
                    }
                } else {
                    _errorMessage.value = "Driver not found"
                    Log.e(TAG, "❌ Driver not found: $driverId")
                }
                _isLoading.value = false
            }
    }

    /**
     * ✅ Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
        Log.d(TAG, "🧹 ViewModel cleared")
    }
}