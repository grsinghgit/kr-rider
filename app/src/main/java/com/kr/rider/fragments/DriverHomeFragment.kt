package com.kr.rider.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.kr.rider.MainActivity
import com.kr.rider.R
import com.kr.rider.services.DriverLocationService
import com.kr.rider.viewmodel.DriverHomeViewModel

class DriverHomeFragment : Fragment() {

    private val viewModel: DriverHomeViewModel by viewModels()

    private lateinit var tvWelcome: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnGoOnline: MaterialButton
    private lateinit var tvTotalRides: TextView
    private lateinit var tvEarnings: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvWalletBalance: TextView
    private lateinit var btnLogout: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private var driverId: String? = null
    private var isOnline = false

    // ✅ Permission request code
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Init views
        tvWelcome = view.findViewById(R.id.tvWelcome)
        tvStatus = view.findViewById(R.id.tvDriverStatus)
        btnGoOnline = view.findViewById(R.id.btnGoOnline)
        tvTotalRides = view.findViewById(R.id.tvTotalRides)
        tvEarnings = view.findViewById(R.id.tvEarnings)
        tvRating = view.findViewById(R.id.tvRating)
        tvWalletBalance = view.findViewById(R.id.tvWalletBalance)
        btnLogout = view.findViewById(R.id.btnLogout)

        // Get driver ID from SharedPreferences
        val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)

        if (driverId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Load driver data
        viewModel.loadDriverData(driverId!!)

        // ✅ Check online status from Firestore
        checkOnlineStatus()

        // ✅ Toggle Online/Offline
        btnGoOnline.setOnClickListener {
            // ✅ Check location permission first
            if (!checkLocationPermission()) {
                requestLocationPermission()
                return@setOnClickListener
            }
            toggleOnlineStatus()
        }

        // ✅ Logout
        btnLogout.setOnClickListener {
            // Stop location service if running
            if (isOnline) {
                requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
            }

            // Clear session
            val pref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
            pref.edit().clear().apply()

            // Navigate to Login
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }

        // ✅ Observers
        setupObservers()
    }

    private fun setupObservers() {
        // Driver data
        viewModel.driverData.observe(viewLifecycleOwner) { driver ->
            driver?.let {
                tvWelcome.text = "🚗 Welcome, ${it.name}!"
                tvTotalRides.text = "${it.totalRides}"
                tvEarnings.text = "₹${it.totalEarnings.toInt()}"
                tvRating.text = String.format("%.1f⭐", it.rating)
                tvWalletBalance.text = "₹${String.format("%.2f", it.walletBalance)}"
            }
        }

        // Error
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    /**
     * ✅ Check online status from Firestore
     */
    private fun checkOnlineStatus() {
        driverId?.let { id ->
            db.collection("driver_locations").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val status = document.getString("status") ?: "OFFLINE"
                        val isOnlineStatus = status == "ONLINE"
                        updateUIStatus(isOnlineStatus)
                    } else {
                        // If document doesn't exist, create one with OFFLINE
                        createDriverLocationDocument()
                    }
                }
                .addOnFailureListener {
                    createDriverLocationDocument()
                }
        }
    }

    /**
     * ✅ Create driver location document if not exists
     */
    private fun createDriverLocationDocument() {
        driverId?.let { id ->
            val data = hashMapOf(
                "driverId" to id,
                "status" to "OFFLINE",
                "isAvailable" to false,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )
            db.collection("driver_locations")
                .document(id)
                .set(data)
                .addOnSuccessListener {
                    android.util.Log.d("DriverHome", "✅ Driver location document created")
                    updateUIStatus(false)
                }
        }
    }

    /**
     * ✅ Toggle Online/Offline
     */
    private fun toggleOnlineStatus() {
        val newStatus = !isOnline
        val statusText = if (newStatus) "ONLINE" else "OFFLINE"

        driverId?.let { id ->
            // Update Firestore status
            db.collection("driver_locations").document(id)
                .update(
                    mapOf(
                        "status" to statusText,
                        "isAvailable" to newStatus,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    updateUIStatus(newStatus)

                    Toast.makeText(
                        requireContext(),
                        if (newStatus) "🟢 You are ONLINE" else "🔴 You are OFFLINE",
                        Toast.LENGTH_SHORT
                    ).show()

                    // ✅ Start/Stop location service
                    if (newStatus) {
                        startLocationService()
                    } else {
                        stopLocationService()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /**
     * ✅ Start Location Service
     */
    private fun startLocationService() {
        val intent = Intent(requireContext(), DriverLocationService::class.java)
        intent.putExtra("driverId", driverId)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(intent)
        } else {
            requireActivity().startService(intent)
        }
        android.util.Log.d("DriverHome", "✅ Location Service Started")
    }

    /**
     * ✅ Stop Location Service
     */
    private fun stopLocationService() {
        requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
        android.util.Log.d("DriverHome", "✅ Location Service Stopped")
    }

    /**
     * ✅ Update UI based on online status
     */
    private fun updateUIStatus(online: Boolean) {
        isOnline = online
        if (online) {
            tvStatus.text = "🟢 ONLINE"
            tvStatus.setTextColor(resources.getColor(R.color.green, null))
            btnGoOnline.text = "🔴 Go Offline"
            btnGoOnline.setBackgroundColor(resources.getColor(R.color.red, null))
        } else {
            tvStatus.text = "🔴 OFFLINE"
            tvStatus.setTextColor(resources.getColor(R.color.red, null))
            btnGoOnline.text = "🟢 Go Online"
            btnGoOnline.setBackgroundColor(resources.getColor(R.color.green, null))
        }
    }

    /**
     * ✅ Check Location Permission
     */
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * ✅ Request Location Permission
     */
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "✅ Location permission granted", Toast.LENGTH_SHORT).show()
                // ✅ Now toggle online
                toggleOnlineStatus()
            } else {
                Toast.makeText(requireContext(), "❌ Location permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Don't stop service here - it should run even if fragment is destroyed
    }
}