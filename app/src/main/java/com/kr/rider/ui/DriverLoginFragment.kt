package com.kr.rider.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.kr.rider.DriverActivity
import com.kr.rider.R
import com.kr.rider.viewmodel.DriverAuthViewModel
import com.kr.rider.viewmodel.DriverData

class DriverLoginFragment : Fragment() {

    private val authViewModel: DriverAuthViewModel by viewModels()

    private lateinit var etPhone: TextInputEditText
    private lateinit var etPIN: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvError: TextView
    private lateinit var progressBar: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPhone = view.findViewById(R.id.etPhone)
        etPIN = view.findViewById(R.id.etPIN)
        btnLogin = view.findViewById(R.id.btnLogin)
        tvError = view.findViewById(R.id.tvError)
        progressBar = view.findViewById(R.id.progressBar)

        progressBar.visibility = View.GONE

        btnLogin.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val pin = etPIN.text.toString().trim()

            if (phone.isEmpty()) {
                showError("Enter phone number")
                return@setOnClickListener
            }
            if (pin.isEmpty() || pin.length < 4) {
                showError("Enter valid PIN (4-6 digits)")
                return@setOnClickListener
            }

            authViewModel.verifyDriverLogin(phone, pin)
        }

        // ✅ Observers
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                progressBar.visibility = View.VISIBLE
                btnLogin.isEnabled = false
                btnLogin.text = "Verifying..."
            } else {
                progressBar.visibility = View.GONE
                btnLogin.isEnabled = true
                btnLogin.text = "🔓 Login"
            }
        }

        authViewModel.loginSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                val driverData = authViewModel.driverData.value
                Toast.makeText(requireContext(), "✅ Welcome ${driverData?.name}!", Toast.LENGTH_LONG).show()

                // ✅ Save session
                saveDriverSession(driverData)

                // ✅ Navigate to DriverActivity
                startActivity(Intent(requireContext(), DriverActivity::class.java))
                requireActivity().finish()
            }
        }

        authViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                authViewModel.clearError()
            }
        }
    }

    private fun saveDriverSession(driverData: DriverData?) {
        driverData?.let {
            val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
            sharedPref.edit()
                .putString("driverId", it.driverId)
                .putString("driverName", it.name)
                .putString("driverPhone", it.phone)
                .putString("vehicleType", it.vehicleType)
                .putString("vehicleNumber", it.vehicleNumber)
                .putBoolean("isDriverLoggedIn", true)
                .apply()
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
}