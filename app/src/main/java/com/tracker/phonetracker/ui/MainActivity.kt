package com.tracker.phonetracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.tracker.phonetracker.R
import com.tracker.phonetracker.data.repository.BackendRepository
import com.tracker.phonetracker.databinding.ActivityMainBinding
import com.tracker.phonetracker.service.LocationTrackingService
import com.tracker.phonetracker.utils.BatteryHelper
import com.tracker.phonetracker.utils.PermissionHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    // Location & Notification permission request launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            checkBackgroundLocationPermission()
        } else {
            Toast.makeText(
                this,
                "Location permission is required for tracking to work",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Background Location permission request launcher (Android 10+)
    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Background tracking requires 'Allow all the time' location permission",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Broadcast receiver for live location updates while activity is in foreground
    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationTrackingService.ACTION_LOCATION_BROADCAST) {
                val lat = intent.getDoubleExtra("latitude", 0.0)
                val lon = intent.getDoubleExtra("longitude", 0.0)
                val acc = intent.getFloatExtra("accuracy", 0f)
                val speed = intent.getFloatExtra("speed", 0f)
                val battery = intent.getIntExtra("batteryLevel", 0)
                val isCharging = intent.getBooleanExtra("isCharging", false)
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

                updateLocationDisplay(lat, lon, acc, speed, battery, isCharging, timestamp)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            navigateToLogin()
            return
        }

        setupUserInfo(currentUser.displayName, currentUser.email)
        setupClickListeners()
        checkPermissions()
        checkBatteryOptimization()
    }

    override fun onResume() {
        super.onResume()
        updateTrackingUIState(LocationTrackingService.isServiceRunning)
        checkBatteryOptimization()

        val filter = IntentFilter(LocationTrackingService.ACTION_LOCATION_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(locationUpdateReceiver)
        } catch (_: Exception) {}
    }

    private fun setupUserInfo(displayName: String?, email: String?) {
        binding.tvWelcomeUser.text = if (!displayName.isNullOrBlank()) "Hi, $displayName" else "Phone Tracker"
        binding.tvUserEmail.text = email ?: "user@tracker.com"

        val backendRepo = BackendRepository(applicationContext)
        val serverUrl = backendRepo.getRenderBaseUrl()
        binding.tvSyncStatus.text = "Firebase: Connected | Render: $serverUrl"
    }

    private fun setupClickListeners() {
        binding.btnToggleTracking.setOnClickListener {
            if (LocationTrackingService.isServiceRunning) {
                stopTracking()
            } else {
                startTracking()
            }
        }

        binding.btnOpenMap.setOnClickListener {
            openInMaps()
        }

        binding.btnFixBatteryOptimization.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        binding.btnLogout.setOnClickListener {
            confirmSignOut()
        }
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasLocationPermissions(this) || !PermissionHelper.hasNotificationPermission(this)) {
            requestPermissionsLauncher.launch(PermissionHelper.getInitialPermissionsToRequest())
        } else {
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PermissionHelper.hasBackgroundLocationPermission(this)) {
            AlertDialog.Builder(this)
                .setTitle("Allow Background Location")
                .setMessage("To track this device when the screen is turned off or locked, please select 'Allow all the time' on the next screen.")
                .setPositiveButton("Grant") { _, _ ->
                    requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun checkBatteryOptimization() {
        val isIgnored = BatteryHelper.isBatteryOptimizationIgnored(this)
        binding.cardBatteryOptimization.visibility = if (isIgnored) View.GONE else View.VISIBLE
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        try {
            val intent = BatteryHelper.getIgnoreBatteryOptimizationIntent(this)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Please allow unrestricted battery in app settings", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startTracking() {
        if (!PermissionHelper.hasLocationPermissions(this)) {
            checkPermissions()
            return
        }

        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        updateTrackingUIState(true)
        Toast.makeText(this, "Live Background Tracking Started!", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        startService(serviceIntent)
        updateTrackingUIState(false)
        Toast.makeText(this, "Tracking Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateTrackingUIState(isRunning: Boolean) {
        if (isRunning) {
            binding.tvTrackingStatusBadge.text = getString(R.string.status_active)
            binding.tvTrackingStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.emerald_active))
            binding.btnToggleTracking.text = getString(R.string.btn_stop_tracking)
            binding.btnToggleTracking.setBackgroundColor(ContextCompat.getColor(this, R.color.rose_inactive))
        } else {
            binding.tvTrackingStatusBadge.text = getString(R.string.status_stopped)
            binding.tvTrackingStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.rose_inactive))
            binding.btnToggleTracking.text = getString(R.string.btn_start_tracking)
            binding.btnToggleTracking.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_indigo))
        }
    }

    private fun updateLocationDisplay(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float,
        batteryLevel: Int,
        isCharging: Boolean,
        timestamp: Long
    ) {
        currentLatitude = latitude
        currentLongitude = longitude

        binding.tvLatitude.text = String.format(Locale.US, "%.6f", latitude)
        binding.tvLongitude.text = String.format(Locale.US, "%.6f", longitude)
        binding.tvAccuracy.text = String.format(Locale.US, "± %.1f meters", accuracy)
        binding.tvSpeed.text = String.format(Locale.US, "%.1f km/h", speed * 3.6f)
        binding.tvBattery.text = "$batteryLevel% ${if (isCharging) "⚡ (Charging)" else ""}"

        val timeFormatted = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))
        binding.tvLastUpdated.text = timeFormatted
    }

    private fun openInMaps() {
        val lat = currentLatitude
        val lon = currentLongitude

        if (lat == null || lon == null) {
            Toast.makeText(this, "No location acquired yet. Start tracking first.", Toast.LENGTH_SHORT).show()
            return
        }

        val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Tracker+Device)")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            startActivity(mapIntent)
        } catch (_: Exception) {
            // Fallback to browser Google Maps
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to log out? Location tracking will stop.")
            .setPositiveButton("Logout") { _, _ ->
                stopTracking()
                auth.signOut()

                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
                    navigateToLogin()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
