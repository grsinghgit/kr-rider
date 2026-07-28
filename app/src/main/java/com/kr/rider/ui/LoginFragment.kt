package com.kr.rider.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels  // ✅ Sahi import
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.kr.rider.DriverActivity
import com.kr.rider.R
import com.kr.rider.viewmodel.AuthViewModel

class LoginFragment : Fragment() {

    // ✅ Correct way to initialize ViewModel
    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var btnSendOTP: MaterialButton
    private lateinit var btnResendOTP: MaterialButton
    private lateinit var etOTP: TextInputEditText
    private lateinit var btnVerifyOTP: MaterialButton
    private lateinit var progressBar: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        btnSendOTP = view.findViewById(R.id.btnSendOTP)
        btnResendOTP = view.findViewById(R.id.btnResendOTP)
        etOTP = view.findViewById(R.id.etOTP)
        btnVerifyOTP = view.findViewById(R.id.btnVerifyOTP)
        progressBar = view.findViewById(R.id.progressBar)

        // Initially OTP fields hidden
        etOTP.visibility = View.GONE
        btnVerifyOTP.visibility = View.GONE
        btnResendOTP.visibility = View.GONE
        progressBar.visibility = View.GONE

        // Send OTP Button
        btnSendOTP.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                authViewModel.sendOTP(phoneNumber, requireActivity())
            } else {
                Toast.makeText(requireContext(), "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // Resend OTP Button
        btnResendOTP.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                authViewModel.resendOTP(phoneNumber, requireActivity())
            } else {
                Toast.makeText(requireContext(), "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // Verify OTP Button
        btnVerifyOTP.setOnClickListener {
            val otp = etOTP.text.toString().trim()
            if (otp.isNotEmpty()) {
                authViewModel.verifyOTP(otp)
            } else {
                Toast.makeText(requireContext(), "Enter OTP", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe LiveData
        setupObservers()

        // Check if already logged in
        if (authViewModel.isUserLoggedIn()) {
            navigateToDriverActivity()
        }
    }

    private fun setupObservers() {
        // Loading state
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                progressBar.visibility = View.VISIBLE
                btnSendOTP.isEnabled = false
                btnVerifyOTP.isEnabled = false
                btnSendOTP.text = "Sending..."
                btnVerifyOTP.text = "Verifying..."
            } else {
                progressBar.visibility = View.GONE
                btnSendOTP.isEnabled = true
                btnVerifyOTP.isEnabled = true
                btnSendOTP.text = "Send OTP"
                btnVerifyOTP.text = "Verify OTP"
            }
        }

        // OTP Sent
        authViewModel.verificationSent.observe(viewLifecycleOwner) { sent ->
            if (sent) {
                Toast.makeText(requireContext(), "✅ OTP Sent Successfully!", Toast.LENGTH_SHORT).show()
                etOTP.visibility = View.VISIBLE
                btnVerifyOTP.visibility = View.VISIBLE
                btnResendOTP.visibility = View.VISIBLE
                btnSendOTP.visibility = View.GONE
                etOTP.requestFocus()
            }
        }

        // OTP Verified
        authViewModel.otpVerified.observe(viewLifecycleOwner) { verified ->
            if (verified) {
                Toast.makeText(requireContext(), "✅ Login Successful! 🎉", Toast.LENGTH_LONG).show()
                navigateToDriverActivity()
            }
        }

        // Error message
        authViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                authViewModel.clearError()
            }
        }
    }

    private fun navigateToDriverActivity() {
        val intent = Intent(requireContext(), DriverActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}