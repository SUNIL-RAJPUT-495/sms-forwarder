# Universal SMS Forwarder — Setup Guide

This guide covers setting up the **Universal SMS Forwarder** on any combination of Android devices (e.g. Google Pixel, Samsung Galaxy, Xiaomi, OnePlus, OPPO, Motorola, Vivo, etc.).

---

## Prerequisites

- **2 Android Devices** running Android 8.0 (API 26) or newer.
- **Node.js 20+** & **Firebase CLI** (`npm install -g firebase-tools`).
- **Android Studio** (Hedgehog or newer) with Android SDK 35.
- **ADB** installed on your computer (optional, for broken screen or headless devices).

---

## Step 1 — Firebase Cloud Setup

1. Open [Firebase Console](https://console.firebase.google.com/) and create a new project: `universal-sms-relay`.
2. Enable **Cloud Firestore** in production mode.
3. Enable **Firebase Cloud Messaging (FCM)**.
4. Register your Android app:
   - Package name: `com.smsforwarder.app` (or your chosen package name)
5. Download `google-services.json` and place it in:
   ```
   android-app/app/google-services.json
   ```

---

## Step 2 — Deploy Backend Functions

```bash
cd backend/functions
npm install
npm test
cd ..
firebase deploy --only functions,firestore:rules
```

After deployment, copy your Cloud Functions base URL (e.g., `https://us-central1-YOUR_PROJECT.cloudfunctions.net/`).

Update `BACKEND_BASE_URL` in `android-app/app/build.gradle.kts`.

---

## Step 3 — Build and Install the App

Connect each Android phone via USB and run:

```bash
cd android-app
./gradlew assembleDebug

# Install on Device 1
adb -s <DEVICE_1_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk

# Install on Device 2
adb -s <DEVICE_2_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 4 — Initial Device Configuration & Pairing

### 1. On the Receiver Device (e.g. Samsung / Primary Phone):
1. Open **SMS Forwarder**.
2. Select **"Receiver / Destination"** mode.
3. Grant **Notification** permission.
4. Tap **"Register Device with Relay"**.
5. Tap **"Start Pairing"** → displays a 6-digit Code (e.g., `839-201`) and a QR Code.

### 2. On the Sender Device (e.g. Pixel / OPPO / Old phone with SIM):
1. Open **SMS Forwarder**.
2. Select **"Forwarder / Sender (Gateway)"** mode.
3. Grant **SMS** and **Notification** permissions.
4. Tap **"Register Device with Relay"**.
5. Tap **"Start Pairing"**:
   - Option A: Scan the QR code displayed on the Receiver screen using the in-app camera.
   - Option B: Type the 6-digit code shown on the Receiver screen.
   - Option C (Broken Screen / ADB):
     ```bash
     adb shell am start -n com.smsforwarder.app/.MainActivity --es pairing_token "839-201"
     ```
6. The Receiver phone will instantly receive a notification: **"Pairing Complete 🎉"**.

---

## Step 5 — OEM Battery Optimization Settings (Crucial)

To ensure the Sender phone forwards SMS reliably when its screen is turned off:

1. In the app, open **Settings → Battery Fix (OEM Keep-Alive Guide)**.
2. Tap **"Open Android Battery Whitelist Dialog"** and select **"Allow / Don't Optimize"**.
3. Follow the manufacturer-specific guide inside the app:
   - **Samsung (OneUI)**: Battery → Background usage limits → Never sleeping apps → Add SMS Forwarder.
   - **Xiaomi / Redmi / POCO**: Apps → SMS Forwarder → Autostart: ON → Battery saver: "No restrictions" → Lock app in Recents.
   - **OPPO / Realme (ColorOS)**: App management → Battery usage → Allow background activity & Auto-launch.
   - **OnePlus (OxygenOS)**: Battery → App battery management → Allow background activity.
   - **Google Pixel / Motorola**: App Info → Battery → Unrestricted.

---

## Step 6 — Testing End-to-End Delivery

1. On the **Sender Device**, tap **"Trigger Test Bank OTP"** on the dashboard.
2. The Sender encrypts the payload using AES-256-GCM + Receiver's RSA-4096 Public Key.
3. The Receiver phone pops up a **Heads-Up Notification** showing:
   ```
   🔑 OTP: 591823 (HDFCBK)
   [ Copy 591823 ]
   ```
4. Tap the **"Copy 591823"** button on the notification to copy the OTP directly to your clipboard.
5. Open the **History** tab to search and view past forwarded messages.
