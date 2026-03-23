package com.example.roadtripcompanion

import com.example.roadtripcompanion.MapsActivity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.roadtripcompanion.databinding.ActivityTripBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TripActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripBinding
    private lateinit var createTripButton: Button
    private lateinit var joinTripButton: Button
    private lateinit var tripCodeInput: EditText

    private val firestore = FirebaseFirestore.getInstance()
    private fun getCurrentUid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if user is authenticated, if not, sign in anonymously
        if (FirebaseAuth.getInstance().currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
        }

        createTripButton = binding.createTripButton
        joinTripButton = binding.joinTripButton
        tripCodeInput = binding.tripCodeInput

        createTripButton.setOnClickListener { createTrip() }
        joinTripButton.setOnClickListener { joinTrip() }
    }

    private fun createTrip() {
        val uid = getCurrentUid()
        if (uid.isEmpty()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // First prompt for user name
        promptForCustomName { userName ->
            val tripId = firestore.collection("trips").document().id
            val tripData = hashMapOf(
                "createdBy" to uid,
                "members" to listOf(uid),
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("trips").document(tripId).set(tripData)
                .addOnSuccessListener {
                    saveTripLocally(tripId)
                    goToMaps()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to create trip: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun joinTrip() {
        val uid = getCurrentUid()
        val code = tripCodeInput.text.toString().trim()
        if (code.isEmpty()) {
            Toast.makeText(this, "Enter a trip code", Toast.LENGTH_SHORT).show()
            return
        }

        if (uid.isEmpty()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // First prompt for user name
        promptForCustomName { userName ->
            firestore.collection("trips").document(code).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val members = (doc.get("members") as? MutableList<*>)?.toMutableList() ?: mutableListOf()
                    if (!members.contains(uid)) {
                        members.add(uid)
                        firestore.collection("trips").document(code).update("members", members)
                    }
                    saveTripLocally(code)
                    goToMaps()
                } else {
                    Toast.makeText(this, "Trip code not found", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(this, "Error joining trip: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTripLocally(tripId: String) {
        getSharedPreferences("trip_prefs", MODE_PRIVATE).edit()
            .putString("trip_id", tripId).apply()
    }

    private fun goToMaps() {
        startActivity(Intent(this, MapsActivity::class.java))
        finish()
    }
    
    private fun promptForCustomName(onNameProvided: (String) -> Unit) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Enter Your Name")
        builder.setMessage("Please enter your name to be displayed to other trip members:")
        
        val input = EditText(this)
        input.hint = "Your name"
        builder.setView(input)
        
        builder.setPositiveButton("OK") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                // Save the name to Firestore
                val userMap = mapOf("name" to name)
                firestore.collection("users").document(getCurrentUid())
                    .set(userMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Name saved successfully!", Toast.LENGTH_SHORT).show()
                        onNameProvided(name)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to save name: ${e.message}", Toast.LENGTH_SHORT).show()
                        onNameProvided(name) // Still proceed even if saving fails
                    }
            } else {
                Toast.makeText(this, "Please enter a valid name", Toast.LENGTH_SHORT).show()
                promptForCustomName(onNameProvided) // Prompt again if name is empty
            }
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            finish() // Close the activity if user cancels
        }
        
        builder.setCancelable(false) // Don't allow dismissing without entering a name
        builder.show()
    }
}
