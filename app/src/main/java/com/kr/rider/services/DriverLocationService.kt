package com.kr.rider.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.kr.rider.DriverActivity
import com.kr.rider.R

class DriverLocationService : Service() {

    private val TAG = "DriverLocationService"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private val db = FirebaseFirestore.getInstance()
    private var driverId: String? = null
    private var isLocationUpdatesStarted = false
    private var isFirstLocationUpdate = true

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "driver_location_channel"
        private const val UPDATE_INTERVAL = 10000L  // 10 seconds
        private const val FASTEST_INTERVAL = 5000L   // 5 seconds
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔵 onCreate: Service created")

        // ✅ SAFETY CHECK 1: Android 14+ ke liye FOREGROUND_SERVICE_LOCATION permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
            if (checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ FOREGROUND_SERVICE_LOCATION permission NOT granted! Stopping service.")
                stopSelf()
                return
            }
        }

        // ✅ SAFETY CHECK 2: Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "⚠️ Notification permission NOT granted! (Notification won't show)")
            }
        }

        createNotificationChannel()

        // ✅ Start foreground with notification
        try {
            startForeground(NOTIFICATION_ID, createNotification("🔄 Initializing..."))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground: ${e.message}")
            stopSelf()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🟢 onStartCommand: Service started")

        driverId = intent?.getStringExtra("driverId")
        Log.d(TAG, "📌 driverId from intent = $driverId")

        if (driverId.isNullOrEmpty()) {
            Log.e(TAG, "❌ driverId is null or empty! Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "✅ driverId set to $driverId")
        updateNotification("🟢 Online - Tracking...")

        // ✅ Update Firestore status to ONLINE
        updateDriverStatus("ONLINE", true)

        // ✅ Build Location Request
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL
        )
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .build()

        // ✅ Location Callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d(TAG, "📍 Location: ${location.latitude}, ${location.longitude}")
                    updateDriverLocation(location)
                    updateNotification("🟢 Online - Tracking...")
                } else {
                    Log.w(TAG, "⚠️ Location is null")
                    updateNotification("⚠️ Searching for GPS...")
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (availability.isLocationAvailable) {
                    Log.d(TAG, "✅ Location available")
                    updateNotification("🟢 Online - GPS Active")
                } else {
                    Log.w(TAG, "⚠️ GPS Not Available")
                    updateNotification("⚠️ GPS Not Available")
                }
            }
        }

        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        Log.d(TAG, "🚀 startLocationUpdates: Starting...")

        // ✅ Check Location Permission
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Location permission NOT granted!")
            stopSelf()
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isLocationUpdatesStarted = true
            Log.d(TAG, "✅ Location updates requested")
            updateNotification("🟢 Online - Tracking location...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to request location updates: ${e.message}")
            stopSelf()
        }
    }

    private fun updateDriverLocation(location: Location) {
        val id = driverId
        if (id.isNullOrEmpty()) {
            Log.e(TAG, "❌ driverId is null, cannot save location")
            return
        }

        val lat = location.latitude
        val lng = location.longitude

        Log.d(TAG, "💾 Saving location for driver: $id")

        val data = hashMapOf(
            "currentLocation" to com.google.firebase.firestore.GeoPoint(lat, lng),
            "updatedAt" to Timestamp.now(),
            "status" to "ONLINE",
            "isAvailable" to true
        )

        db.collection("driver_locations")
            .document(id)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                if (isFirstLocationUpdate) {
                    Log.d(TAG, "✅✅✅ FIRST LOCATION SAVED! ✅✅✅")
                    isFirstLocationUpdate = false
                    updateNotification("✅ Online - Location Active")
                } else {
                    Log.d(TAG, "✅ Location updated successfully")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save location: ${e.message}")
                updateNotification("⚠️ Location save failed")
            }
    }

    private fun updateDriverStatus(status: String, isAvailable: Boolean) {
        val id = driverId ?: return
        Log.d(TAG, "📤 Updating driver status to $status")

        db.collection("driver_locations")
            .document(id)
            .update(
                mapOf(
                    "status" to status,
                    "isAvailable" to isAvailable,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "✅ Status updated to $status")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Status update failed: ${e.message}")
                // If document doesn't exist, create it
                createDriverLocationDocument(status, isAvailable)
            }
    }

    private fun createDriverLocationDocument(status: String, isAvailable: Boolean) {
        val id = driverId ?: return
        Log.d(TAG, "📝 Creating driver location document for: $id")

        val data = hashMapOf(
            "driverId" to id,
            "status" to status,
            "isAvailable" to isAvailable,
            "updatedAt" to Timestamp.now()
        )

        db.collection("driver_locations")
            .document(id)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Driver location document created")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to create document: ${e.message}")
            }
    }

    private fun createNotification(message: String): Notification {
        val intent = Intent(this, DriverActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔥 FIX: Use R.drawable.ic_notification (not mipmap) for better Android 12+ compatibility
        // Agar aapke paas notification icon nahi hai, toh R.mipmap.ic_launcher use kar sakte hain,
        // lekin ek simple white icon drawable folder mein daalna better hai.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Kr Driver")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher) // ✅ Change to R.drawable.ic_notification if available
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification(message))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Notification error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows driver online/offline status"
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification channel created")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "🔴 onDestroy: Service destroying")

        // ✅ Stop location updates
        try {
            if (::locationCallback.isInitialized && isLocationUpdatesStarted) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                Log.d(TAG, "✅ Location updates stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping: ${e.message}")
        }

        // ✅ Update status to OFFLINE
        driverId?.let {
            Log.d(TAG, "📤 Updating status to OFFLINE")
            db.collection("driver_locations")
                .document(it)
                .update(
                    mapOf(
                        "status" to "OFFLINE",
                        "isAvailable" to false,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Status updated to OFFLINE")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Status update failed: ${e.message}")
                }
        }

        updateNotification("🔴 You are OFFLINE")

        // ✅ Remove notification after delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                Log.d(TAG, "🔔 Notification removed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error removing notification: ${e.message}")
            }
        }, 3000)

        super.onDestroy()
        Log.d(TAG, "🔴 onDestroy: Service destroyed")
    }
}