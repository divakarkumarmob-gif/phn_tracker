# 📱 Phone Tracker (Kotlin Android + Firebase + Render + GitHub Actions)

Real-time Android Phone Tracker application developed in **Kotlin** with persistent **24/7 background location tracking** (even when the screen is locked/turned off), **Firebase Google Authentication**, cloud storage on **Firebase Firestore**, live telemetry synced to a custom **Render Backend** with an interactive OpenStreetMap web dashboard, and automated **APK compilation via GitHub Actions CI/CD**.

---

## 🌟 Key Features

1. **Continuous Live Tracking (Screen-Off / Sleep Mode)**:
   - Uses Android `ForegroundService` with `FOREGROUND_SERVICE_TYPE_LOCATION` (Android 14+ compliant).
   - CPU `PARTIAL_WAKE_LOCK` prevents the operating system from suspending location callbacks when the screen turns off.
   - Built-in battery optimization exemption request (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
   - Sticky ongoing notification showing live coordinates, battery percentage, and timestamp.
2. **Google Authentication & Firebase Cloud**:
   - Secure Google Sign-In with Firebase Auth.
   - Live location saved to `users/{userId}/devices/{deviceId}` in Firestore.
   - Historical breadcrumb route logged in subcollection `history/` for path playback.
3. **Render Backend & Live Map Portal**:
   - Node.js & Express REST API ready for instant deployment on Render (`render.yaml` & `Dockerfile`).
   - Built-in live browser dashboard with **Leaflet.js & OpenStreetMap** (`http://your-render-app.onrender.com`).
   - Real-time device marker updates, accuracy radius circle, battery status, and speed metrics.
4. **Automated APK Build via GitHub Actions**:
   - Compiles debug APK on every push/pull request or manual trigger (`workflow_dispatch`).
   - Download the APK directly from GitHub Actions **Artifacts** tab without needing Android Studio!

---

## 🏗 Architecture & Project Structure

```
phn_tracker/
├── app/                                    # Android Native App (Kotlin)
│   ├── src/main/
│   │   ├── AndroidManifest.xml             # Foreground service & permissions
│   │   ├── java/com/tracker/phonetracker/
│   │   │   ├── PhoneTrackerApp.kt          # Application class & NotificationChannel
│   │   │   ├── data/
│   │   │   │   ├── model/LocationPayload.kt # Data class with Firestore/Gson annotations
│   │   │   │   └── repository/
│   │   │   │       ├── FirebaseRepository.kt# Firestore write operations
│   │   │   │       └── BackendRepository.kt # OkHttp sync to Render API
│   │   │   ├── service/
│   │   │   │   └── LocationTrackingService.kt# Foreground service, WakeLock, FusedLocation
│   │   │   ├── ui/
│   │   │   │   ├── LoginActivity.kt        # Google Sign-In & Auth flow
│   │   │   │   └── MainActivity.kt         # Tracking Dashboard & metrics UI
│   │   │   └── utils/
│   │   │       ├── BatteryHelper.kt        # Battery level & Doze mode handling
│   │   │       └── PermissionHelper.kt     # Android 10-14 permission management
│   │   └── res/                            # Modern dark Material 3 layouts & icons
│   ├── google-services.json                # Firebase configuration file
│   └── build.gradle.kts                    # App dependencies
│
├── backend/                                # Render Cloud Backend (Node.js/Express)
│   ├── server.js                           # Location ingestion API & device store
│   ├── public/index.html                   # Interactive live tracking map dashboard
│   ├── package.json                        # Express, Cors, Helmet
│   ├── render.yaml                         # Render 1-click blueprint
│   └── Dockerfile                          # Alpine Node container
│
├── .github/workflows/
│   └── build-apk.yml                       # Automated GitHub Actions APK build workflow
│
├── build.gradle.kts                        # Root Gradle build script
├── settings.gradle.kts                     # Project module configuration
└── gradle/libs.versions.toml               # Modern Gradle Version Catalog
```

---

## 🚀 Setup & Deployment Guide

### 1. Firebase Console Setup (Google Sign-In & Firestore)
1. Go to [Firebase Console](https://console.firebase.google.com/) and create a new project.
2. Add an **Android App**:
   - **Package Name:** `com.tracker.phonetracker` (and `com.tracker.phonetracker.debug` for debug builds)
   - Generate your SHA-1 fingerprint by running:
     ```bash
     ./gradlew signingReport
     ```
   - Paste the SHA-1 in your Firebase Android App settings.
3. Download `google-services.json` and replace the placeholder in `app/google-services.json`.
4. Enable **Authentication**:
   - Go to **Build > Authentication > Sign-in method**.
   - Enable **Google** provider and save.
5. Enable **Firestore Database**:
   - Go to **Build > Firestore Database > Create database**.
   - Start in test mode or add rules:
     ```javascript
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         match /users/{userId}/devices/{deviceId}/{document=**} {
           allow read, write: if request.auth != null && request.auth.uid == userId;
         }
       }
     }
     ```

---

### 2. Screen-Off Location Tracking (Crucial OEM Settings)
Android battery management systems (especially MIUI, OneUI, ColorOS, OxygenOS) can aggressively kill background services when the screen is locked:
1. Inside the app, when prompted, click **"Disable Battery Optimization"** to whitelist the app from Android Doze mode.
2. In your device Settings > Apps > Phone Tracker:
   - **App Battery Usage:** Select **Unrestricted**.
   - **Permissions > Location:** Select **"Allow all the time"**.
   - **Autostart / Background Launch:** Turn **ON** (on Xiaomi/Oppo/Vivo/Realme).

---

### 3. Deploying the Backend to Render
1. Push this repository to your GitHub account:
   ```bash
   git init
   git add .
   git commit -m "Initial commit of Phone Tracker"
   git branch -M main
   git remote add origin https://github.com/your-username/phn_tracker.git
   git push -u origin main
   ```
2. Log in to [Render.com](https://render.com/).
3. Click **New + > Web Service**:
   - Select your GitHub repository.
   - **Root Directory:** `backend`
   - **Runtime:** `Node`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Plan:** Free
4. Once deployed, Render will provide a live URL like:
   `https://phn-tracker-backend.onrender.com`
5. Open that URL in your browser to view the **Live Web Map Dashboard**!
6. In `app/src/main/java/com/tracker/phonetracker/data/repository/BackendRepository.kt`, update `DEFAULT_RENDER_URL` with your Render URL.

---

### 4. Automated APK Build with GitHub Actions
You don't need Android Studio installed to compile the APK!
1. Push your repository to GitHub.
2. Go to the **Actions** tab in your GitHub repository.
3. Select **Build Android APK** workflow and click **Run workflow** (or simply push to `main`).
4. Once the build finishes (takes ~2-3 minutes):
   - Click on the completed run.
   - Scroll down to the **Artifacts** section.
   - Click **PhoneTracker-Debug-APK** to download your compiled `.apk` file directly to your computer or phone!
5. *(Optional for Private Firebase credentials)*:
   - Add your `google-services.json` contents as a GitHub Repository Secret named `GOOGLE_SERVICES_JSON` under **Settings > Secrets and variables > Actions**. The workflow will automatically inject it during build.

---

## 🛠 Local Testing

### Backend
```bash
cd backend
npm install
npm start
# Opens at http://localhost:3000
```

### Android App
```bash
# Build debug APK locally
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```
