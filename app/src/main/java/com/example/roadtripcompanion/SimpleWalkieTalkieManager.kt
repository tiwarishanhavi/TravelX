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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import android.util.Base64
import java.nio.ByteBuffer

class SimpleWalkieTalkieManager(
    private val context: Context,
    private val tripId: String,
    private val onConnectionStateChanged: (String, Boolean) -> Unit
) {
    
    private val TAG = "SimpleWalkieTalkieManager"
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var isTransmitting = false
    private var isInitialized = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    
    // Audio configuration
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = run {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $minBufferSize")
            8192 // Fallback buffer size
        } else {
            minBufferSize
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        // Check for audio recording permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Audio recording permission not granted")
            return
        }
        
        try {
            // Validate buffer size
            if (bufferSize <= 0) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return
            }
            
            // Initialize audio recording
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            // Check if AudioRecord was initialized successfully
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed - state: ${audioRecord?.state}")
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            // Initialize audio playback
            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    audioFormat,
                    bufferSize * 2,
                    AudioTrack.MODE_STREAM
                )
            }
            
            // Check if AudioTrack was initialized successfully
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed")
                audioTrack?.release()
                audioTrack = null
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            // Listen for incoming audio data
            listenForAudioData()
            
            isInitialized = true
            Log.d(TAG, "SimpleWalkieTalkieManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio", e)
        }
    }
    
    fun startTransmission() {
        if (!isInitialized) {
            Log.e(TAG, "SimpleWalkieTalkieManager not initialized")
            return
        }
        
        if (currentUserId.isEmpty()) {
            Log.e(TAG, "User not authenticated")
            return
        }
        
        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not properly initialized")
            return
        }
        
        if (isTransmitting) {
            Log.w(TAG, "Already transmitting")
            return
        }
        
        isTransmitting = true
        
        // Set audio mode for better voice quality
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true
        
        // Start recording and streaming
        startRecording()
        
        Log.d(TAG, "Started transmission")
    }
    
    fun stopTransmission() {
        if (!isTransmitting) return
        
        isTransmitting = false
        
        // Stop recording
        recordingJob?.cancel()
        audioRecord?.stop()
        
        // Reset audio mode
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        
        // Notify others that we stopped transmitting
        notifyTransmissionStop()
        
        Log.d(TAG, "Stopped transmission")
    }
    
    private fun startRecording() {
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val record = audioRecord
                if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord not available for recording")
                    return@launch
                }
                
                record.startRecording()
                
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "Failed to start recording - state: ${record.recordingState}")
                    return@launch
                }
                
                val buffer = ByteArray(bufferSize)
                
                while (isTransmitting && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        // Encode audio data and send to Firebase
                        val encodedAudio = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                        sendAudioData(encodedAudio)
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "Error reading audio data: $bytesRead")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during recording", e)
            } finally {
                try {
                    audioRecord?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recording", e)
                }
            }
        }
    }
    
    private fun sendAudioData(encodedAudio: String) {
        val audioData = hashMapOf(
            "userId" to currentUserId,
            "audioData" to encodedAudio,
            "timestamp" to System.currentTimeMillis(),
            "sampleRate" to sampleRate
        )
        
        firestore.collection("trips").document(tripId)
            .collection("voice_stream")
            .add(audioData)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send audio data", e)
            }
    }
    
    private fun listenForAudioData() {
        firestore.collection("trips").document(tripId)
            .collection("voice_stream")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Listen failed: ", e)
                    return@addSnapshotListener
                }
                
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val userId = data["userId"] as? String
                        val audioData = data["audioData"] as? String
                        val timestamp = data["timestamp"] as? Long ?: 0
                        
                        // Only play audio from other users and recent data (within 5 seconds)
                        if (userId != currentUserId && audioData != null) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - timestamp < 5000) {
                                playAudioData(audioData)
                            }
                        }
                    }
                }
            }
    }
    
    private fun playAudioData(encodedAudio: String) {
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioData = Base64.decode(encodedAudio, Base64.NO_WRAP)
                
                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.play()
                    }
                    audioTrack?.write(audioData, 0, audioData.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during playback", e)
            }
        }
    }
    
    private fun notifyTransmissionStop() {
        // Clean up old audio data
        val cutoffTime = System.currentTimeMillis() - 10000 // 10 seconds ago
        
        firestore.collection("trips").document(tripId)
            .collection("voice_stream")
            .whereLessThan("timestamp", cutoffTime)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.delete()
                }
            }
    }
    
    fun cleanup() {
        stopTransmission()
        
        recordingJob?.cancel()
        playbackJob?.cancel()
        
        audioRecord?.release()
        audioTrack?.release()
        
        isInitialized = false
        Log.d(TAG, "SimpleWalkieTalkieManager cleaned up")
    }
}
