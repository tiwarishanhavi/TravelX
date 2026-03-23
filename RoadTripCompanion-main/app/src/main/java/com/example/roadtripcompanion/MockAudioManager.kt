package com.example.roadtripcompanion

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Mock implementation for testing without audio hardware
 * This simulates the audio functionality to test UI and Firebase integration
 */
class MockAudioManager(
    private val context: Context,
    private val tripId: String,
    private val onError: (String) -> Unit = {}
) {
    
    private val TAG = "MockAudioManager"
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var isRecording = false
    private var isInitialized = false
    
    fun initialize(): Boolean {
        Log.d(TAG, "Mock: Initializing audio manager...")
        
        if (currentUserId.isEmpty()) {
            onError("User not authenticated")
            return false
        }
        
        // Mock initialization - always succeeds
        isInitialized = true
        listenForMockAudio()
        
        Log.d(TAG, "Mock: Audio manager initialized successfully")
        return true
    }
    
    fun startRecording(): Boolean {
        if (!isInitialized) {
            onError("Audio manager not initialized")
            return false
        }
        
        if (isRecording) {
            Log.w(TAG, "Mock: Already recording")
            return true
        }
        
        Log.d(TAG, "Mock: Starting recording...")
        isRecording = true
        
        // Simulate recording by sending mock data
        CoroutineScope(Dispatchers.IO).launch {
            simulateRecording()
        }
        
        return true
    }
    
    private suspend fun simulateRecording() {
        var counter = 0
        while (isRecording && counter < 30) { // Max 30 seconds
            delay(1000) // Send mock data every second
            
            val mockData = hashMapOf(
                "userId" to currentUserId,
                "audio" to "mock_audio_data_$counter",
                "timestamp" to System.currentTimeMillis(),
                "sampleRate" to 8000,
                "isMock" to true
            )
            
            try {
                firestore.collection("trips").document(tripId)
                    .collection("voice_data")
                    .add(mockData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Mock: Sent audio chunk $counter")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Mock: Failed to send audio chunk", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Mock: Error sending audio", e)
                break
            }
            
            counter++
        }
    }
    
    fun stopRecording() {
        if (!isRecording) return
        
        Log.d(TAG, "Mock: Stopping recording")
        isRecording = false
    }
    
    private fun listenForMockAudio() {
        firestore.collection("trips").document(tripId)
            .collection("voice_data")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Mock: Listen failed", e)
                    return@addSnapshotListener
                }
                
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val userId = data["userId"] as? String
                        val isMock = data["isMock"] as? Boolean ?: false
                        val timestamp = data["timestamp"] as? Long ?: 0
                        
                        // Simulate receiving audio from other users
                        if (userId != currentUserId) {
                            val age = System.currentTimeMillis() - timestamp
                            if (age < 10000) { // Only process recent data
                                if (isMock) {
                                    Log.d(TAG, "Mock: Received mock audio from $userId")
                                } else {
                                    Log.d(TAG, "Mock: Received real audio from $userId")
                                }
                            }
                        }
                    }
                }
            }
    }
    
    fun cleanup() {
        Log.d(TAG, "Mock: Cleaning up")
        stopRecording()
        isInitialized = false
    }
}
