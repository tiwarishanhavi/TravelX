package com.example.roadtripcompanion.models

import com.google.android.gms.maps.model.LatLng

data class NavigationData(
    val isNavigating: Boolean = false,
    val destinationName: String = "",
    val destinationLatLng: LatLng? = null,
    val currentInstruction: String = "",
    val distanceToDestination: String = "",
    val estimatedTimeArrival: String = "",
    val routePolyline: String = "",
    val currentStepIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val routeSteps: List<Map<String, Any>> = emptyList(), // Serializable route steps
    val startedBy: String = "", // User ID who started navigation
    val totalDistance: String = "",
    val totalDuration: String = ""
)

data class NavigationStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val maneuver: String,
    val startLocation: LatLng,
    val endLocation: LatLng
)
