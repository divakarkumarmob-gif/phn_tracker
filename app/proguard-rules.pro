# Proguard rules for PhoneTracker

# Keep model classes serialized by Gson
-keepclassmembers class com.tracker.phonetracker.data.model.** { <fields>; }
-keep class com.tracker.phonetracker.data.model.** { *; }

# Keep Firebase models
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
