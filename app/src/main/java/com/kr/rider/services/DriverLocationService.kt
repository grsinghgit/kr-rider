package com.kr.rider.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
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

    private val db = FirebaseFirestore.getInstance()
    private var driverId: String? = null
    private var isLocationUpdatesStarted = false
    private var isServiceDestroyed = false

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "driver_location_channel"
        private const val UPDATE_INTERVAL = 10000L
        private const val FASTEST_INTERVAL = 5000L
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "🔵 onCreate: Service created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("🔄 Initializing..."))
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        isServiceDestroyed = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d(TAG, "🟢 onStartCommand: Service started")

        driverId = intent?.getStringExtra("driverId")
        android.util.Log.d(TAG, "📌 driverId = $driverId")

        if (driverId.isNullOrEmpty()) {
            android.util.Log.e(TAG, "❌ driverId is null! Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        // ✅ Update notification
        updateNotification("🟢 Online - Tracking...")

        // ✅ Update Firestore status
        updateDriverStatus("ONLINE", true)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                location?.let {
                    android.util.Log.d(TAG, "📍 Location: ${it.latitude}, ${it.longitude}")
                    updateDriverLocation(it)
                    updateNotification("🟢 Online - Tracking...")
                }
            }
        }

        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e(TAG, "❌ Location permission NOT granted!")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL
        )
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        isLocationUpdatesStarted = true
        android.util.Log.d(TAG, "✅ Location updates started")
    }

    private fun updateDriverLocation(location: Location) {
        val id = driverId ?: return

        val data = hashMapOf(
            "currentLocation" to com.google.firebase.firestore.GeoPoint(
                location.latitude,
                location.longitude
            ),
            "updatedAt" to Timestamp.now(),
            "status" to "ONLINE",
            "isAvailable" to true
        )

        db.collection("driver_locations")
            .document(id)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { e ->
                android.util.Log.e(TAG, "❌ Failed to save location: ${e.message}")
            }
    }

    private fun updateDriverStatus(status: String, isAvailable: Boolean) {
        val id = driverId ?: return
        db.collection("driver_locations")
            .document(id)
            .update(
                mapOf(
                    "status" to status,
                    "isAvailable" to isAvailable,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnFailureListener { e ->
                android.util.Log.e(TAG, "❌ Status update failed: ${e.message}")
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Kr Driver")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
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
            android.util.Log.e(TAG, "❌ Notification error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows driver online status"
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        android.util.Log.d(TAG, "🔴 onDestroy: Service destroying")
        isServiceDestroyed = true

        // Stop location updates
        try {
            if (::locationCallback.isInitialized && isLocationUpdatesStarted) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error stopping: ${e.message}")
        }

        // Update status to OFFLINE
        driverId?.let {
            db.collection("driver_locations")
                .document(it)
                .update(
                    mapOf(
                        "status" to "OFFLINE",
                        "isAvailable" to false,
                        "updatedAt" to Timestamp.now()
                    )
                )
        }

        super.onDestroy()
        android.util.Log.d(TAG, "🔴 onDestroy: Service destroyed")
    }
}