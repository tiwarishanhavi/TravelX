package com.example.roadtripcompanion
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.roadtripcompanion.LocationService
import com.example.roadtripcompanion.R
import com.example.roadtripcompanion.databinding.ActivityMapsBinding
import com.example.roadtripcompanion.ChatAdapter
import com.example.roadtripcompanion.ChatMessage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roadtripcompanion.adapters.PlaceSearchAdapter
import com.example.roadtripcompanion.models.PlaceSearchResult
import com.example.roadtripcompanion.models.NavigationData
import com.example.roadtripcompanion.models.NavigationStep
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import com.google.android.gms.location.Priority

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private var destinationPolyline: Polyline? = null
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val userMarkers = mutableMapOf<String, Marker>()
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var destinationText: TextView
    private lateinit var distanceText: TextView
    private lateinit var etaText: TextView
    private lateinit var memberListText: TextView
    private lateinit var memberDistancesText: TextView

    private lateinit var signalBreak: Button
    private lateinit var signalHelp: Button
    private lateinit var signalReached: Button
    private lateinit var leaveTripButton: Button
    private lateinit var endTripButton: Button
    private lateinit var firestore: FirebaseFirestore
    private lateinit var uid: String
    private lateinit var locationRequest: LocationRequest



// State management for preventing unnecessary updates
    private var lastNavigationTimestamp = 0L
    private var lastDestinationLatLng: LatLng? = null
    private var isProcessingNavigation = false
    private var isNavigating = false
    private var destinationMarker: Marker? = null

    // Chat UI
    private lateinit var chatInput: android.widget.EditText
    private lateinit var sendMessageButton: Button
    //private lateinit var chatMessages: TextView
    private lateinit var chatRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    
    // Floating Action Buttons and panels
    private lateinit var fabTripInfo: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var fabChat: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var tripInfoCard: androidx.cardview.widget.CardView
    private lateinit var chatCard: androidx.cardview.widget.CardView
    private lateinit var tripCodeText: TextView
    private lateinit var shareTripCodeButton: Button
    
    // Search UI components
    private lateinit var searchDestination: EditText
    private lateinit var clearSearchButton: ImageButton
    private lateinit var searchResultsRecyclerView: RecyclerView
    
    // Places API
    private lateinit var placesClient: PlacesClient
    private lateinit var searchAdapter: PlaceSearchAdapter
    private var autocompleteToken: AutocompleteSessionToken? = null
    
    // Navigation UI elements
    private lateinit var navigationCard: androidx.cardview.widget.CardView
    private lateinit var navigationInstruction: TextView
    private lateinit var navigationDistance: TextView
    private lateinit var navigationETA: TextView
    private lateinit var stopNavigationButton: Button
    
    // Navigation state
    private var currentRoute: List<NavigationStep> = emptyList()
    private var currentStepIndex = 0
    private var routePolyline: Polyline? = null
    
    // Text-to-Speech for voice navigation
    private lateinit var textToSpeech: TextToSpeech
    
    // Walkie Talkie functionality
    private var audioManager: SimpleAudioManager? = null
    private var mockAudioManager: MockAudioManager? = null
    private var useMockAudio = false
    private lateinit var pushToTalkButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private var isRecording = false
    private var autoStopRunnable: Runnable? = null
    private val autoStopHandler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val AUDIO_PERMISSION_REQUEST_CODE = 2
    }

    private lateinit var tripId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tripId = getSharedPreferences("trip_prefs", MODE_PRIVATE).getString("trip_id", null) ?: run {
            Toast.makeText(this, "No trip selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startService(Intent(this, LocationService::class.java))

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase first
        firestore = FirebaseFirestore.getInstance()
        uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        
        // Firebase Anonymous Sign-In if not already signed in
        if (FirebaseAuth.getInstance().currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        // Check if user has a custom name, if not prompt for one
                        checkAndPromptForCustomName()
                    } else {
                        // Handle error
                    }
                }
        } else {
            // User already signed in, check if they have a custom name
            checkAndPromptForCustomName()
        }

        signalBreak = findViewById(R.id.signalBreak)
        signalHelp = findViewById(R.id.signalHelp)
        signalReached = findViewById(R.id.signalReached)
        leaveTripButton = findViewById(R.id.leaveTripButton)
        endTripButton = findViewById(R.id.endTripButton)
        // Toggle visibility of End Trip button based on leadership
        firestore.collection("trips").document(tripId).get().addOnSuccessListener {
            if (it.getString("createdBy") == uid) {
                endTripButton.visibility = android.view.View.VISIBLE
            } else {
                endTripButton.visibility = android.view.View.GONE
            }
        }

        leaveTripButton.setOnClickListener {
            firestore.collection("trips").document(tripId).get().addOnSuccessListener { doc ->
                val members = (doc["members"] as? MutableList<*>)?.filter { it != uid }
                firestore.collection("trips").document(tripId).update("members", members)
                    .addOnSuccessListener {
                        // Remove user's location data
                        firestore.collection("trips").document(tripId)
                            .collection("locations")
                            .document(uid)
                            .delete()
                        
                        // Remove user's signal data
                        firestore.collection("trips").document(tripId)
                            .collection("signals")
                            .document(uid)
                            .delete()
                        
                        // Remove user's voice data
                        firestore.collection("trips").document(tripId)
                            .collection("voice_data")
                            .whereEqualTo("userId", uid)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                for (document in querySnapshot.documents) {
                                    document.reference.delete()
                                }
                            }
                        
                        Toast.makeText(this, "Left the trip successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to leave trip: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        endTripButton.setOnClickListener {
            // Confirm with the user before ending the trip
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("End Trip")
            builder.setMessage("Are you sure you want to end this trip? This will close the trip for all members.")
            builder.setPositiveButton("Yes, End Trip") { _, _ ->
                endTripForAllMembers()
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }

        // Chat view refs
        chatInput = findViewById(R.id.chatInput)
        sendMessageButton = findViewById(R.id.sendMessageButton)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter()
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        // Initialize FAB buttons and panels
        fabTripInfo = findViewById(R.id.fabTripInfo)
        fabChat = findViewById(R.id.fabChat)
        tripInfoCard = findViewById(R.id.tripInfoCard)
        chatCard = findViewById(R.id.chatCard)
        tripCodeText = findViewById(R.id.tripCodeText)
        shareTripCodeButton = findViewById(R.id.shareTripCodeButton)
    
        // Initialize search UI components
        searchDestination = findViewById(R.id.searchDestination)
        clearSearchButton = findViewById(R.id.clearSearchButton)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)

        // Places API initialization
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)
        autocompleteToken = AutocompleteSessionToken.newInstance()

        // Setup search adapter
        searchAdapter = PlaceSearchAdapter { place ->
            fetchPlaceDetails(place)
        }
        searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MapsActivity)
            adapter = searchAdapter
        }

        // Add TextWatcher to searchDestination EditText
        searchDestination.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    fetchAutocompletePredictions(s.toString())
                } else {
                    searchAdapter.setPlaces(emptyList())
                }
            }
        })

        // Clear search button
        clearSearchButton.setOnClickListener {
            searchDestination.text.clear()
            searchAdapter.setPlaces(emptyList())
        }
        
        // Initialize navigation UI elements
        navigationCard = findViewById(R.id.navigationCard)
        navigationInstruction = findViewById(R.id.navigationInstruction)
        navigationDistance = findViewById(R.id.navigationDistance)
        navigationETA = findViewById(R.id.navigationETA)
        stopNavigationButton = findViewById(R.id.stopNavigationButton)
        
        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.getDefault()
            }
        }
        
        // Set up stop navigation button
        stopNavigationButton.setOnClickListener {
            stopNavigation()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        destinationText = findViewById(R.id.destinationText)
        distanceText = findViewById(R.id.distanceText)
        etaText = findViewById(R.id.etaText)
        memberListText = findViewById(R.id.memberListText)
        memberDistancesText = findViewById(R.id.memberDistancesText)

        locationRequest = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    uploadLocationToFirebase(location.latitude, location.longitude)
                    // Update member distances when user's location changes
                    updateMemberDistances()
                }
            }
        }


        // Signal button listeners
        signalBreak.setOnClickListener { sendSignal("BREAK") }
        signalHelp.setOnClickListener { sendSignal("HELP") }
        signalReached.setOnClickListener { sendSignal("REACHED") }
        
        // FAB button listeners
        fabTripInfo.setOnClickListener {
            togglePanel(tripInfoCard)
        }
        
        fabChat.setOnClickListener {
            togglePanel(chatCard)
        }
        
        // Trip code functionality
        tripCodeText.text = tripId
        tripCodeText.setOnClickListener {
            copyTripCodeToClipboard()
        }
        
        shareTripCodeButton.setOnClickListener {
            shareTripCode()
        }

        // --- Chat logic ---
        sendMessageButton.setOnClickListener {
            val messageText = chatInput.text.toString().trim()
            val sender = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            if (messageText.isNotEmpty()) {
                val message = mapOf(
                    "uid" to sender,
                    "text" to messageText,
                    "timestamp" to System.currentTimeMillis()
                )
                FirebaseFirestore.getInstance()
                    .collection("trips").document(tripId ?: return@setOnClickListener)
                    .collection("messages").add(message)
                chatInput.text.clear()
            }
        }


        // Listen for trip destination updates - immediate navigation start
        FirebaseFirestore.getInstance()
            .collection("trips").document(tripId ?: return)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val destination = snapshot.get("destination") as? Map<*, *>
                    val lat = destination?.get("lat") as? Double
                    val lng = destination?.get("lng") as? Double
                    if (lat != null && lng != null) {
                        val destinationLatLng = LatLng(lat, lng)
                        if (lastDestinationLatLng == null || lastDestinationLatLng != destinationLatLng) {
                            lastDestinationLatLng = destinationLatLng
                            if (::mMap.isInitialized) {
                                // Add destination marker immediately
                                destinationMarker?.remove()
                                destinationMarker = mMap.addMarker(
                                    MarkerOptions()
                                        .position(destinationLatLng)
                                        .title("Shared Destination")
                                )

                                // Start navigation immediately without delay
                                Toast.makeText(this, "🧭 Navigation started to destination", Toast.LENGTH_SHORT).show()
                                enhancedStartNavigation(destinationLatLng)
                            }
                        }
                    }
                }
            }
        
        // Listen for chat messages
        FirebaseFirestore.getInstance()
            .collection("trips").document(tripId ?: return)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messageList = mutableListOf<ChatMessage>()
                    val firestore = FirebaseFirestore.getInstance()
                    val tasks = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: return@mapNotNull null
                        val text = doc.getString("text") ?: ""
                        firestore.collection("users").document(uid).get().continueWith { task ->
                            val name = task.result?.getString("name") ?: uid.take(6)
                            ChatMessage(uid, name, text)
                        }
                    }
                    com.google.android.gms.tasks.Tasks.whenAllComplete(tasks).addOnSuccessListener {
                        messageList.addAll(tasks.mapNotNull { it.result })
                        chatAdapter.updateMessages(messageList)
                        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }
        // --- end chat logic ---
        
        // Load the map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        firestore.collection("trips").document(tripId ?: return)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val members = snapshot.get("members") as? List<*>
                    if (members != null) {
                        // Get custom names for each member
                        val memberNames = mutableListOf<String>()
                        val nameRetrievalTasks = members.map { memberId ->
                            firestore.collection("users").document(memberId.toString()).get().continueWith { task ->
                                val customName = task.result?.getString("name")
                                if (customName != null && customName.isNotEmpty()) {
                                    customName
                                } else {
                                    memberId.toString().take(6) // Fallback to shortened UID
                                }
                            }
                        }
                        
                        // Wait for all name retrieval tasks to complete
                        com.google.android.gms.tasks.Tasks.whenAllComplete(nameRetrievalTasks).addOnSuccessListener {
                            memberNames.clear()
                            memberNames.addAll(nameRetrievalTasks.mapNotNull { it.result })
                            memberListText.text = memberNames.joinToString(separator = "\n") { "• $it" }
                        }.addOnFailureListener {
                            // Fallback to UIDs if name retrieval fails
                            memberListText.text = members.joinToString(separator = "\n") { "• ${it.toString().take(6)}" }
                        }
                    }
                } else {
                    // Trip document doesn't exist (deleted by leader)
                    Toast.makeText(this, "Trip has been ended by the leader", Toast.LENGTH_LONG).show()
                    finish()
                }
            }

        firestore.collection("trips")
            .document(tripId ?: return@onCreate)
            .collection("signals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val senderId = doc.id
                        if (senderId != uid) {
                            val type = doc.getString("type")
                            if (type != null) {
                                // Get sender's custom name
                                getUserCustomName(senderId) { senderName ->
                                    Toast.makeText(this, "Signal from $senderName: $type", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        
        // Initialize Audio Manager with fallback
        initializeAudioManager()
        
        // Check audio permissions and initialize walkie talkie
        checkAudioPermissions()
    }

    private fun checkAndPromptForCustomName() {
        // Check if the user already has a custom name in Firestore
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val userName = document.getString("name")
                if (userName.isNullOrEmpty()) {
                    // User doesn't have a custom name, prompt them to enter one
                    promptForCustomName()
                }
            }
            .addOnFailureListener { exception ->
                // If there's an error, still prompt for name as a fallback
                promptForCustomName()
            }
    }
    
    // Function to refresh all markers with updated names
    private fun refreshMarkerTitles() {
        userMarkers.forEach { (userId, marker) ->
            getUserCustomName(userId) { userName ->
                marker.title = "Member: $userName"
            }
        }
    }

    private fun updateMemberDistances() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { currentLocation ->
                    val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                    
                    if (userMarkers.isEmpty()) {
                        memberDistancesText.text = "No other members in this trip"
                        return@let
                    }
                    
                    val distanceStrings = mutableListOf<String>()
                    var completed = 0
                    
                    userMarkers.forEach { (userId, marker) ->
                        val memberLocation = marker.position
                        val distance = calculateDistance(currentLatLng, memberLocation)
                        
                        getUserCustomName(userId) { userName ->
                            val distanceStr = "${userName}: ${String.format("%.2f", distance / 1000)} km"
                            distanceStrings.add(distanceStr)
                            completed++
                            
                            // Update UI when all distances are calculated
                            if (completed == userMarkers.size) {
                                memberDistancesText.text = distanceStrings.joinToString(separator = "\n")
                            }
                        }
                    }
                }
            }.addOnFailureListener {
                memberDistancesText.text = "Location unavailable"
            }
        } else {
            memberDistancesText.text = "Location permission required"
        }
    }

    private fun promptForCustomName() {
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
                firestore.collection("users").document(uid)
                    .set(userMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Name saved successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to save name: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Please enter a valid name", Toast.LENGTH_SHORT).show()
                promptForCustomName() // Prompt again if name is empty
            }
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.setCancelable(false) // Don't allow dismissing without entering a name
        builder.show()
    }

    private fun drawRouteToDestination(origin: LatLng, destination: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            val apiKey = getString(R.string.google_maps_key)
            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${origin.latitude},${origin.longitude}&" +
                    "destination=${destination.latitude},${destination.longitude}&" +
                    "key=$apiKey"

            val result = URL(url).readText()
            val json = JSONObject(result)
            val routes = json.getJSONArray("routes")
            if (routes.length() > 0) {
                val points = mutableListOf<LatLng>()
                val steps = routes.getJSONObject(0)
                    .getJSONArray("legs").getJSONObject(0)
                    .getJSONArray("steps")

                for (i in 0 until steps.length()) {
                    val polyline = steps.getJSONObject(i)
                        .getJSONObject("polyline")
                        .getString("points")
                    points.addAll(decodePolyline(polyline))
                }

                withContext(Dispatchers.Main) {
                    destinationPolyline?.remove()
                    destinationPolyline = mMap.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(ContextCompat.getColor(this@MapsActivity, com.example.roadtripcompanion.R.color.purple_500))
                            .width(12f)
                    )

                    val leg = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0)
                    val distance = leg.getJSONObject("distance").getString("text")
                    val duration = leg.getJSONObject("duration").getString("text")
                    Toast.makeText(
                        this@MapsActivity,
                        "Distance: $distance, ETA: $duration",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.destinationText.text = "Destination: ${destination.latitude}, ${destination.longitude}"
                    binding.distanceText.text = "Distance: $distance"
                    binding.etaText.text = "ETA: $duration"
                }
            }
        }
    }


    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Show rationale dialog before requesting permission
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Location Permission Required")
            builder.setMessage("This app needs location access to:\n\n• Show your position on the map\n• Share your location with trip members\n• Provide navigation assistance\n\nPlease grant location permission to continue.")
            builder.setPositiveButton("Grant Permission") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            builder.setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Location permission is required for core functionality", Toast.LENGTH_LONG).show()
                finish()
            }
            builder.setCancelable(false)
            builder.show()
        } else {
            enableUserLocation()
        }
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }
    }


    private fun uploadLocationToFirebase(latitude: Double, longitude: Double) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val firestore = FirebaseFirestore.getInstance()

        val locationMap = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("trips").document(tripId)
            .collection("locations")
            .document(currentUser?.uid ?: "unknown")
            .set(locationMap)
    }
    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    override fun onResume() {
        super.onResume()
        if (::fusedLocationClient.isInitialized &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        checkLocationPermission()
        
        // Get user's current location and zoom to it immediately
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // First try to get last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18f), 1000, null)
                    Log.d("MapsActivity", "Zoomed to user location: $userLatLng")
                }
            }.addOnFailureListener {
                Log.e("MapsActivity", "Failed to get last location", it)
                // If no last location, request current location
                requestCurrentLocation()
            }
            
            // Also request a fresh location update
            requestCurrentLocation()
        }
        
        // Allow trip leader to set destination by long-clicking on map
        mMap.setOnMapLongClickListener { latLng ->
            firestore.collection("trips").document(tripId)
                .get().addOnSuccessListener { doc ->
                    if (doc.exists() && doc.getString("createdBy") == uid) {
                        val destination = mapOf("lat" to latLng.latitude, "lng" to latLng.longitude)
                        firestore.collection("trips").document(tripId)
                            .update("destination", destination)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Destination set for all members", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Only the trip leader can set destination", Toast.LENGTH_SHORT).show()
                    }
                }
        }
        
        // Start location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
        
        listenForOtherUsers()
        
        // Initial distance calculation
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateMemberDistances()
        }, 2000) // Delay to allow location to be available
    }
    
    private fun requestCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val currentLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdates(1)
                .build()
            
            val singleLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    locationResult.lastLocation?.let { location ->
                        val userLatLng = LatLng(location.latitude, location.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18f), 1000, null)
                        Log.d("MapsActivity", "Fresh location obtained and zoomed: $userLatLng")
                    }
                }
            }
            
            fusedLocationClient.requestLocationUpdates(currentLocationRequest, singleLocationCallback, mainLooper)
        }
    }
    
    private fun listenForOtherUsers() {
        firestore.collection("trips").document(tripId)
            .collection("locations")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                // Get current active user IDs from the snapshot
                val activeUserIds = snapshots.documents.map { it.id }.toSet()
                
                // Remove markers for users who are no longer in the trip
                val usersToRemove = mutableListOf<String>()
                userMarkers.forEach { (userId, marker) ->
                    if (userId !in activeUserIds) {
                        marker.remove()
                        usersToRemove.add(userId)
                    }
                }
                usersToRemove.forEach { userId ->
                    userMarkers.remove(userId)
                }
                
                for (doc in snapshots.documents) {
                    val userId = doc.id
                    if (userId == uid) continue // Don't show your own marker
                    
                    val lat = doc.getDouble("latitude")
                    val lng = doc.getDouble("longitude")
                    
                    if (lat != null && lng != null) {
                        val position = LatLng(lat, lng)
                        
                        // Update or add marker for other users
                        val marker = userMarkers[userId]
                        if (marker != null) {
                            marker.position = position
                        } else {
                            // Get user's custom name for marker
                            getUserCustomName(userId) { userName ->
                                val newMarker = mMap.addMarker(
                                    MarkerOptions().position(position).title("Member: $userName")
                                )
                                if (newMarker != null) {
                                    userMarkers[userId] = newMarker
                                }
                            }
                        }
                    }
                }
                
                // Update member distances after processing all locations
                updateMemberDistances()
            }
    }

    private fun getUserCustomName(userId: String, callback: (String) -> Unit) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val userName = document.getString("name") ?: userId.take(6)
                callback(userName)
            }
            .addOnFailureListener {
                callback(userId.take(6)) // Fallback to UID prefix if error occurs
            }
    }
    
    private fun startNavigationToDestination(destination: LatLng) {
        if (isNavigating) {
            return // Already navigating
        }
        
        isProcessingNavigation = true
        isNavigating = true
        
        // Update destination text with coordinates
        destinationText.text = "Navigating to: ${destination.latitude.toString().take(8)}, ${destination.longitude.toString().take(8)}"
        
        // Start full navigation with directions API
        getDirectionsFromAPI(destination) { route ->
            currentRoute = route
            currentStepIndex = 0
            
            // Update navigation UI
            displayNavigationInstruction()
            
            // Show navigation started message
            Toast.makeText(this, "Navigation started to shared destination", Toast.LENGTH_SHORT).show()
            
            isProcessingNavigation = false
        }
    }
    
    private fun enhancedStartNavigation(destination: LatLng) {
        // Stop any existing navigation
        if (isNavigating) {
            stopNavigation()
        }

        isProcessingNavigation = true
        isNavigating = true

        // Update destination text immediately
        destinationText.text = "🎯 Starting navigation..."

        // Improve speed by preloading part of the route
        preloadRoute(destination) { preloadedRoute ->
            if (preloadedRoute.isNotEmpty()) {
                currentRoute = preloadedRoute
                currentStepIndex = 0
                displayNavigationInstruction() // Show the preloaded steps immediately
            }

            // Get full directions and start navigation
            getDirectionsFromAPIFast(destination) { route ->
                currentRoute = route
                currentStepIndex = 0

                // Update navigation UI
                displayNavigationInstruction()

                // Start real-time navigation tracking
                startNavigationTracking()

                isProcessingNavigation = false
            }
        }
    }

    // Function to preload route to quickly update UI
    private fun preloadRoute(destination: LatLng, callback: (List<NavigationStep>) -> Unit) {
        // Dummy preload functionality
        val dummyRoute = listOf(
            NavigationStep(
                "Head towards destination",
                "Calculating distance...",
                "Calculating time...",
                "straight",
                LatLng(destination.latitude - 0.05, destination.longitude - 0.05),
                destination
            )
        )
        callback(dummyRoute)
    }
    
    private fun sendSignal(type: String) {
        if (uid.isEmpty()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        
        val signal = mapOf(
            "type" to type,
            "timestamp" to System.currentTimeMillis()
        )
        
        firestore.collection("trips")
            .document(tripId)
            .collection("signals")
            .document(uid)
            .set(signal)
            .addOnSuccessListener {
                val message = when (type) {
                    "BREAK" -> "Break signal sent to trip members"
                    "HELP" -> "Help signal sent to trip members"
                    "REACHED" -> "Reached signal sent to trip members"
                    else -> "Signal sent"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send signal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun togglePanel(panel: androidx.cardview.widget.CardView) {
        if (panel.visibility == android.view.View.VISIBLE) {
            panel.visibility = android.view.View.GONE
        } else {
            panel.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun copyTripCodeToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Trip Code", tripId)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Trip code copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareTripCode() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Join my road trip! Use this code in RoadTripCompanion app: $tripId")
            putExtra(Intent.EXTRA_SUBJECT, "Road Trip Invitation")
        }
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Share Trip Code"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to share trip code", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun fetchAutocompletePredictions(query: String) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(autocompleteToken)
            .setQuery(query)
            .build()
        
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions
                val results = predictions.map { prediction ->
                    PlaceSearchResult(
                        placeId = prediction.placeId,
                        name = prediction.getPrimaryText(null).toString(),
                        address = prediction.getSecondaryText(null).toString(),
                        latLng = null // Will be fetched when selected
                    )
                }
                searchAdapter.setPlaces(results)
                searchResultsRecyclerView.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Search failed: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun fetchPlaceDetails(place: PlaceSearchResult) {
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val request = FetchPlaceRequest.builder(place.placeId, placeFields).build()
        
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val selectedPlace = response.place
                selectedPlace.latLng?.let { latLng ->
                    // Clear search results
                    searchResultsRecyclerView.visibility = View.GONE
                    searchDestination.text.clear()
                    
                    // Start navigation to selected place
                    startNavigation(selectedPlace)
                    
                    // Update destination on Firebase for all members
                    firestore.collection("trips").document(tripId)
                        .update("destination", mapOf("lat" to latLng.latitude, "lng" to latLng.longitude))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Navigation started to: ${selectedPlace.name}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to get place details: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun startNavigation(place: Place) {
        isNavigating = true
        
        // Get directions from Google Directions API
        getDirectionsFromAPI(place.latLng!!) { route ->
            currentRoute = route
            currentStepIndex = 0
            
            // Update navigation UI
            displayNavigationInstruction()
            
            // Share navigation with trip members
            shareNavigationWithTrip(place)
            
            // Hide search results
            searchResultsRecyclerView.visibility = View.GONE
            searchDestination.text.clear()
        }
    }
    
    private fun getDirectionsFromAPI(destination: LatLng, onResult: (List<NavigationStep>) -> Unit) {
        // Get current location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { currentLocation ->
                    val origin = LatLng(currentLocation.latitude, currentLocation.longitude)
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val apiKey = getString(R.string.google_maps_key)
                            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                                    "origin=${origin.latitude},${origin.longitude}&" +
                                    "destination=${destination.latitude},${destination.longitude}&" +
                                    "key=$apiKey"
                            
                            val result = URL(url).readText()
                            val json = JSONObject(result)
                            val routes = json.getJSONArray("routes")
                            
                            if (routes.length() > 0) {
                                val steps = routes.getJSONObject(0)
                                    .getJSONArray("legs").getJSONObject(0)
                                    .getJSONArray("steps")
                                
                                val navigationSteps = mutableListOf<NavigationStep>()
                                for (i in 0 until steps.length()) {
                                    val step = steps.getJSONObject(i)
                                    val instruction = step.getString("html_instructions")
                                        .replace("<[^>]*>".toRegex(), "") // Remove HTML tags
                                    val distance = step.getJSONObject("distance").getString("text")
                                    val duration = step.getJSONObject("duration").getString("text")
                                    val maneuver = step.optString("maneuver", "continue")
                                    
                                    val startLoc = step.getJSONObject("start_location")
                                    val endLoc = step.getJSONObject("end_location")
                                    
                                    navigationSteps.add(
                                        NavigationStep(
                                            instruction = instruction,
                                            distance = distance,
                                            duration = duration,
                                            maneuver = maneuver,
                                            startLocation = LatLng(startLoc.getDouble("lat"), startLoc.getDouble("lng")),
                                            endLocation = LatLng(endLoc.getDouble("lat"), endLoc.getDouble("lng"))
                                        )
                                    )
                                }
                                
                                withContext(Dispatchers.Main) {
                                    onResult(navigationSteps)
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MapsActivity, "Failed to get directions: ${e.message}", Toast.LENGTH_SHORT).show()
                                // Fallback to mock data
                                onResult(listOf(
                                    NavigationStep("Head to destination", "Unknown distance", "Unknown time", "straight", origin, destination)
                                ))
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun getDirectionsFromAPIFast(destination: LatLng, onResult: (List<NavigationStep>) -> Unit) {
        // Get current location with faster processing
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val origin = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else {
                    // Fallback to a default location if no location available
                    LatLng(37.7749, -122.4194) // San Francisco as fallback
                }
                
                // Start with a basic route while API loads
                val basicRoute = listOf(
                    NavigationStep(
                        "Head towards destination",
                        "Calculating distance...",
                        "Calculating time...",
                        "straight",
                        origin,
                        destination
                    )
                )
                
                // Immediately return basic route for instant feedback
                onResult(basicRoute)
                
                // Then get real directions in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val apiKey = getString(R.string.google_maps_key)
                        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                                "origin=${origin.latitude},${origin.longitude}&" +
                                "destination=${destination.latitude},${destination.longitude}&" +
                                "key=$apiKey"
                        
                        val result = URL(url).readText()
                        val json = JSONObject(result)
                        val routes = json.getJSONArray("routes")
                        
                        if (routes.length() > 0) {
                            val steps = routes.getJSONObject(0)
                                .getJSONArray("legs").getJSONObject(0)
                                .getJSONArray("steps")
                            
                            val navigationSteps = mutableListOf<NavigationStep>()
                            for (i in 0 until steps.length()) {
                                val step = steps.getJSONObject(i)
                                val instruction = step.getString("html_instructions")
                                    .replace("<[^>]*>".toRegex(), "") // Remove HTML tags
                                val distance = step.getJSONObject("distance").getString("text")
                                val duration = step.getJSONObject("duration").getString("text")
                                val maneuver = step.optString("maneuver", "continue")
                                
                                val startLoc = step.getJSONObject("start_location")
                                val endLoc = step.getJSONObject("end_location")
                                
                                navigationSteps.add(
                                    NavigationStep(
                                        instruction = instruction,
                                        distance = distance,
                                        duration = duration,
                                        maneuver = maneuver,
                                        startLocation = LatLng(startLoc.getDouble("lat"), startLoc.getDouble("lng")),
                                        endLocation = LatLng(endLoc.getDouble("lat"), endLoc.getDouble("lng"))
                                    )
                                )
                            }
                            
                            withContext(Dispatchers.Main) {
                                // Update with real directions
                                currentRoute = navigationSteps
                                currentStepIndex = 0
                                displayNavigationInstruction()
                                drawRouteOnMap(navigationSteps)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MapsActivity", "Failed to get directions", e)
                        // Keep the basic route if API fails
                    }
                }
            }
        }
    }
    
    private fun displayNavigationInstruction() {
        if (currentRoute.isNotEmpty()) {
            val currentStep = currentRoute[currentStepIndex]
            navigationInstruction.text = currentStep.instruction
            navigationDistance.text = currentStep.distance
            navigationETA.text = currentStep.duration
            navigationCard.visibility = View.VISIBLE
            
            // Draw route on map
            drawRouteOnMap(currentRoute)
            
            // Start real-time navigation tracking
            startNavigationTracking()
        }
    }
    
    private fun startNavigationTracking() {
        if (!isNavigating) return
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        
        val navigationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    updateNavigationProgress(LatLng(location.latitude, location.longitude))
                }
            }
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, navigationCallback, mainLooper)
        }
    }
    
    private fun updateNavigationProgress(currentLocation: LatLng) {
        if (currentRoute.isEmpty()) return
        
        val currentStep = currentRoute[currentStepIndex]
        val distanceToNextStep = calculateDistance(currentLocation, currentStep.endLocation)
        
        // Always update camera to follow user (Google Maps style)
        val bearing = calculateBearing(currentLocation, currentStep.endLocation)
        val cameraPosition = com.google.android.gms.maps.model.CameraPosition.Builder()
            .target(currentLocation)
            .zoom(18f)
            .bearing(bearing)
            .tilt(45f) // 3D perspective like Google Maps
            .build()
        
        mMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(cameraPosition),
            1000,
            null
        )
        
        // Check if we're close to the next step (within 30 meters)
        if (distanceToNextStep < 30) {
            // Move to next step
            currentStepIndex++
            if (currentStepIndex < currentRoute.size) {
                val nextStep = currentRoute[currentStepIndex]
                navigationInstruction.text = nextStep.instruction
                navigationDistance.text = nextStep.distance
                
                // Play voice instruction
                speakInstruction(nextStep.instruction)
                
                // Share step progress with trip members
                shareNavigationStepUpdate()
                
            } else {
                // Navigation complete
                speakInstruction("You have arrived at your destination")
                stopNavigation()
            }
        } else {
            // Update distance to next turn
            val distance = distanceToNextStep.toInt()
            navigationDistance.text = when {
                distance < 100 -> "In ${distance}m"
                distance < 1000 -> "In ${(distance / 100) * 100}m"
                else -> "In ${String.format("%.1f", distance / 1000.0)}km"
            }
        }
    }
    
    private fun calculateBearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)
        
        val y = Math.sin(deltaLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng)
        
        var bearing = Math.toDegrees(Math.atan2(y, x))
        bearing = (bearing + 360) % 360
        
        return bearing.toFloat()
    }
    
    private fun calculateDistance(from: LatLng, to: LatLng): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude,
            results
        )
        return results[0].toDouble()
    }
    
    private fun speakInstruction(instruction: String) {
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    private fun drawRouteOnMap(route: List<NavigationStep>) {
        // Get detailed polyline from the API for smoother route display
        if (route.isNotEmpty()) {
            val origin = route.first().startLocation
            val destination = route.last().endLocation
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val apiKey = getString(R.string.google_maps_key)
                    val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                            "origin=${origin.latitude},${origin.longitude}&" +
                            "destination=${destination.latitude},${destination.longitude}&" +
                            "key=$apiKey"
                    
                    val result = URL(url).readText()
                    val json = JSONObject(result)
                    val routes = json.getJSONArray("routes")
                    
                    if (routes.length() > 0) {
                        val overviewPolyline = routes.getJSONObject(0)
                            .getJSONObject("overview_polyline")
                            .getString("points")
                        
                        val points = decodePolyline(overviewPolyline)
                        
                        withContext(Dispatchers.Main) {
                            routePolyline?.remove()
                            routePolyline = mMap.addPolyline(
                                PolylineOptions()
                                    .addAll(points)
                                    .width(12f)
                                    .color(ContextCompat.getColor(this@MapsActivity, R.color.purple_700))
                                    .pattern(null) // Solid line
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to simple polyline
                    withContext(Dispatchers.Main) {
                        val polylineOptions = PolylineOptions()
                            .width(10f)
                            .color(ContextCompat.getColor(this@MapsActivity, R.color.purple_500))
                        
                        for (step in route) {
                            polylineOptions.add(step.startLocation)
                            polylineOptions.add(step.endLocation)
                        }
                        
                        routePolyline?.remove()
                        routePolyline = mMap.addPolyline(polylineOptions)
                    }
                }
            }
        }
    }
    
    private fun shareNavigationWithTrip(place: Place) {
        val routeSteps = currentRoute.map { step ->
            mapOf(
                "instruction" to step.instruction,
                "distance" to step.distance,
                "duration" to step.duration,
                "maneuver" to step.maneuver,
                "startLocation" to mapOf("lat" to step.startLocation.latitude, "lng" to step.startLocation.longitude),
                "endLocation" to mapOf("lat" to step.endLocation.latitude, "lng" to step.endLocation.longitude)
            )
        }
        
        val navigationData = NavigationData(
            isNavigating = true,
            destinationName = place.name ?: "Unknown Destination",
            destinationLatLng = place.latLng,
            currentInstruction = if (currentRoute.isNotEmpty()) currentRoute[0].instruction else "",
            routePolyline = "", // Could encode full polyline
            currentStepIndex = 0,
            startedBy = uid,
            routeSteps = routeSteps
        )
        
        firestore.collection("trips").document(tripId)
            .update("navigation", navigationData)
            .addOnSuccessListener {
                Toast.makeText(this, "Navigation shared with all trip members", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun shareNavigationStepUpdate() {
        if (currentRoute.isNotEmpty() && currentStepIndex < currentRoute.size) {
            val currentStep = currentRoute[currentStepIndex]
            
            val stepUpdate = mapOf(
                "currentStepIndex" to currentStepIndex,
                "currentInstruction" to currentStep.instruction,
                "timestamp" to System.currentTimeMillis(),
                "updatedBy" to uid
            )
            
            firestore.collection("trips").document(tripId)
                .update("navigation.currentStepIndex", currentStepIndex)
        }
    }
    
    private fun stopNavigation() {
        isNavigating = false
        navigationCard.visibility = View.GONE
        routePolyline?.remove()
        destinationMarker?.remove()
        
        // Clear route data
        currentRoute = emptyList()
        currentStepIndex = 0
        
        // Stop text-to-speech
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
        }
        
        Toast.makeText(this, "Navigation stopped", Toast.LENGTH_SHORT).show()
    }
    
    
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            
            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        
        return poly
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        
        // Clean up Audio Managers
        audioManager?.cleanup()
        mockAudioManager?.cleanup()
        
        // Clean up auto-stop handler
        autoStopRunnable?.let { autoStopHandler.removeCallbacks(it) }
    }
    
    private fun checkAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_REQUEST_CODE
            )
        } else {
            setupPushToTalkButton()
        }
    }

    private fun initializeAudioManager() {
        Log.d("MapsActivity", "Initializing audio manager...")
        
        // First try the real audio manager
        audioManager = SimpleAudioManager(this, tripId) { error ->
            Log.e("MapsActivity", "Audio error: $error")
            runOnUiThread {
                Toast.makeText(this, "Audio: $error", Toast.LENGTH_SHORT).show()
            }
        }
        
        val initialized = audioManager?.initialize() ?: false
        if (!initialized) {
            Log.w("MapsActivity", "Real audio failed, falling back to mock audio")
            useMockAudio = true
            
            // Initialize mock audio manager
            mockAudioManager = MockAudioManager(this, tripId) { error ->
                Log.e("MapsActivity", "Mock audio error: $error")
                runOnUiThread {
                    Toast.makeText(this, "Mock Audio: $error", Toast.LENGTH_SHORT).show()
                }
            }
            
            val mockInitialized = mockAudioManager?.initialize() ?: false
            if (!mockInitialized) {
                Log.e("MapsActivity", "Both real and mock audio failed to initialize")
            }
        }
        
        Log.d("MapsActivity", "Audio manager initialized. Using mock: $useMockAudio")
    }
    
    private fun setupPushToTalkButton() {
        // Audio manager already initialized
        
        pushToTalkButton = findViewById(R.id.pushToTalkButton)
        
        // Set initial state - not recording
        updateButtonState(false)
        
        // Simple click listener for toggle functionality
        pushToTalkButton.setOnClickListener {
            if (!isRecording) {
                // Start recording
                startRecording()
            } else {
                // Stop recording
                stopRecording()
            }
        }
    }
    
    private fun startRecording() {
        try {
            val success = if (useMockAudio) {
                mockAudioManager?.startRecording() ?: false
            } else {
                audioManager?.startRecording() ?: false
            }
            
            if (success) {
                isRecording = true
                updateButtonState(true)
                val mode = if (useMockAudio) "Mock" else "Real"
                Toast.makeText(this, "🎤 $mode Recording... Tap to stop (auto-stop in 30s)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to start recording", Toast.LENGTH_LONG).show()
                return
            }
        } catch (e: Exception) {
            Log.e("MapsActivity", "Error starting recording", e)
            isRecording = false
            updateButtonState(false)
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        
        // Set up auto-stop after 30 seconds to prevent accidental long recordings
        autoStopRunnable = Runnable {
            if (isRecording) {
                stopRecording()
                Toast.makeText(this, "Recording auto-stopped after 30 seconds", Toast.LENGTH_LONG).show()
            }
        }
        autoStopHandler.postDelayed(autoStopRunnable!!, 30000) // 30 seconds
    }
    
    private fun stopRecording() {
        try {
            if (useMockAudio) {
                mockAudioManager?.stopRecording()
            } else {
                audioManager?.stopRecording()
            }
            
            isRecording = false
            updateButtonState(false)
            Toast.makeText(this, "🔇 Recording stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MapsActivity", "Error stopping recording", e)
            isRecording = false
            updateButtonState(false)
            Toast.makeText(this, "Recording stopped with error", Toast.LENGTH_SHORT).show()
        } finally {
            // Cancel auto-stop if user manually stopped
            autoStopRunnable?.let { autoStopHandler.removeCallbacks(it) }
        }
    }
    
    private fun updateButtonState(isRecording: Boolean) {
        if (isRecording) {
            // Recording state - red background, microphone icon
            pushToTalkButton.setImageResource(android.R.drawable.ic_btn_speak_now)
            pushToTalkButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
        } else {
            // Not recording state - orange background, microphone icon
            pushToTalkButton.setImageResource(android.R.drawable.ic_btn_speak_now)
            pushToTalkButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.signal_help)
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableUserLocation()
                }
            }
            AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupPushToTalkButton()
                } else {
                    Toast.makeText(this, "Audio permission is required for walkie-talkie feature", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun endTripForAllMembers() {
        // Delete the trip document - this will trigger the snapshot listener in all members' apps
        firestore.collection("trips").document(tripId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Trip ended successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to end trip: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
