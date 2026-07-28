package com.kr.rider.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore

class DriverAuthViewModel : ViewModel() {

    companion object {
        private const val TAG = "DriverAuthViewModel"
    }

    private val db = FirebaseFirestore.getInstance()

    // LiveData
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loginSuccess = MutableLiveData(false)
    val loginSuccess: LiveData<Boolean> = _loginSuccess

    private val _driverData = MutableLiveData<DriverData?>(null)
    val driverData: LiveData<DriverData?> = _driverData

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * ✅ Verify Driver Login with Phone + PIN
     */
    fun verifyDriverLogin(phone: String, pin: String) {
        if (phone.isEmpty()) {
            _errorMessage.value = "Please enter phone number"
            return
        }

        if (pin.isEmpty() || pin.length < 4) {
            _errorMessage.value = "Please enter valid PIN (4-6 digits)"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        db.collection("drivers")
            .whereEqualTo("phone", phone)
            .whereEqualTo("pin", pin)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                _isLoading.value = false

                if (documents.isEmpty()) {
                    _errorMessage.value = "❌ Invalid phone number or PIN"
                    return@addOnSuccessListener
                }

                val doc = documents.first()
                val driverId = doc.id
                val driverName = doc.getString("name") ?: "Driver"
                val driverPhone = doc.getString("phone") ?: ""
                val vehicleType = doc.getString("vehicleType") ?: "Car"
                val vehicleNumber = doc.getString("vehicleNumber") ?: ""
                val walletBalance = doc.getDouble("walletBalance") ?: 0.0
                val totalRides = doc.getLong("totalRides")?.toInt() ?: 0
                val totalEarnings = doc.getDouble("totalEarnings") ?: 0.0
                val rating = doc.getDouble("rating") ?: 0.0

                _driverData.value = DriverData(
                    driverId = driverId,
                    name = driverName,
                    phone = driverPhone,
                    vehicleType = vehicleType,
                    vehicleNumber = vehicleNumber,
                    walletBalance = walletBalance,
                    totalRides = totalRides,
                    totalEarnings = totalEarnings,
                    rating = rating
                )

                _loginSuccess.value = true
                Log.d(TAG, "✅ Login successful: $driverName ($driverId)")

                // ✅ Save session
                saveDriverSession(driverId, driverName, driverPhone)
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Error: ${e.message}"
                Log.e(TAG, "❌ Login failed: ${e.message}")
            }
    }

    /**
     * ✅ Save driver session in SharedPreferences
     */
    private fun saveDriverSession(driverId: String, name: String, phone: String) {
        // Session will be saved in fragment
        // We'll pass data via Fragment
    }

    /**
     * ✅ Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * ✅ Reset login state
     */
    fun resetLoginState() {
        _loginSuccess.value = false
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 ViewModel cleared")
    }
}

/**
 * ✅ Data class for Driver
 */
data class DriverData(
    val driverId: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicleType: String = "",
    val vehicleNumber: String = "",
    val walletBalance: Double = 0.0,
    val totalRides: Int = 0,
    val totalEarnings: Double = 0.0,
    val rating: Double = 0.0
)