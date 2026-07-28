package com.kr.rider.model

import com.google.firebase.Timestamp

data class RideModel(
    val rideId: String = "",
    val userId: String = "",
    val userPhone: String = "",
    val userName: String = "",

    val pickup: LocationData? = null,
    val destination: LocationData? = null,

    // ✅ Vehicle Selection Fields
    val vehicleType: String = "car",
    val vehicleIcon: String = "🚗",
    val vehicleName: String = "Car",

    // ✅ Distance & Duration
    val distance: Double = 0.0,
    val duration: Int = 0,

    // ✅ FARE BREAKDOWN
    val basePrice: Double = 0.0,
    val perKmRate: Double = 0.0,
    val distanceFare: Double = 0.0,
    val totalFare: Double = 0.0,

    // ✅ Distance Breakdown
    val pickupDistance: Double = 0.0,
    val tripDistance: Double = 0.0,
    val totalDistance: Double = 0.0,

    // ✅ Flag to check if fare is calculated
    val fareCalculated: Boolean = false,

    // ✅ Status Flow
    val status: String = "PENDING",

    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverVehicle: String? = null,
    val driverVehicleNumber: String? = null,

    val areaId: String = "",
    val adminId: String = "",

    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",
    val cancelReason: String? = null,
    val cancelledBy: String? = null,

    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,

    // ✅ Ride completion
    val completedAt: Timestamp? = null,

    // ✅ PIN Based Verification
    val pickupPin: String? = null,
    val pickupTime: Timestamp? = null,
    val completePin: String? = null,
    val completeTime: Timestamp? = null
)

data class LocationData(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)