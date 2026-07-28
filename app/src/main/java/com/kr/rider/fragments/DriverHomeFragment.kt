package com.kr.rider.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private val TAG = "DriverHomeFragment"
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
    private var locationDialog: AlertDialog? = null

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

        initViews(view)

        val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)
        Log.d(TAG, "📌 driverId from sharedPref = $driverId")

        if (driverId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.loadDriverData(driverId!!)
        checkOnlineStatus()

        btnGoOnline.setOnClickListener {
            Log.d(TAG, "🟢 Go Online/Offline button clicked")

            // ✅ Step 1: Check Location Permission
            if (!checkAllPermissions()) {
                requestAllPermissions()
                return@setOnClickListener
            }

            // ✅ Step 2: Check if Location is enabled
            if (!isLocationEnabled()) {
                showLocationSettingsDialog()
                return@setOnClickListener
            }

            // ✅ Step 3: Toggle Online/Offline
            toggleOnlineStatus()
        }

        btnLogout.setOnClickListener {
            if (isOnline) {
                requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
            }
            val pref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
            pref.edit().clear().apply()
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }

        setupObservers()
    }

    private fun initViews(view: View) {
        tvWelcome = view.findViewById(R.id.tvWelcome)
        tvStatus = view.findViewById(R.id.tvDriverStatus)
        btnGoOnline = view.findViewById(R.id.btnGoOnline)
        tvTotalRides = view.findViewById(R.id.tvTotalRides)
        tvEarnings = view.findViewById(R.id.tvEarnings)
        tvRating = view.findViewById(R.id.tvRating)
        tvWalletBalance = view.findViewById(R.id.tvWalletBalance)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    private fun setupObservers() {
        viewModel.driverData.observe(viewLifecycleOwner) { driver ->
            driver?.let {
                tvWelcome.text = "🚗 Welcome, ${it.name}!"
                tvTotalRides.text = "${it.totalRides}"
                tvEarnings.text = "₹${it.totalEarnings.toInt()}"
                tvRating.text = String.format("%.1f⭐", it.rating)
                tvWalletBalance.text = "₹${String.format("%.2f", it.walletBalance)}"
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    /**
     * ✅ Check if Location is enabled
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * ✅ Show Dialog to enable Location
     */
    private fun showLocationSettingsDialog() {
        if (locationDialog?.isShowing == true) return

        locationDialog = AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Location Required")
            .setMessage("This app needs location access to track your rides. Please enable location services to continue.")
            .setCancelable(false)
            .setPositiveButton("Enable Location") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(requireContext(), "⚠️ Location required for tracking", Toast.LENGTH_SHORT).show()
            }
            .create()
        locationDialog?.show()
    }

    private fun dismissLocationDialog() {
        locationDialog?.dismiss()
        locationDialog = null
    }

    private fun checkOnlineStatus() {
        driverId?.let { id ->
            Log.d(TAG, "🔍 Checking online status for: $id")
            db.collection("driver_locations").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val status = document.getString("status") ?: "OFFLINE"
                        val isOnlineStatus = status == "ONLINE"
                        Log.d(TAG, "📊 Current status: $status")
                        updateUIStatus(isOnlineStatus)

                        // ✅ If status is ONLINE but location is off, show dialog
                        if (isOnlineStatus && !isLocationEnabled()) {
                            showLocationSettingsDialog()
                        }
                    } else {
                        Log.d(TAG, "📝 Document doesn't exist, creating...")
                        createDriverLocationDocument()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to check status: ${e.message}")
                    createDriverLocationDocument()
                }
        }
    }

    private fun createDriverLocationDocument() {
        driverId?.let { id ->
            Log.d(TAG, "📝 Creating driver location document for: $id")
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
                    Log.d(TAG, "✅ Driver location document created")
                    updateUIStatus(false)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to create document: ${e.message}")
                }
        }
    }

    private fun toggleOnlineStatus() {
        val newStatus = !isOnline
        val statusText = if (newStatus) "ONLINE" else "OFFLINE"
        Log.d(TAG, "🔄 Toggling status to: $statusText")

        driverId?.let { id ->
            db.collection("driver_locations").document(id)
                .update(
                    mapOf(
                        "status" to statusText,
                        "isAvailable" to newStatus,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Status updated to $statusText")
                    updateUIStatus(newStatus)

                    Toast.makeText(
                        requireContext(),
                        if (newStatus) "🟢 You are ONLINE" else "🔴 You are OFFLINE",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (newStatus) {
                        // ✅ Start service only if location is enabled
                        if (isLocationEnabled()) {
                            startLocationService()
                        } else {
                            showLocationSettingsDialog()
                            // ✅ Revert status to OFFLINE if location is off
                            revertToOffline()
                        }
                    } else {
                        stopLocationService()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to update status: ${e.message}")
                    Toast.makeText(requireContext(), "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /**
     * ✅ Revert to OFFLINE if location is not enabled
     */
    private fun revertToOffline() {
        driverId?.let { id ->
            db.collection("driver_locations").document(id)
                .update(
                    mapOf(
                        "status" to "OFFLINE",
                        "isAvailable" to false,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Reverted to OFFLINE")
                    updateUIStatus(false)
                }
        }
    }

    private fun startLocationService() {
        Log.d(TAG, "🚀 Starting Location Service")
        val intent = Intent(requireContext(), DriverLocationService::class.java)
        intent.putExtra("driverId", driverId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(intent)
        } else {
            requireActivity().startService(intent)
        }
        Toast.makeText(requireContext(), "📍 Location tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationService() {
        Log.d(TAG, "🛑 Stopping Location Service")
        requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
        Toast.makeText(requireContext(), "📍 Location tracking stopped", Toast.LENGTH_SHORT).show()
    }

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

    private fun checkAllPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation && coarseLocation
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            Log.d(TAG, "📋 Requesting ${permissions.size} permissions")
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissions.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.d(TAG, "✅ All permissions granted")
                Toast.makeText(requireContext(), "✅ All permissions granted", Toast.LENGTH_SHORT).show()

                // ✅ Check location settings after permission granted
                if (!isLocationEnabled()) {
                    showLocationSettingsDialog()
                } else {
                    toggleOnlineStatus()
                }
            } else {
                Log.e(TAG, "❌ Some permissions denied")
                Toast.makeText(requireContext(), "❌ Location permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // ✅ Dismiss location dialog if location is enabled
        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
        }

        // ✅ If driver is ONLINE but location is off, revert to OFFLINE
        if (isOnline && !isLocationEnabled()) {
            Log.d(TAG, "⚠️ Location disabled while online, reverting to OFFLINE")
            revertToOffline()
            Toast.makeText(requireContext(), "⚠️ Location disabled, went offline", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissLocationDialog()
    }
}