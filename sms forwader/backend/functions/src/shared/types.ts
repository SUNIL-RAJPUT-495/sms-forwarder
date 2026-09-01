/**
 * Shared TypeScript types used across all Firebase Functions.
 *
 * These mirror the Kotlin DTOs on the Android side.
 * Any change here must be reflected in both Android apps.
 */

/** Device role in the forwarding pipeline. */
export type DeviceRole = "SOURCE" | "DESTINATION";

/** Device registration status. */
export type DeviceStatus = "ACTIVE" | "REVOKED";

/**
 * Device document stored in Firestore.
 * Collection: /devices/{deviceId}
 *
 * SECURITY: deviceApiKeyHash is a SHA-256 hash of the raw API key.
 * The raw key is returned only at registration time and never stored.
 */
export interface DeviceDocument {
  deviceId: string;
  role: DeviceRole;
  deviceName: string;
  status: DeviceStatus;

  /** SHA-256(deviceApiKey) — used for authentication. Raw key never stored. */
  deviceApiKeyHash: string;

  /**
   * RSA-2048 public key in PEM format (SubjectPublicKeyInfo).
   * Stored for DESTINATION devices only.
   * Used by SOURCE device to encrypt AES session keys.
   */
  publicKeyPem?: string;

  /**
   * Firebase Cloud Messaging token.
   * Stored for DESTINATION devices only.
   * Never stored for SOURCE devices.
   */
  fcmToken?: string;

  /** UUID of the device this is paired with. */
  pairedWithDeviceId?: string;

  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
}

/**
 * Pairing session document.
 * Collection: /pairingSessions/{sessionId}
 *
 * Created by DESTINATION when initiating pairing.
 * Consumed by SOURCE when completing pairing.
 * Single-use and short-lived (10 minutes).
 */
export interface PairingSessionDocument {
  sessionId: string;

  /** SHA-256(rawToken) — for constant-time comparison. */
  tokenHash: string;

  /** Device ID of the DESTINATION that created this session. */
  destinationDeviceId: string;

  /** When this session expires (10 min after creation). */
  expiresAt: FirebaseFirestore.Timestamp;

  /** Whether this token has been used. Prevents replay. */
  used: boolean;

  createdAt: FirebaseFirestore.Timestamp;
}

/**
 * Encrypted message stored temporarily in Firestore.
 * Collection: /encryptedMessages/{messageId}
 *
 * SECURITY:
 * - This document NEVER contains plaintext SMS.
 * - All fields can only be decrypted by the DESTINATION's private key.
 * - Documents are auto-deleted after [expiresAt] by a scheduled function.
 */
export interface EncryptedMessageDocument {
  messageId: string;            // UUID from SOURCE device (idempotency key)
  sourceDeviceId: string;
  destinationDeviceId: string;
  protocolVersion: number;
  timestamp: number;            // epoch ms (from SOURCE)

  /** Base64-encoded RSA-OAEP wrapped AES-256-GCM key. */
  encryptedKey: string;

  /** Base64-encoded 12-byte GCM nonce/IV. */
  nonce: string;

  /** Base64-encoded AES-256-GCM ciphertext + 128-bit auth tag. */
  ciphertext: string;

  /** When this document should be deleted (createdAt + 24h). */
  expiresAt: FirebaseFirestore.Timestamp;

  /** Whether FCM delivery was attempted. */
  fcmDelivered: boolean;

  /** Whether DESTINATION sent an ACK. */
  acknowledged: boolean;

  createdAt: FirebaseFirestore.Timestamp;
}

// ─────────────────────────────────────────────
// API Request / Response types
// ─────────────────────────────────────────────

export interface RegisterDeviceRequest {
  deviceName: string;
  role: DeviceRole;
  publicKeyPem?: string;  // Required for DESTINATION
  fcmToken?: string;      // Required for DESTINATION
}

export interface RegisterDeviceResponse {
  deviceId: string;
  deviceApiKey: string;   // Raw key — shown once, never stored
}

export interface InitiatePairingResponse {
  pairingToken: string;   // Raw 32-byte base64url token — shown once
  expiresAt: string;      // ISO timestamp
}

export interface CompletePairingRequest {
  pairingToken: string;
  sourceName: string;
}

export interface CompletePairingResponse {
  deviceId: string;
  deviceApiKey: string;
  destinationPublicKeyPem: string;
  destinationDeviceName: string;
}

export interface SendMessageRequest {
  messageId: string;
  sourceDeviceId: string;
  destinationDeviceId: string;
  protocolVersion: number;
  timestamp: number;
  encryptedKey: string;
  nonce: string;
  ciphertext: string;
}

export interface AckMessageRequest {
  messageId: string;
}
