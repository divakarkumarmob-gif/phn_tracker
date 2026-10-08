package com.tracker.phonetracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tracker.phonetracker.data.model.LocationPayload
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    companion object {
        private const val TAG = "FirebaseRepository"
        private const val USERS_COLLECTION = "users"
        private const val DEVICES_COLLECTION = "devices"
        private const val HISTORY_SUBCOLLECTION = "history"
    }

    /**
     * Uploads the latest device location to Firebase Firestore:
     * 1. Updates the device document with `lastLocation` summary
     * 2. Adds a new document in the `history` sub-collection for route mapping
     */
    suspend fun saveLocation(payload: LocationPayload): Result<Unit> {
        return try {
            if (payload.userId.isBlank() || payload.deviceId.isBlank()) {
                return Result.failure(IllegalArgumentException("userId and deviceId cannot be empty"))
            }

            val deviceDocRef = firestore.collection(USERS_COLLECTION)
                .document(payload.userId)
                .collection(DEVICES_COLLECTION)
                .document(payload.deviceId)

            // 1. Update latest device snapshot
            val deviceSummary = hashMapOf(
                "deviceId" to payload.deviceId,
                "deviceModel" to payload.deviceModel,
                "userEmail" to payload.userEmail,
                "lastLatitude" to payload.latitude,
                "lastLongitude" to payload.longitude,
                "lastAltitude" to payload.altitude,
                "accuracy" to payload.accuracy,
                "speed" to payload.speed,
                "batteryLevel" to payload.batteryLevel,
                "isCharging" to payload.isCharging,
                "lastUpdated" to payload.timestamp,
                "isOnline" to true
            )

            deviceDocRef.set(deviceSummary, SetOptions.merge()).await()

            // 2. Append to location history sub-collection
            val historyDocRef = deviceDocRef.collection(HISTORY_SUBCOLLECTION)
                .document(payload.timestamp.toString())

            historyDocRef.set(payload).await()

            Log.d(TAG, "Location successfully saved to Firebase for device: ${payload.deviceId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location to Firebase", e)
            Result.failure(e)
        }
    }
}
