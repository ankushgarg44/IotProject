# 🔥 Firebase Setup Guide — Ebike Tracker

Follow these steps **exactly** to get your Firebase backend running.

---

## Step 1: Create Firebase Project

1. Go to **[Firebase Console](https://console.firebase.google.com/)**
2. Click **"Add project"**
3. Project name: `ebike-tracker` (or any name you want)
4. Turn OFF Google Analytics (not needed) → Click **"Create project"**
5. Wait for it to finish → Click **"Continue"**

---

## Step 2: Add Android App to Firebase

1. On the Firebase project overview page, click the **Android icon** (🤖)
2. Fill in:
   - **Android package name**: `com.example.ebike`
   - **App nickname**: `Ebike App`
   - **Debug signing certificate SHA-1**: (skip for now, can add later)
3. Click **"Register app"**
4. **Download `google-services.json`** ← ⚠️ VERY IMPORTANT
5. Place the downloaded file at:
   ```
   EbikeFinale/app/google-services.json
   ```
   (It must be inside the `app/` folder, NOT the root!)
6. Click **"Next"** through the remaining steps → Click **"Continue to console"**

---

## Step 3: Enable Realtime Database

1. In the left sidebar, click **"Build"** → **"Realtime Database"**
2. Click **"Create Database"**
3. Select location: **United States (us-central1)** or closest to you
4. Select **"Start in test mode"** → Click **"Enable"**
5. **Copy your database URL** — it looks like:
   ```
   https://ebike-tracker-xxxxx-default-rtdb.firebaseio.com/
   ```

---

## Step 4: Set Database Rules

1. In Realtime Database, click the **"Rules"** tab
2. Replace the rules with:
   ```json
   {
     "rules": {
       "location": {
         ".read": true,
         ".write": true
       }
     }
   }
   ```
3. Click **"Publish"**

> ⚠️ **Note**: These rules allow anyone to read/write. For production,
> add authentication. For development and testing, this is fine.

---

## Step 5: Add Test Data (Verify it works)

1. In Realtime Database, click the **"Data"** tab
2. Click the **"+"** button next to your database URL
3. Add this structure:
   ```
   location/
   ├── latitude: 28.6139
   ├── longitude: 77.2090
   └── speed: 15.5
   ```
   
   **How to add:**
   - Click `+` → Name: `location` → Click `+` on location
   - Name: `latitude` → Value: `28.6139` → Click ✓
   - Name: `longitude` → Value: `77.2090` → Click ✓
   - Name: `speed` → Value: `15.5` → Click ✓

4. Your database should now show:
   ```
   ebike-tracker-xxxxx-default-rtdb
   └── location
       ├── latitude: 28.6139
       ├── longitude: 77.209
       └── speed: 15.5
   ```

---

## Step 6: Get Service Account Key (for Python Backend)

1. Click the **⚙️ gear icon** → **"Project settings"**
2. Go to **"Service accounts"** tab
3. Make sure **Firebase Admin SDK** is selected
4. Click **"Generate new private key"**
5. Click **"Generate key"** → A JSON file will download
6. **Rename** the downloaded file to `serviceAccountKey.json`
7. Place it at:
   ```
   EbikeFinale/backend/serviceAccountKey.json
   ```

---

## Step 7: Configure Python Backend

1. Open `backend/push_location.py`
2. Update the `FIREBASE_DB_URL` on line 31:
   ```python
   FIREBASE_DB_URL = "https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com/"
   ```
   Replace with YOUR actual database URL from Step 3.

3. Install dependencies & run:
   ```bash
   cd backend
   pip install -r requirements.txt
   python push_location.py
   ```

4. You should see:
   ```
   ✅ Firebase initialized successfully!
   📡 Pushing location updates every 1s...
     [1] 📍 Lat: 28.613900, Lng: 77.209000, Speed: 12.3 km/h
     [2] 📍 Lat: 28.614012, Lng: 77.209145, Speed: 14.7 km/h
   ```

---

## Step 8: Build & Run the Android App

1. Make sure `google-services.json` is in `app/` folder
2. Open the project in Android Studio
3. Click **"Sync Now"** when prompted (or File → Sync Project with Gradle Files)
4. Run the app on your phone/emulator
5. The map should show OpenStreetMap tiles (no Google API key needed!)
6. Location data should update in real-time from Firebase

---

## 📁 Final Project Structure

```
EbikeFinale/
├── app/
│   ├── google-services.json          ← ⚠️ YOU ADD THIS (from Step 2)
│   ├── build.gradle.kts              ← ✅ Updated (Firebase + osmdroid)
│   └── src/main/
│       ├── AndroidManifest.xml       ← ✅ Updated (removed Google Maps key)
│       └── java/com/example/ebike/
│           ├── HomeScreen.kt         ← ✅ Updated (osmdroid maps)
│           ├── LocationData.kt       ← ✅ Updated
│           ├── MainActivity.kt       ← ✅ Updated (osmdroid init)
│           ├── SplashActivity.kt     ← Unchanged
│           └── ViewModel.kt          ← ✅ Updated (Firebase listener)
├── backend/
│   ├── push_location.py              ← ✅ New (Python Firebase pusher)
│   ├── esp32_direct_push.ino         ← ✅ New (ESP32 alternative)
│   ├── requirements.txt              ← ✅ New
│   └── serviceAccountKey.json        ← ⚠️ YOU ADD THIS (from Step 6)
├── build.gradle.kts                  ← ✅ Updated (Google Services plugin)
├── gradle/libs.versions.toml         ← ✅ Updated (new dependencies)
└── settings.gradle.kts               ← Unchanged
```

---

## 🧪 Quick Test Checklist

- [ ] Created Firebase project
- [ ] Added Android app with package `com.example.ebike`
- [ ] Downloaded & placed `google-services.json` in `app/`
- [ ] Created Realtime Database in test mode
- [ ] Added test location data (latitude, longitude, speed)
- [ ] Downloaded service account key for backend
- [ ] Python backend runs and pushes data
- [ ] Android app shows map with location marker
- [ ] Location updates in real-time

---

## 🆘 Troubleshooting

**"Could not resolve firebase-bom"**
→ Make sure `google-services.json` is in `app/` folder and Gradle sync is done.

**Map shows but no location marker**
→ Check Firebase Console → Realtime Database → Data tab has location node.

**"Firebase Database connection failed"**
→ Make sure your phone/emulator has internet. Check database URL matches.

**Map tiles not loading**
→ osmdroid needs internet. Check `INTERNET` permission in AndroidManifest.xml.
→ Also check that `usesCleartextTraffic="true"` is set in manifest.
