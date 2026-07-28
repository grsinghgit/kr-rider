package com.kr.rider.utils

import kotlin.math.*

object DistanceUtils {

    /**
     * ✅ Calculate distance between two coordinates using Haversine formula
     * @return Distance in Kilometers
     */
    fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val R = 6371.0 // Earth radius in km

        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c // Distance in km
    }

    /**
     * ✅ Format distance to 1 decimal place
     */
    fun formatDistance(distance: Double): String {
        return String.format("%.1f", distance)
    }

    /**
     * ✅ Format fare to 2 decimal places
     */
    fun formatFare(fare: Double): String {
        return String.format("%.2f", fare)
    }

    /**
     * ✅ Format fare to integer (for display)
     */
    fun formatFareInt(fare: Double): String {
        return String.format("%.0f", fare)
    }
}