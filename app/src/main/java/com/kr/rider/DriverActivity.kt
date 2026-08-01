package com.kr.rider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kr.rider.fragments.DriverHomeFragment
import com.kr.rider.fragments.DriverPendingRidesFragment
import com.kr.rider.services.DriverLocationService

class DriverActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val containerId = R.id.driver_fragment_container

    private val homeFragment = DriverHomeFragment()
    private val pendingRidesFragment = DriverPendingRidesFragment()

    private var currentFragment: Fragment = homeFragment

    // 🔥 Driver ID store karne ke liye
    private var driverId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "🚗 Driver"

        // ✅ Get Driver ID
        val sharedPref = getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)

        if (driverId == null) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                driverId = currentUser.uid
                sharedPref.edit().putString("driverId", driverId).apply()
            } else {
                Toast.makeText(this, "⚠️ Please login again", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        bottomNav = findViewById(R.id.driver_bottom_navigation)

        if (savedInstanceState == null) {
            loadFragment(homeFragment, "HOME")

            // ✅ PERMISSION CHECK YAHAN HOGA (Sabhi ek saath)
            checkAndRequestAllPermissions()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.driver_home -> {
                    loadFragment(homeFragment, "HOME")
                    true
                }
                R.id.driver_pending_rides -> {
                    loadFragment(pendingRidesFragment, "PENDING")
                    true
                }
                R.id.driver_rides -> {
                    showComingSoonToast()
                    true
                }
                R.id.driver_wallet -> {
                    showComingSoonToast()
                    true
                }
                R.id.driver_profile -> {
                    showComingSoonToast()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment, tag: String) {
        if (currentFragment.javaClass == fragment.javaClass && currentFragment.isAdded) {
            return
        }

        currentFragment = fragment
        supportActionBar?.title = when (tag) {
            "HOME" -> "🚗 Driver"
            "PENDING" -> "📋 Pending Rides"
            else -> "Driver"
        }

        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)
            .commit()
    }

    private fun showComingSoonToast() {
        Toast.makeText(this, "⏳ Coming Soon!", Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // 🔥 UPDATED PERMISSION LOGIC (3-in-1) - ANDROID 14+ SAFE
    // ============================================================

    private fun checkAndRequestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. FOREGROUND_SERVICE_LOCATION Permission (Android 14+ - CRASH FIX)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            }
        }

        // 3. Location Permissions (Coarse & Fine)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Agar koi permission maangni hai toh request karo
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                101 // Request Code
            )
        } else {
            // 🔥 Sab permissions already hain, direct service start karo
            startLocationService()
        }
    }

    // 🔥 Permission result handle karein:
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            // Check if all requested permissions are granted
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (allGranted) {
                Toast.makeText(this, "✅ All permissions granted! Going Online...", Toast.LENGTH_SHORT).show()
                startLocationService()
            } else {
                Toast.makeText(this, "⚠️ Some permissions denied! Background tracking may be limited.", Toast.LENGTH_LONG).show()
                // Phir bhi service start karo (jaise bhi ho, try karo)
                startLocationService()
            }
        }
    }

    // 🔥 Service start karne ka function:
    private fun startLocationService() {
        if (driverId == null) {
            Toast.makeText(this, "❌ Driver ID not found!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(this, DriverLocationService::class.java)
            intent.putExtra("driverId", driverId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "🚗 You are now ONLINE!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Failed to go online: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 🔥 Service stop karne ka function (Optional - use when driver goes offline)
    private fun stopLocationService() {
        val intent = Intent(this, DriverLocationService::class.java)
        stopService(intent)
        Toast.makeText(this, "🔴 You are OFFLINE", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // App band ho raha hai toh service ko mat roko (Background mein chalta rahe)
        // Agar aap chahte hain ki app band hone par service band ho jaye, toh niche wali line uncomment karein:
        // stopLocationService()
    }
}