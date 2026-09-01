# Universal SMS Forwarder — Multi-Device Encrypted Bank SMS Relay

A universal, brand-agnostic, end-to-end encrypted (E2EE) SMS forwarding and OTP relay system that works on **ANY Android device** (Google Pixel, Samsung Galaxy, Xiaomi / Redmi, OPPO, OnePlus, Motorola, Vivo, Nothing, etc.).

---

## 🌟 Key Features

- **Universal Multi-Device Support**: Works across any Android smartphone brand without hardcoded limitations.
- **Three Flexible Operating Roles (One Single APK)**:
  - **Forwarder / Gateway (Sender)**: Monitors incoming SIM SMS, evaluates customizable bank/OTP filter rules, encrypts payloads, and relays them.
  - **Receiver / Client (Destination)**: Securely receives push notifications via FCM, decrypts locally using hardware-backed KeyStore keys, and displays heads-up alerts with **1-tap OTP copy buttons**.
  - **Dual Mode (Bidirectional)**: Cross-forwarding between two active phones (e.g. Work SIM ↔ Personal Phone).
- **Zero-Knowledge End-to-End Encryption (E2EE)**:
  - Ephemeral **AES-256-GCM** message encryption with authenticated associated data (AAD).
  - **RSA-OAEP-SHA256 (2048/4096-bit)** key wrapping backed by Android KeyStore.
  - The Firebase relay server only sees ciphertext — plaintext SMS and private keys never touch the cloud.
- **Universal Pairing Experience**:
  - Scan dynamic **QR Code** via camera.
  - Enter human-readable **6-digit pairing code** (e.g. `839-201`).
  - Headless **ADB Command Helper** for broken-screen or automated setups.
- **OEM Keep-Alive Battery Guides**:
  - Built-in guidance and shortcuts for Samsung OneUI, Xiaomi MIUI/HyperOS, OPPO/Realme ColorOS, OnePlus OxygenOS, Vivo, and Google Pixel.
- **Offline Resiliency**:
  - Outbound messages are queued in local Room DB and automatically retried using WorkManager with exponential backoff.

---

## 📁 Repository Structure

```
sms-forwarder/
├── android-app/       Universal Android app (Jetpack Compose, Hilt, Room, KeyStore, FCM)
├── backend/           Firebase Cloud Functions & Firestore Relay (TypeScript)
├── docs/              Setup guides, security architecture, and troubleshooting
├── oppo-app/          (Legacy brand-specific sender build)
└── samsung-app/       (Legacy brand-specific receiver build)
```

---

## 🚀 Quick Start

1. **Deploy Backend**:
   ```bash
   cd backend/functions
   npm install
   npm test
   cd ..
   firebase deploy --only functions,firestore:rules
   ```

2. **Build & Install Android App**:
   - Open `android-app/` in Android Studio.
   - Set `BACKEND_BASE_URL` in `app/build.gradle.kts` to your Firebase Functions URL.
   - Build and install the APK on both devices:
     ```bash
     ./gradlew assembleDebug
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```

3. **Pair Devices**:
   - On **Phone B (Receiver)**: Select *Receiver Mode* → Tap **"Start Pairing"** to show the QR code & 6-digit code.
   - On **Phone A (Sender)**: Select *Forwarder Mode* → Scan the QR code or enter the 6-digit code.
   - Done! Any incoming bank SMS or OTP matching your filter rules will instantly appear on Phone B.

---

## 📖 Documentation

- [Setup Guide](docs/setup.md)
- [Architecture & Security](docs/architecture.md)

---

## 🔒 Security Notice

This application is built for personal use on devices you own. It encrypts and relays SMS only between devices that you have explicitly paired using cryptographic verification.
