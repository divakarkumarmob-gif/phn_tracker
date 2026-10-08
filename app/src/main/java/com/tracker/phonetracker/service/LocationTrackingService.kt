package com.tracker.phonetracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.tracker.phonetracker.PhoneTrackerApp
import com.tracker.phonetracker.R
import com.tracker.phonetracker.data.model.LocationPayload
import com.tracker.phonetracker.data.repository.BackendRepository
import com.tracker.phonetracker.data.repository.FirebaseRepository
import com.tracker.phonetracker.ui.MainActivity
import com.tracker.phonetracker.utils.BatteryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 1001

        // Intent Actions
        const val ACTION_START_TRACKING = "com.tracker.phonetracker.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.tracker.phonetracker.STOP_TRACKING"
        const val ACTION_LOCATION_BROADCAST = "com.tracker.phonetracker.LOCATION_UPDATE"

        // Location Update Frequency
        private const val UPDATE_INTERVAL_MS = 10000L   // 10 seconds
        private const val FASTEST_INTERVAL_MS = 5000L   // 5 seconds

        var isServiceRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null

    private val firebaseRepository by lazy { FirebaseRepository() }
    private val backendRepository by lazy { BackendRepository(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationTrackingService onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        acquireWakeLock()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_TRACKING
        Log.d(TAG, "onStartCommand with action: $action")

        when (action) {
            ACTION_STOP_TRACKING -> {
                stopTrackingService()
                return START_NOT_STICKY
            }
            ACTION_START_TRACKING -> {
                startForegroundWithNotification()
                requestLocationUpdates()
                isServiceRunning = true
            }
        }

        // START_STICKY ensures Android restarts the service if it gets killed due to memory constraints
        return START_STICKY
    }

    /**
     * Acquires partial wake lock to keep CPU active during screen-off intervals.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneTracker::LocationWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "Partial WakeLock acquired for screen-off execution")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    /**
     * Starts foreground service with persistent notification according to Android 14+ guidelines.
     */
    private fun startForegroundWithNotification() {
        val notification = buildNotification(
            title = getString(R.string.notification_title),
            message = getString(R.string.notification_initial_text)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, message: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PhoneTrackerApp.TRACKING_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_tracker_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = buildNotification(
            title = getString(R.string.notification_title),
            message = message
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setMinUpdateDistanceMeters(0f)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates requested successfully (Interval: ${UPDATE_INTERVAL_MS}ms)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onNewLocation(location)
            }
        }
    }

    private fun onNewLocation(location: Location) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: "anonymous_user"
        val userEmail = user?.email ?: "anonymous@tracker.local"

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

        val batteryLevel = BatteryHelper.getBatteryLevel(applicationContext)
        val isCharging = BatteryHelper.isDeviceCharging(applicationContext)

        val payload = LocationPayload(
            userId = userId,
            userEmail = userEmail,
            deviceId = deviceId,
            deviceModel = deviceModel,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            speed = location.speed,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            timestamp = System.currentTimeMillis()
        )

        // 1. Update Persistent Notification Text
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(payload.timestamp))
        val notificationText = String.format(
            Locale.US,
            "Lat: %.5f, Lon: %.5f | Bat: %d%% | %s",
            payload.latitude, payload.longitude, payload.batteryLevel, timeStr
        )
        updateNotification(notificationText)

        // 2. Broadcast to UI if MainActivity is open
        val broadcastIntent = Intent(ACTION_LOCATION_BROADCAST).apply {
            putExtra("latitude", payload.latitude)
            putExtra("longitude", payload.longitude)
            putExtra("accuracy", payload.accuracy)
            putExtra("speed", payload.speed)
            putExtra("batteryLevel", payload.batteryLevel)
            putExtra("isCharging", payload.isCharging)
            putExtra("timestamp", payload.timestamp)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        // 3. Asynchronously upload to Firebase Firestore & Render Backend
        serviceScope.launch {
            // Sync with Firebase Firestore
            firebaseRepository.saveLocation(payload)

            // Sync with Render backend API
            backendRepository.postLocationToBackend(payload)
        }
    }

    private fun stopTrackingService() {
        Log.d(TAG, "Stopping tracking service")
        isServiceRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LocationTrackingService onDestroy")
        isServiceRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
