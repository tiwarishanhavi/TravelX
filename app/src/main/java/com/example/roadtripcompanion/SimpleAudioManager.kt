package com.example.roadtripcompanion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.util.Base64

class SimpleAudioManager(
    private val context: Context,
    private val tripId: String,
    private val onError: (String) -> Unit = {}
) {
    
    private val TAG = "SimpleAudioManager"
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var isRecording = false
    private var isInitialized = false
    
    // Improved audio settings for better quality while maintaining compatibility
    private val sampleRate = 16000  // Higher sample rate for better clarity
    private val channelInConfig = AudioFormat.CHANNEL_IN_MONO
    private val channelOutConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    
    // Calculate buffer size safely
    private val bufferSize by lazy {
        try {
            val minSize = AudioRecord.getMinBufferSize(sampleRate, channelInConfig, audioFormat)
            when {
                minSize == AudioRecord.ERROR -> {
                    Log.w(TAG, "AudioRecord.ERROR returned, using fallback")
                    4096
                }
                minSize == AudioRecord.ERROR_BAD_VALUE -> {
                    Log.w(TAG, "ERROR_BAD_VALUE returned, using fallback")
                    4096
                }
                minSize <= 0 -> {
                    Log.w(TAG, "Invalid buffer size: $minSize, using fallback")
                    4096
                }
                else -> {
                    Log.d(TAG, "Calculated buffer size: $minSize")
                    maxOf(minSize, 4096) // Ensure minimum 4KB
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calculating buffer size", e)
            4096 // Safe fallback
        }
    }
    
    fun initialize(): Boolean {
        if (isInitialized) return true
        
        Log.d(TAG, "Initializing SimpleAudioManager...")
        
        // Check permissions
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission not granted")
            return false
        }
        
        // Check if user is authenticated
        if (currentUserId.isEmpty()) {
            onError("User not authenticated")
            return false
        }
        
        Log.d(TAG, "Using buffer size: $bufferSize")
        
        try {
            // Initialize with very conservative settings
            initializeAudioRecord()
            initializeAudioTrack()
            
            if (audioRecord != null && audioTrack != null) {
                listenForAudioData()
                isInitialized = true
                Log.d(TAG, "SimpleAudioManager initialized successfully")
                return true
            } else {
                onError("Failed to initialize audio components")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            onError("Audio initialization failed: ${e.message}")
            cleanup()
            return false
        }
    }
    
    @Suppress("MissingPermission")
    private fun initializeAudioRecord() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelInConfig,
                audioFormat,
                bufferSize
            )
            
            val state = audioRecord?.state
            Log.d(TAG, "AudioRecord state: $state")
            
            if (state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized properly")
                audioRecord?.release()
                audioRecord = null
                throw RuntimeException("AudioRecord initialization failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            audioRecord = null
            throw e
        }
    }
    
    private fun initializeAudioTrack() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOutConfig)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelOutConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }
            
            val state = audioTrack?.state
            Log.d(TAG, "AudioTrack state: $state")
            
            if (state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized properly")
                audioTrack?.release()
                audioTrack = null
                throw RuntimeException("AudioTrack initialization failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack", e)
            audioTrack = null
            throw e
        }
    }
    
    fun startRecording(): Boolean {
        if (!isInitialized) {
            onError("Audio manager not initialized")
            return false
        }
        
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return true
        }
        
        try {
            val record = audioRecord ?: run {
                onError("AudioRecord not available")
                return false
            }
            
            record.startRecording()
            
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                onError("Failed to start recording")
                return false
            }
            
            isRecording = true
            
            // Start recording in background
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordAudio(record)
            }
            
            Log.d(TAG, "Recording started successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            onError("Failed to start recording: ${e.message}")
            return false
        }
    }
    
    private suspend fun recordAudio(record: AudioRecord) {
        val buffer = ByteArray(bufferSize / 8) // Even smaller chunks for better stability
        var chunkCounter = 0
        
        try {
            while (isRecording && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = record.read(buffer, 0, buffer.size)
                
                if (bytesRead > 0) {
                    // Send every 4th chunk for better quality while maintaining stability
                    chunkCounter++
                    if (chunkCounter % 4 == 0) {
                        val encoded = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                        sendAudioChunk(encoded)
                    }
                    
                    // Reduced delay for better real-time performance
                    kotlinx.coroutines.delay(25)
                } else if (bytesRead < 0) {
                    Log.w(TAG, "Audio read error: $bytesRead")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during recording", e)
        } finally {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
        }
    }
    
    private fun sendAudioChunk(encodedAudio: String) {
        try {
            val data = hashMapOf(
                "userId" to currentUserId,
                "audio" to encodedAudio,
                "timestamp" to System.currentTimeMillis(),
                "sampleRate" to sampleRate
            )
            
            firestore.collection("trips").document(tripId)
                .collection("voice_data")
                .add(data)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to send audio chunk", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }
    
    fun stopRecording() {
        if (!isRecording) return
        
        try {
            isRecording = false
            recordingJob?.cancel()
            
            audioRecord?.let { record ->
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recording", e)
                }
            }
            
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error during stop recording", e)
        }
    }
    
    private fun listenForAudioData() {
        firestore.collection("trips").document(tripId)
            .collection("voice_data")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed", e)
                    return@addSnapshotListener
                }
                
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val userId = data["userId"] as? String
                        val audioData = data["audio"] as? String
                        val timestamp = data["timestamp"] as? Long ?: 0
                        
                        // Play audio from other users (recent data only)
                        if (userId != currentUserId && audioData != null) {
                            val age = System.currentTimeMillis() - timestamp
                            if (age < 10000) { // Only play audio less than 10 seconds old
                                playAudioChunk(audioData)
                            }
                        }
                    }
                }
            }
    }
    
    private fun playAudioChunk(encodedAudio: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioData = Base64.decode(encodedAudio, Base64.NO_WRAP)
                val track = audioTrack
                
                if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                    // Ensure AudioTrack is playing
                    if (track.playState == AudioTrack.PLAYSTATE_STOPPED) {
                        track.play()
                    }
                    
                    // Write audio data in smaller chunks to avoid buffer overflow
                    val chunkSize = 1024
                    var offset = 0
                    while (offset < audioData.size) {
                        val remaining = audioData.size - offset
                        val writeSize = minOf(chunkSize, remaining)
                        val bytesWritten = track.write(audioData, offset, writeSize)
                        
                        if (bytesWritten < 0) {
                            Log.w(TAG, "AudioTrack write error: $bytesWritten")
                            break
                        }
                        
                        offset += bytesWritten
                        
                        // Small delay to prevent overwhelming the audio system
                        kotlinx.coroutines.delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error playing audio", e)
            }
        }
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up SimpleAudioManager")
        
        stopRecording()
        
        try {
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        
        isInitialized = false
    }
}
