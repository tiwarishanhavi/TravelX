package com.example.roadtripcompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var startTripButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase Anonymous Auth (optional)
        if (FirebaseAuth.getInstance().currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        // Handle auth error
                    }
                }
        }

        startTripButton = findViewById(R.id.startTripButton)

        startTripButton.setOnClickListener {
            startActivity(Intent(this, TripActivity::class.java))
            finish()
        }
    }
}