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
import com.kr.rider.ui.DriverLoginFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ Check if already logged in
        val sharedPref = getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isDriverLoggedIn", false)

        if (isLoggedIn) {
            // ✅ Already logged in - Check permissions and go to DriverActivity
            checkAndRequestAllPermissions() // 🔥 Permissions yahin check hongi
            return
        }

        // ✅ Load DriverLoginFragment for new login
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, DriverLoginFragment())
                .commit()
        }
    }

    // ============================================================
    // 🔥 PERMISSION REQUEST LOGIC (Android 14+ Safe)
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
            // 🔥 Sab permissions already hain, direct DriverActivity open karo
            navigateToDriverActivity()
        }
    }

    // 🔥 Permission result handle karein:
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (allGranted) {
                Toast.makeText(this, "✅ Permissions granted! Going Online...", Toast.LENGTH_SHORT).show()
                navigateToDriverActivity()
            } else {
                Toast.makeText(this, "⚠️ Some permissions denied! Background tracking may be limited.", Toast.LENGTH_LONG).show()
                // Phir bhi DriverActivity open karo
                navigateToDriverActivity()
            }
        }
    }

    // 🔥 DriverActivity par navigate karne ka function
    private fun navigateToDriverActivity() {
        val sharedPref = getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        val driverId = sharedPref.getString("driverId", null)

        val intent = Intent(this, DriverActivity::class.java)
        intent.putExtra("driverId", driverId) // DriverId pass karo
        startActivity(intent)
        finish() // MainActivity band karo
    }
}