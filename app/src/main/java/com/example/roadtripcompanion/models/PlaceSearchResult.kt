package com.example.roadtripcompanion.models

import com.google.android.gms.maps.model.LatLng

data class PlaceSearchResult(
    val placeId: String,
    val name: String,
    val address: String,
    val latLng: LatLng?
)
