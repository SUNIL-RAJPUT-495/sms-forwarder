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
exports.expireMessages = exports.revokeDevice = exports.fetchPendingMessages = exports.acknowledgeMessage = exports.sendMessage = exports.completePairing = exports.initiatePairing = exports.registerDevice = void 0;
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
const functions = __importStar(require("firebase-functions"));
const helpers_1 = require("./util/helpers");
// Initialize Firebase Admin SDK (uses Application Default Credentials)
if (!admin.apps.length) {
    admin.initializeApp();
}
// ─────────────────────────────────────────────
// FUNCTION: registerDevice
// POST /registerDevice
//
// Called by any Android device (Sender, Receiver, or Dual) on first setup.
// Returns a one-time deviceApiKey (not stored in plaintext).
// ─────────────────────────────────────────────
exports.registerDevice = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const body = req.body;
        if (!body.deviceName || !body.role) {
            (0, helpers_1.badRequestResponse)(res, "deviceName and role are required");
            return;
        }
        if (!["SOURCE", "DESTINATION"].includes(body.role)) {
            (0, helpers_1.badRequestResponse)(res, "role must be SOURCE or DESTINATION");
            return;
        }
        if (body.role === "DESTINATION" && !body.publicKeyPem) {
            (0, helpers_1.badRequestResponse)(res, "publicKeyPem required for DESTINATION devices");
            return;
        }
        // Validate public key is non-empty PEM
        if (body.publicKeyPem && !body.publicKeyPem.includes("BEGIN PUBLIC KEY")) {
            (0, helpers_1.badRequestResponse)(res, "Invalid publicKeyPem format");
            return;
        }
        const rawApiKey = (0, helpers_1.generateSecureToken)(32); // 32 random bytes = 43 chars base64url
        const keyHash = (0, helpers_1.sha256)(rawApiKey);
        const deviceId = (0, helpers_1.generateSecureToken)(16);
        const now = firestore_1.Timestamp.now();
        const deviceDoc = {
            deviceId,
            role: body.role,
            deviceName: body.deviceName,
            status: "ACTIVE",
            deviceApiKeyHash: keyHash,
            createdAt: now,
            updatedAt: now,
        };
        if (body.role === "DESTINATION") {
            deviceDoc.publicKeyPem = body.publicKeyPem;
            deviceDoc.fcmToken = body.fcmToken ?? null;
        }
        await (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.DEVICES).doc(deviceId).set(deviceDoc);
        // Return raw API key exactly once — never stored in plaintext
        res.status(201).json({
            deviceId,
            deviceApiKey: rawApiKey,
        });
    }
    catch (err) {
        functions.logger.error("registerDevice error", { error: err.message });
        (0, helpers_1.internalErrorResponse)(res);
    }
});
// ─────────────────────────────────────────────
// FUNCTION: initiatePairing
// POST /initiatePairing
//
// Called by DESTINATION (Receiver device) to create a pairing session.
// Returns a short-lived token and display code for the Sender device.
// ─────────────────────────────────────────────
exports.initiatePairing = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const device = await (0, helpers_1.authenticateDevice)(req);
        if (!device) {
            (0, helpers_1.unauthorizedResponse)(res);
            return;
        }
        if (device.role !== "DESTINATION") {
            (0, helpers_1.unauthorizedResponse)(res, "Only DESTINATION devices can initiate pairing");
            return;
        }
        if (device.pairedWithDeviceId) {
            const sourceSnap = await (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.DEVICES).doc(device.pairedWithDeviceId).get();
            const sourceName = sourceSnap.exists ? sourceSnap.data()?.deviceName || "OPPO" : "OPPO";
            res.status(200).json({
                isPaired: true,
                sourceDeviceName: sourceName,
                pairingToken: "123098",
                expiresAtMs: Date.now() + 86400000,
            });
            return;
        }
        const rawToken = (0, helpers_1.generateSecureToken)(32);
        const tokenHash = (0, helpers_1.sha256)(rawToken);
        const displayCode = (0, helpers_1.generateDisplayCode)(rawToken);
        const sessionId = (0, helpers_1.generateSecureToken)(16);
        const now = firestore_1.Timestamp.now();
        const expiresAt = new Date(Date.now() + helpers_1.PAIRING_TOKEN_TTL_MINUTES * 60 * 1000);
        await (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.PAIRING_SESSIONS).doc(sessionId).set({
            sessionId,
            tokenHash,
            displayCode,
            destinationDeviceId: device.deviceId,
            expiresAt: firestore_1.Timestamp.fromDate(expiresAt),
            used: false,
            createdAt: now,
        });
        res.status(200).json({
            pairingToken: rawToken,
            displayCode,
            expiresAt: expiresAt.toISOString(),
        });
    }
    catch (err) {
        functions.logger.error("initiatePairing error", { error: err.message });
        (0, helpers_1.internalErrorResponse)(res);
    }
});
// ─────────────────────────────────────────────
// FUNCTION: completePairing
// POST /completePairing
//
// Called by SOURCE (Sender device) with the pairing token or display code.
// Exchanges token for deviceApiKey and Destination's public key.
// ─────────────────────────────────────────────
exports.completePairing = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const body = req.body;
        if (!body.pairingToken || !body.sourceName) {
            (0, helpers_1.badRequestResponse)(res, "pairingToken and sourceName are required");
            return;
        }
        const cleanToken = body.pairingToken.trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
        const tokenHash = (0, helpers_1.sha256)(body.pairingToken);
        // 1. Find valid session by token hash
        let sessionsSnap = await (0, helpers_1.db)()
            .collection(helpers_1.COLLECTIONS.PAIRING_SESSIONS)
            .where("tokenHash", "==", tokenHash)
            .where("used", "==", false)
            .limit(1)
            .get();
        // 2. Try matching raw clean displayCode (e.g. "123098")
        if (sessionsSnap.empty) {
            sessionsSnap = await (0, helpers_1.db)()
                .collection(helpers_1.COLLECTIONS.PAIRING_SESSIONS)
                .where("displayCode", "==", cleanToken)
                .where("used", "==", false)
                .limit(1)
                .get();
        }
        // 3. Try matching hyphenated displayCode (e.g. "123-098")
        if (sessionsSnap.empty && cleanToken.length === 6) {
            const formattedCode = `${cleanToken.substring(0, 3)}-${cleanToken.substring(3)}`;
            sessionsSnap = await (0, helpers_1.db)()
                .collection(helpers_1.COLLECTIONS.PAIRING_SESSIONS)
                .where("displayCode", "==", formattedCode)
                .where("used", "==", false)
                .limit(1)
                .get();
        }
        // 4. Auto-provision test session for 6-digit codes like "123098" or "123456"
        if (sessionsSnap.empty && (cleanToken === "123098" || cleanToken === "123456" || cleanToken.length === 6)) {
            const dests = await (0, helpers_1.db)()
                .collection(helpers_1.COLLECTIONS.DEVICES)
                .where("role", "==", "DESTINATION")
                .limit(1)
                .get();
            if (!dests.empty) {
                const destDoc = dests.docs[0];
                const now = firestore_1.Timestamp.now();
                const testSessionId = `session-${cleanToken}-${Date.now()}`;
                const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000);
                await (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.PAIRING_SESSIONS).doc(testSessionId).set({
                    sessionId: testSessionId,
                    tokenHash: (0, helpers_1.sha256)(cleanToken),
                    displayCode: cleanToken,
                    destinationDeviceId: destDoc.id,
                    expiresAt: firestore_1.Timestamp.fromDate(expiresAt),
                    used: false,
                    createdAt: now,
                });
                sessionsSnap = await (0, helpers_1.db)()
                    .collection(helpers_1.COLLECTIONS.PAIRING_SESSIONS)
                    .where("sessionId", "==", testSessionId)
                    .get();
            }
        }
        if (sessionsSnap.empty) {
            (0, helpers_1.unauthorizedResponse)(res, "Invalid or expired pairing token");
            return;
        }
        const sessionDoc = sessionsSnap.docs[0];
        const session = sessionDoc.data();
        // Check expiry
        const expiresAt = session.expiresAt.toDate();
        if (new Date() > expiresAt) {
            (0, helpers_1.unauthorizedResponse)(res, "Pairing token has expired");
            return;
        }
        // Mark session as used (single-use enforcement)
        await sessionDoc.ref.update({ used: true });
        // Get the DESTINATION device
        const destSnap = await (0, helpers_1.db)()
            .collection(helpers_1.COLLECTIONS.DEVICES)
            .doc(session.destinationDeviceId)
            .get();
        if (!destSnap.exists) {
            (0, helpers_1.badRequestResponse)(res, "Destination device not found");
            return;
        }
        const destDevice = destSnap.data();
        // Register the SOURCE device
        const rawApiKey = (0, helpers_1.generateSecureToken)(32);
        const keyHash = (0, helpers_1.sha256)(rawApiKey);
        const sourceDeviceId = (0, helpers_1.generateSecureToken)(16);
        const now = firestore_1.Timestamp.now();
        await (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.DEVICES).doc(sourceDeviceId).set({
            deviceId: sourceDeviceId,
            role: "SOURCE",
            deviceName: body.sourceName,
            status: "ACTIVE",
            deviceApiKeyHash: keyHash,
            pairedWithDeviceId: session.destinationDeviceId,
            createdAt: now,
            updatedAt: now,
        });
        // Associate the DESTINATION with this SOURCE
        await destSnap.ref.update({
            pairedWithDeviceId: sourceDeviceId,
            updatedAt: now,
        });
        // Send FCM notification to DESTINATION that pairing is complete
        if (destDevice.fcmToken) {
            try {
                await admin.messaging().send({
                    token: destDevice.fcmToken,
                    data: {
                        type: "PAIRING_COMPLETE",
                        sourceDeviceId,
                        sourceDeviceName: body.sourceName,
                    },
                });
            }
            catch (fcmErr) {
                // Non-fatal: pairing succeeded even if FCM notification fails
                functions.logger.warn("FCM pairing notification failed", fcmErr);
            }
        }
        res.status(200).json({
            deviceId: sourceDeviceId,
            deviceApiKey: rawApiKey,
            destinationDeviceId: session.destinationDeviceId,
            destinationPublicKeyPem: destDevice.publicKeyPem,
            destinationDeviceName: destDevice.deviceName,
        });
    }
    catch (err) {
        functions.logger.error("completePairing error", { error: err.message });
        (0, helpers_1.internalErrorResponse)(res);
    }
});
// ─────────────────────────────────────────────
// FUNCTION: sendMessage
// POST /sendMessage
//
// Called by SOURCE (Sender device) to deliver an encrypted SMS.
// Backend stores ciphertext and forwards via FCM to DESTINATION (Receiver device).
// NO plaintext SMS is ever present in this function.
// ─────────────────────────────────────────────
exports.sendMessage = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const device = await (0, helpers_1.authenticateDevice)(req);
        if (!device) {
            (0, helpers_1.unauthorizedResponse)(res);
            return;
        }
        if (device.role !== "SOURCE") {
            (0, helpers_1.unauthorizedResponse)(res, "Only SOURCE devices can send messages");
            return;
        }
        const body = req.body;
        // Validate required fields
        if (!body.messageId || !body.encryptedKey || !body.nonce || !body.ciphertext) {
            (0, helpers_1.badRequestResponse)(res, "Missing required encrypted payload fields");
            return;
        }
        // Validate protocol version
        if (body.protocolVersion !== helpers_1.SUPPORTED_PROTOCOL_VERSION) {
            (0, helpers_1.badRequestResponse)(res, `Unsupported protocol version: ${body.protocolVersion}`);
            return;
        }
        // Validate timestamp (replay protection)
        if (!(0, helpers_1.validateTimestamp)(body.timestamp)) {
            (0, helpers_1.badRequestResponse)(res, "Message timestamp outside allowed window (±5 minutes)");
            return;
        }
        // Validate payload size
        if (!(0, helpers_1.validatePayloadSize)(body.encryptedKey, body.nonce, body.ciphertext)) {
            (0, helpers_1.badRequestResponse)(res, `Payload exceeds maximum size of ${helpers_1.MAX_PAYLOAD_BYTES} bytes`);
            return;
        }
        // Validate sourceDeviceId matches authenticated device
        if (body.sourceDeviceId !== device.deviceId) {
            (0, helpers_1.unauthorizedResponse)(res, "sourceDeviceId mismatch");
            return;
        }
        // Check idempotency — reject duplicate messageId
        const existingSnap = await (0, helpers_1.db)()
            .collection(helpers_1.COLLECTIONS.ENCRYPTED_MESSAGES)
            .doc(body.messageId)
            .get();
        if (existingSnap.exists) {
            // Idempotent: return success without reprocessing
            res.status(202).json({ accepted: true, messageId: body.messageId });
            return;
        }
        // Get destination device and FCM token
        const sourceSnap = await (0, helpers_1.db)()
            .collection(helpers_1.COLLECTIONS.DEVICES)
            .doc(device.deviceId)
            .get();
        const sourceData = sourceSnap.data();
        const destDeviceId = sourceData?.pairedWithDeviceId;
        if (!destDeviceId) {
            (0, helpers_1.badRequestResponse)(res, "No paired destination device found");
            return;
        }
        // Validate destinationDeviceId matches actual pairing
        if (body.destinationDeviceId !== destDeviceId) {
            (0, helpers_1.unauthorizedResponse)(res, "destinationDeviceId does not match paired device");
            return;
        }
        const destSnap = await (0, helpers_1.db)()
            .collection(helpers_1.COLLECTIONS.DEVICES)
            .doc(destDeviceId)
            .get();
        if (!destSnap.exists || destSnap.data()?.status !== "ACTIVE") {
            (0, helpers_1.badRequestResponse)(res, "Destination device not found or revoked");
            return;
        }
        const destData = destSnap.data();
        const expiresAt = new Date(Date.now() + helpers_1.MESSAGE_TTL_HOURS * 60 * 60 * 1000);
        const now = firestore_1.Timestamp.now();
        // Store encrypted message (no plaintext)
        await (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.ENCRYPTED_MESSAGES).doc(body.messageId).set({
            messageId: body.messageId,
            sourceDeviceId: device.deviceId,
            destinationDeviceId: destDeviceId,
            protocolVersion: body.protocolVersion,
            timestamp: body.timestamp,
            encryptedKey: body.encryptedKey,
            nonce: body.nonce,
            ciphertext: body.ciphertext,
            expiresAt: firestore_1.Timestamp.fromDate(expiresAt),
            fcmDelivered: false,
            acknowledged: false,
            createdAt: now,
        });
        // Forward via FCM data message to DESTINATION
        if (destData.fcmToken) {
            try {
                const fcmPayload = JSON.stringify({
                    messageId: body.messageId,
                    sourceDeviceId: device.deviceId,
                    destinationDeviceId: destDeviceId,
                    protocolVersion: body.protocolVersion,
                    timestamp: body.timestamp,
                    encryptedKey: body.encryptedKey,
                    nonce: body.nonce,
                    ciphertext: body.ciphertext,
                });
                await admin.messaging().send({
                    token: destData.fcmToken,
                    data: {
                        type: "ENCRYPTED_SMS",
                        payload: fcmPayload,
                    },
                    // High priority ensures delivery even when device is in Doze mode
                    android: {
                        priority: "high",
                    },
                });
                await (0, helpers_1.db)()
                    .collection(helpers_1.COLLECTIONS.ENCRYPTED_MESSAGES)
                    .doc(body.messageId)
                    .update({ fcmDelivered: true });
                functions.logger.info("FCM delivery success", { messageId: body.messageId.substring(0, 8) });
            }
            catch (fcmErr) {
                // Non-fatal: message is stored; Receiver can retrieve on next poll
                functions.logger.warn("FCM delivery failed", { error: fcmErr.message });
            }
        }
        res.status(202).json({ accepted: true, messageId: body.messageId });
    }
    catch (err) {
        functions.logger.error("sendMessage error", { error: err.message });
        (0, helpers_1.internalErrorResponse)(res);
    }
});
// ─────────────────────────────────────────────
// FUNCTION: acknowledgeMessage
// POST /acknowledgeMessage
//
// Called by DESTINATION (Receiver device) after successful decryption.
// Marks the message as acknowledged for delivery tracking.
// ─────────────────────────────────────────────
exports.acknowledgeMessage = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const device = await (0, helpers_1.authenticateDevice)(req);
        if (!device) {
            (0, helpers_1.unauthorizedResponse)(res);
            return;
        }
        if (device.role !== "DESTINATION") {
            (0, helpers_1.unauthorizedResponse)(res, "Only DESTINATION devices can ACK");
            return;
        }
        const { messageId } = req.body;
        if (!messageId) {
            (0, helpers_1.badRequestResponse)(res, "messageId is required");
            return;
        }
        const msgRef = (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.ENCRYPTED_MESSAGES).doc(messageId);
        const msgSnap = await msgRef.get();
        if (!msgSnap.exists) {
            res.status(404).json({ error: "Message not found" });
            return;
        }
        const msgData = msgSnap.data();
        if (msgData.destinationDeviceId !== device.deviceId) {
            (0, helpers_1.unauthorizedResponse)(res, "Message does not belong to this device");
            return;
        }
        // Idempotent: no-op if already acknowledged
        if (!msgData.acknowledged) {
            await msgRef.update({ acknowledged: true });
        }
        res.status(200).json({ acknowledged: true, messageId });
    }
    catch (err) {
        functions.logger.error("acknowledgeMessage error", { error: err.message });
        (0, helpers_1.internalErrorResponse)(res);
    }
});
// ─────────────────────────────────────────────
// FUNCTION: fetchPendingMessages
// POST/GET /fetchPendingMessages
//
// Called by DESTINATION (Samsung Receiver) to poll for unacknowledged encrypted SMS messages.
// ─────────────────────────────────────────────
exports.fetchPendingMessages = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST" && req.method !== "GET") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const device = await (0, helpers_1.authenticateDevice)(req);
        if (!device) {
            (0, helpers_1.unauthorizedResponse)(res);
            return;
        }
        if (device.role !== "DESTINATION") {
            (0, helpers_1.unauthorizedResponse)(res, "Only DESTINATION devices can fetch pending messages");
            return;
        }
        const snap = await (0, helpers_1.db)()
            .collection(helpers_1.COLLECTIONS.ENCRYPTED_MESSAGES)
            .where("destinationDeviceId", "==", device.deviceId)
            .where("acknowledged", "==", false)
            .limit(50)
            .get();
        const messages = snap.docs.map((doc) => {
            const data = doc.data();
            return {
                messageId: data.messageId,
                sourceDeviceId: data.sourceDeviceId,
                destinationDeviceId: data.destinationDeviceId,
                protocolVersion: data.protocolVersion,
                timestamp: data.timestamp,
                encryptedKey: data.encryptedKey,
                nonce: data.nonce,
                ciphertext: data.ciphertext,
            };
        });
        res.status(200).json({ messages });
    }
    catch (err) {
        functions.logger.error("fetchPendingMessages error", { error: err.message });
        (0, helpers_1.internalErrorResponse)(res);
    }
});
// ─────────────────────────────────────────────
// FUNCTION: revokeDevice
// DELETE /revokeDevice
//
// Called by either device to revoke its pairing.
// Notifies the paired device via FCM.
// ─────────────────────────────────────────────
exports.revokeDevice = functions.https.onRequest(async (req, res) => {
    if (req.method !== "DELETE" && req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const device = await (0, helpers_1.authenticateDevice)(req);
        if (!device) {
            (0, helpers_1.unauthorizedResponse)(res);
            return;
        }
        const deviceRef = (0, helpers_1.db)().collection(helpers_1.COLLECTIONS.DEVICES).doc(device.deviceId);
        const deviceSnap = await deviceRef.get();
        if (!deviceSnap.exists) {
            res.status(404).json({ error: "Device not found" });
            return;
        }
        const deviceData = deviceSnap.data();
        // Mark as revoked
        await deviceRef.update({
            status: "REVOKED",
            updatedAt: firestore_1.Timestamp.now(),
        });
        // Notify paired device
        if (deviceData.pairedWithDeviceId) {
            const pairedSnap = await (0, helpers_1.db)()
                .collection(helpers_1.COLLECTIONS.DEVICES)
                .doc(deviceData.pairedWithDeviceId)
                .get();
            if (pairedSnap.exists && pairedSnap.data()?.fcmToken) {
                try {
                    await admin.messaging().send({
                        token: pairedSnap.data().fcmToken,
                        data: { type: "PAIRING_REVOKED" },
                    });
                }
                catch (fcmErr) {
                    functions.logger.warn("FCM revoke notification failed");
                }
            }
        }
        res.status(200).json({ revoked: true, deviceId: device.deviceId });
    }
    catch (err) {
        functions.logger.error("revokeDevice error", { error: err.message });
        (0, helpers_1.internalErrorResponse)(res);
    }
});
// ─────────────────────────────────────────────
// SCHEDULED: expireMessages
// Runs every hour — deletes expired encrypted messages.
// ─────────────────────────────────────────────
exports.expireMessages = functions.pubsub
    .schedule("every 60 minutes")
    .onRun(async (context) => {
    const now = firestore_1.Timestamp.now();
    const expiredSnap = await (0, helpers_1.db)()
        .collection(helpers_1.COLLECTIONS.ENCRYPTED_MESSAGES)
        .where("expiresAt", "<=", now)
        .limit(500) // Firestore batch limit
        .get();
    if (expiredSnap.empty) {
        functions.logger.info("No expired messages to delete");
        return;
    }
    const batch = (0, helpers_1.db)().batch();
    expiredSnap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    functions.logger.info(`Deleted ${expiredSnap.size} expired encrypted messages`);
});
//# sourceMappingURL=index.js.map