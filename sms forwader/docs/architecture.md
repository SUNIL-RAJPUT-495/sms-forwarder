# Universal SMS Forwarder — Architecture & Cryptography

## Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│               ANY SENDER DEVICE                        │
│   (e.g., Google Pixel, Xiaomi, OPPO, Motorola)        │
│                                                        │
│  [ Incoming SIM SMS: Bank OTP / Alert ]                │
│                     │                                  │
│                     ▼                                  │
│  [ SmsFilterEngine: Regex & Keyword Matcher ]          │
│                     │                                  │
│                     ▼                                  │
│  [ UniversalCryptoEngine (AES-256-GCM) ]               │
│  - Ephemeral 256-bit AES session key                   │
│  - 12-byte IV / Nonce                                  │
│  - AAD: "v1|msgId|srcId|destId|timestamp"              │
│  - RSA-OAEP-SHA256 Encrypted AES Key (Receiver PubKey) │
└─────────────────────┬──────────────────────────────────┘
                      │ HTTPS POST /sendMessage (Ciphertext only)
                      ▼
┌────────────────────────────────────────────────────────┐
│            FIREBASE CLOUD RELAY                        │
│   (Cloud Functions + Firestore + Cloud Messaging)      │
│                                                        │
│  - Zero-Knowledge: Only stores encrypted ciphertext    │
│  - Replay & Clock Drift Validation (±5 min)            │
│  - High Priority FCM Push Delivery                     │
└─────────────────────┬──────────────────────────────────┘
                      │ FCM High Priority Push
                      ▼
┌────────────────────────────────────────────────────────┐
│              ANY RECEIVER DEVICE                       │
│    (e.g., Samsung Galaxy, OnePlus, Pixel, Tablet)      │
│                                                        │
│  [ UniversalFcmService: Inbound Push Receiver ]        │
│                     │                                  │
│                     ▼                                  │
│  [ UniversalCryptoEngine (Hardware AndroidKeyStore) ]  │
│  - RSA Private Key unwraps AES Session Key             │
│  - AES-256-GCM verifies AAD & Decrypts Plaintext       │
│                     │                                  │
│                     ▼                                  │
│  [ Heads-Up Alert with 1-Tap "Copy OTP" Action ]       │
│  [ Local Room Database History (Searchable) ]          │
└────────────────────────────────────────────────────────┘
```

---

## Cryptographic Guarantees

1. **Confidentiality**: All SMS sender addresses and bodies are encrypted before leaving the sender device with AES-256-GCM.
2. **Integrity & Authenticity**: GCM 128-bit authentication tags and Associated Authenticated Data (AAD) ensure ciphertext and metadata (timestamp, device IDs, protocol version) cannot be altered in transit.
3. **Hardware Key Protection**: Receiver private keys are generated inside Android KeyStore with StrongBox / TEE hardware isolation.
4. **Replay Protection**: Messages include millisecond-accurate timestamps verified against a strict ±5-minute window and unique idempotency keys (`messageId`).
