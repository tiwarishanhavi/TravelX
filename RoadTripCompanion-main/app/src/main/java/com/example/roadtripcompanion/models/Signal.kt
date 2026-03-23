package com.example.roadtripcompanion
data class Signal(
    val uid: String = "",
    val type: String = "", // e.g. BREAK, HELP, REACHED
    val timestamp: Long = 0L
)