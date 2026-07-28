package com.kr.rider.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class AuthViewModel : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    // LiveData
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _verificationSent = MutableLiveData(false)
    val verificationSent: LiveData<Boolean> = _verificationSent

    private val _otpVerified = MutableLiveData(false)
    val otpVerified: LiveData<Boolean> = _otpVerified

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _phoneNumber = MutableLiveData<String?>()
    val phoneNumber: LiveData<String?> = _phoneNumber

    // Callback for OTP
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(TAG, "✅ onVerificationCompleted: Auto-verified")
            _isLoading.value = false
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e(TAG, "❌ onVerificationFailed: ${e.message}")
            _isLoading.value = false
            _errorMessage.value = "Verification failed: ${e.message}"
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d(TAG, "✅ onCodeSent: OTP sent successfully")
            this@AuthViewModel.verificationId = verificationId
            resendToken = token
            _isLoading.value = false
            _verificationSent.value = true
            _errorMessage.value = null
        }
    }

    /**
     * ✅ Send OTP to phone number
     */
    fun sendOTP(phoneNumber: String, activity: Activity) {
        if (phoneNumber.isEmpty()) {
            _errorMessage.value = "Please enter phone number"
            return
        }

        _isLoading.value = true
        _phoneNumber.value = phoneNumber
        _errorMessage.value = null

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
        Log.d(TAG, "📤 Sending OTP to: $phoneNumber")
    }

    /**
     * ✅ Resend OTP - FIXED null safety
     */
    fun resendOTP(phoneNumber: String, activity: Activity) {
        if (phoneNumber.isEmpty()) {
            _errorMessage.value = "Please enter phone number"
            return
        }

        val token = resendToken
        if (token == null) {
            _errorMessage.value = "Please send OTP first"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .setForceResendingToken(token)  // ✅ Now token is non-null
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
        Log.d(TAG, "📤 Resending OTP to: $phoneNumber")
    }

    /**
     * ✅ Verify OTP
     */
    fun verifyOTP(otp: String) {
        if (otp.isEmpty()) {
            _errorMessage.value = "Please enter OTP"
            return
        }

        if (otp.length < 6) {
            _errorMessage.value = "OTP must be 6 digits"
            return
        }

        val verificationId = this.verificationId
        if (verificationId.isNullOrEmpty()) {
            _errorMessage.value = "Verification ID not found. Please resend OTP."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        signInWithPhoneAuthCredential(credential)
    }

    /**
     * ✅ Sign in with Phone Auth Credential
     */
    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Sign in successful: ${auth.currentUser?.phoneNumber}")
                    _otpVerified.value = true
                    _errorMessage.value = null
                } else {
                    Log.e(TAG, "❌ Sign in failed: ${task.exception?.message}")
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        _errorMessage.value = "Invalid OTP. Please try again."
                    } else {
                        _errorMessage.value = "Login failed: ${task.exception?.message}"
                    }
                }
            }
    }

    /**
     * ✅ Check if user is logged in
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * ✅ Get current user phone number
     */
    fun getCurrentUserPhone(): String? {
        return auth.currentUser?.phoneNumber
    }

    /**
     * ✅ Logout
     */
    fun logout() {
        auth.signOut()
        _otpVerified.value = false
        _verificationSent.value = false
        verificationId = null
        resendToken = null
        Log.d(TAG, "🚪 User logged out")
    }

    /**
     * ✅ Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * ✅ Reset OTP state
     */
    fun resetOTPState() {
        _verificationSent.value = false
        _otpVerified.value = false
        verificationId = null
        resendToken = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 ViewModel cleared")
    }
}