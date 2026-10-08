package com.tracker.phonetracker.data.model

import com.google.firebase.firestore.PropertyName
import com.google.gson.annotations.SerializedName

data class LocationPayload(
    @get:PropertyName("userId")
    @set:PropertyName("userId")
    @SerializedName("userId")
    var userId: String = "",

    @get:PropertyName("userEmail")
    @set:PropertyName("userEmail")
    @SerializedName("userEmail")
    var userEmail: String = "",

    @get:PropertyName("deviceId")
    @set:PropertyName("deviceId")
    @SerializedName("deviceId")
    var deviceId: String = "",

    @get:PropertyName("deviceModel")
    @set:PropertyName("deviceModel")
    @SerializedName("deviceModel")
    var deviceModel: String = "",

    @get:PropertyName("latitude")
    @set:PropertyName("latitude")
    @SerializedName("latitude")
    var latitude: Double = 0.0,

    @get:PropertyName("longitude")
    @set:PropertyName("longitude")
    @SerializedName("longitude")
    var longitude: Double = 0.0,

    @get:PropertyName("altitude")
    @set:PropertyName("altitude")
    @SerializedName("altitude")
    var altitude: Double = 0.0,

    @get:PropertyName("accuracy")
    @set:PropertyName("accuracy")
    @SerializedName("accuracy")
    var accuracy: Float = 0f,

    @get:PropertyName("speed")
    @set:PropertyName("speed")
    @SerializedName("speed")
    var speed: Float = 0f,

    @get:PropertyName("batteryLevel")
    @set:PropertyName("batteryLevel")
    @SerializedName("batteryLevel")
    var batteryLevel: Int = 0,

    @get:PropertyName("isCharging")
    @set:PropertyName("isCharging")
    @SerializedName("isCharging")
    var isCharging: Boolean = false,

    @get:PropertyName("timestamp")
    @set:PropertyName("timestamp")
    @SerializedName("timestamp")
    var timestamp: Long = System.currentTimeMillis()
)
