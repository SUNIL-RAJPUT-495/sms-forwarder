import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import * as crypto from "crypto";

// ─────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────

/** Maximum payload size for encrypted messages (bytes). */
export const MAX_PAYLOAD_BYTES = 4096;

/** Pairing token TTL in minutes. */
export const PAIRING_TOKEN_TTL_MINUTES = 10;

/** Encrypted message TTL in hours. */
export const MESSAGE_TTL_HOURS = 24;

/** Rate limit: max messages per device per minute. */
export const RATE_LIMIT_MSG_PER_MINUTE = 10;

/** Protocol version supported by this backend. */
export const SUPPORTED_PROTOCOL_VERSION = 1;

/** Maximum timestamp drift allowed (milliseconds). */
export const MAX_TIMESTAMP_DRIFT_MS = 5 * 60 * 1000; // 5 minutes

// ─────────────────────────────────────────────
// Firestore helpers
// ─────────────────────────────────────────────

export const db = () => admin.firestore();

export const COLLECTIONS = {
  DEVICES: "devices",
  PAIRING_SESSIONS: "pairingSessions",
  ENCRYPTED_MESSAGES: "encryptedMessages",
  RATE_LIMITS: "rateLimits",
} as const;

// ─────────────────────────────────────────────
// Cryptographic utilities
// ─────────────────────────────────────────────

/**
 * Generate a cryptographically random token.
 * @param bytes Number of random bytes (default 32)
 * @returns base64url-encoded token
 */
export function generateSecureToken(bytes = 32): string {
  return crypto.randomBytes(bytes).toString("base64url");
}

/**
 * Hash a value with SHA-256.
 * Used for storing API keys and pairing tokens without plaintext.
 */
export function sha256(value: string): string {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

/**
 * Constant-time string comparison to prevent timing attacks.
 * Use this for all secret/hash comparisons.
 */
export function secureCompare(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(Buffer.from(a, "utf8"), Buffer.from(b, "utf8"));
}

/**
 * Generate a 6-character human-readable pairing code from a token.
 * Used as the display value for manual entry or ADB entry.
 * Format: ABC-XYZ (3 + 3 alphanumeric)
 */
export function generateDisplayCode(token: string): string {
  const chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  const hash = crypto.createHash("sha256").update(token).digest();
  let code = "";
  for (let i = 0; i < 6; i++) {
    code += chars[hash[i] % chars.length];
    if (i === 2) {
      code += "-";
    }
  }
  return code;
}

// ─────────────────────────────────────────────
// Authentication
// ─────────────────────────────────────────────

/**
 * Extract and validate the device API key from the Authorization header.
 * Returns the device document if valid, or null if authentication fails.
 *
 * Authorization: Bearer <deviceApiKey>
 */
export async function authenticateDevice(
  req: functions.https.Request
): Promise<{ deviceId: string; role: string; pairedWithDeviceId?: string } | null> {
  const authHeader = req.headers["authorization"];
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return null;
  }

  const rawKey = authHeader.substring(7).trim();
  if (!rawKey || rawKey.length < 20) return null;

  const keyHash = sha256(rawKey);

  // Query by hashed API key
  const snap = await db()
    .collection(COLLECTIONS.DEVICES)
    .where("deviceApiKeyHash", "==", keyHash)
    .where("status", "==", "ACTIVE")
    .limit(1)
    .get();

  if (snap.empty) return null;

  const doc = snap.docs[0];
  const data = doc.data();

  return {
    deviceId: doc.id,
    role: data.role,
    pairedWithDeviceId: data.pairedWithDeviceId,
  };
}

// ─────────────────────────────────────────────
// Validation
// ─────────────────────────────────────────────

/**
 * Validate that the message timestamp is within the allowed drift window.
 * Rejects messages that are too old or from the future (replay protection).
 */
export function validateTimestamp(timestampMs: number): boolean {
  const now = Date.now();
  const drift = Math.abs(now - timestampMs);
  return drift <= MAX_TIMESTAMP_DRIFT_MS;
}

/**
 * Validate encrypted payload field sizes.
 * Prevents oversized payloads from being stored or forwarded.
 */
export function validatePayloadSize(
  encryptedKey: string,
  nonce: string,
  ciphertext: string
): boolean {
  const totalBytes =
    Buffer.byteLength(encryptedKey, "base64") +
    Buffer.byteLength(nonce, "base64") +
    Buffer.byteLength(ciphertext, "base64");
  return totalBytes <= MAX_PAYLOAD_BYTES;
}

// ─────────────────────────────────────────────
// HTTP helpers
// ─────────────────────────────────────────────

export function unauthorizedResponse(
  res: functions.Response,
  message = "Unauthorized"
): void {
  res.status(401).json({ error: message });
}

export function badRequestResponse(
  res: functions.Response,
  message: string
): void {
  res.status(400).json({ error: message });
}

export function internalErrorResponse(res: functions.Response): void {
  res.status(500).json({ error: "Internal server error" });
}
