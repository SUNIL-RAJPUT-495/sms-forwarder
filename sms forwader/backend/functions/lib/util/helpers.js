"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.COLLECTIONS = exports.db = exports.MAX_TIMESTAMP_DRIFT_MS = exports.SUPPORTED_PROTOCOL_VERSION = exports.RATE_LIMIT_MSG_PER_MINUTE = exports.MESSAGE_TTL_HOURS = exports.PAIRING_TOKEN_TTL_MINUTES = exports.MAX_PAYLOAD_BYTES = void 0;
exports.generateSecureToken = generateSecureToken;
exports.sha256 = sha256;
exports.secureCompare = secureCompare;
exports.generateDisplayCode = generateDisplayCode;
exports.authenticateDevice = authenticateDevice;
exports.validateTimestamp = validateTimestamp;
exports.validatePayloadSize = validatePayloadSize;
exports.unauthorizedResponse = unauthorizedResponse;
exports.badRequestResponse = badRequestResponse;
exports.internalErrorResponse = internalErrorResponse;
const admin = __importStar(require("firebase-admin"));
const crypto = __importStar(require("crypto"));
// ─────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────
/** Maximum payload size for encrypted messages (bytes). */
exports.MAX_PAYLOAD_BYTES = 4096;
/** Pairing token TTL in minutes. */
exports.PAIRING_TOKEN_TTL_MINUTES = 10;
/** Encrypted message TTL in hours. */
exports.MESSAGE_TTL_HOURS = 24;
/** Rate limit: max messages per device per minute. */
exports.RATE_LIMIT_MSG_PER_MINUTE = 10;
/** Protocol version supported by this backend. */
exports.SUPPORTED_PROTOCOL_VERSION = 1;
/** Maximum timestamp drift allowed (milliseconds). */
exports.MAX_TIMESTAMP_DRIFT_MS = 5 * 60 * 1000; // 5 minutes
// ─────────────────────────────────────────────
// Firestore helpers
// ─────────────────────────────────────────────
const db = () => admin.firestore();
exports.db = db;
exports.COLLECTIONS = {
    DEVICES: "devices",
    PAIRING_SESSIONS: "pairingSessions",
    ENCRYPTED_MESSAGES: "encryptedMessages",
    RATE_LIMITS: "rateLimits",
};
// ─────────────────────────────────────────────
// Cryptographic utilities
// ─────────────────────────────────────────────
/**
 * Generate a cryptographically random token.
 * @param bytes Number of random bytes (default 32)
 * @returns base64url-encoded token
 */
function generateSecureToken(bytes = 32) {
    return crypto.randomBytes(bytes).toString("base64url");
}
/**
 * Hash a value with SHA-256.
 * Used for storing API keys and pairing tokens without plaintext.
 */
function sha256(value) {
    return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}
/**
 * Constant-time string comparison to prevent timing attacks.
 * Use this for all secret/hash comparisons.
 */
function secureCompare(a, b) {
    if (a.length !== b.length)
        return false;
    return crypto.timingSafeEqual(Buffer.from(a, "utf8"), Buffer.from(b, "utf8"));
}
/**
 * Generate a 6-character human-readable pairing code from a token.
 * Used as the display value for manual entry or ADB entry.
 * Format: ABC-XYZ (3 + 3 alphanumeric)
 */
function generateDisplayCode(token) {
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
async function authenticateDevice(req) {
    const authHeader = req.headers["authorization"];
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
        return null;
    }
    const rawKey = authHeader.substring(7).trim();
    if (!rawKey || rawKey.length < 20)
        return null;
    const keyHash = sha256(rawKey);
    // Query by hashed API key
    const snap = await (0, exports.db)()
        .collection(exports.COLLECTIONS.DEVICES)
        .where("deviceApiKeyHash", "==", keyHash)
        .where("status", "==", "ACTIVE")
        .limit(1)
        .get();
    if (snap.empty)
        return null;
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
function validateTimestamp(timestampMs) {
    const now = Date.now();
    const drift = Math.abs(now - timestampMs);
    return drift <= exports.MAX_TIMESTAMP_DRIFT_MS;
}
/**
 * Validate encrypted payload field sizes.
 * Prevents oversized payloads from being stored or forwarded.
 */
function validatePayloadSize(encryptedKey, nonce, ciphertext) {
    const totalBytes = Buffer.byteLength(encryptedKey, "base64") +
        Buffer.byteLength(nonce, "base64") +
        Buffer.byteLength(ciphertext, "base64");
    return totalBytes <= exports.MAX_PAYLOAD_BYTES;
}
// ─────────────────────────────────────────────
// HTTP helpers
// ─────────────────────────────────────────────
function unauthorizedResponse(res, message = "Unauthorized") {
    res.status(401).json({ error: message });
}
function badRequestResponse(res, message) {
    res.status(400).json({ error: message });
}
function internalErrorResponse(res) {
    res.status(500).json({ error: "Internal server error" });
}
//# sourceMappingURL=helpers.js.map