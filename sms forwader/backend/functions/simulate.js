const crypto = require("crypto");

console.log("===============================================================");
console.log(" 📱 UNIVERSAL SMS FORWARDER — MULTI-DEVICE E2E SIMULATION");
console.log("===============================================================\n");

// 1. SETUP RECEIVER DEVICE (Any Android Phone: Samsung, Pixel, OnePlus, etc.)
console.log("[1/6] 🔒 Initializing Receiver Device (e.g. Google Pixel / Samsung Galaxy)...");
const receiverKeyPair = crypto.generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "spki", format: "pem" },
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
});
const receiverDeviceId = "receiver_dev_" + crypto.randomBytes(6).toString("hex");
console.log(`      Device ID: ${receiverDeviceId}`);
console.log("      Hardware-backed RSA-2048 Asymmetric Keypair generated in KeyStore.");

// 2. PAIRING SESSION
console.log("\n[2/6] 🤝 Initiating Universal Pairing Session on Receiver...");
const rawPairingToken = crypto.randomBytes(32).toString("base64url");
const hash = crypto.createHash("sha256").update(rawPairingToken).digest("hex").toUpperCase();
const displayCode = `${hash.slice(0, 3)}-${hash.slice(3, 6)}`;
console.log(`      Generated 6-Digit Pairing Code: \x1b[32m\x1b[1m${displayCode}\x1b[0m`);
console.log(`      Generated QR Code URI: smsforwarder://pair?token=${rawPairingToken}&code=${displayCode}`);

// 3. SENDER PAIRING (Any Android Phone: Xiaomi, OPPO, Motorola, Vivo, etc.)
console.log("\n[3/6] 📱 Configuring Sender Device (e.g. Xiaomi / OPPO / SIM phone)...");
const senderDeviceId = "sender_dev_" + crypto.randomBytes(6).toString("hex");
console.log(`      Device ID: ${senderDeviceId}`);
console.log(`      Pairing executed with code '${displayCode}' -> Paired successfully.`);

// 4. INCOMING SMS & FILTER EVALUATION
console.log("\n[4/6] 📥 Simulating Incoming Bank SMS on Sender Device...");
const simulatedSms = {
  sender: "HDFCBK",
  body: "Dear Customer, OTP is 849201 for payment of Rs 1,499.00 to SWIGGY. Valid for 5 mins. Do not share OTP.",
  timestamp: Date.now(),
};
console.log(`      From: \x1b[33m${simulatedSms.sender}\x1b[0m`);
console.log(`      Body: "${simulatedSms.body}"`);

const isBankSms = /hdfc|sbi|icici|otp|payment|rs/i.test(simulatedSms.sender + simulatedSms.body);
console.log(`      Filter Rule Match: \x1b[32m${isBankSms ? "MATCHED (Forwarding)" : "IGNORED"}\x1b[0m`);

// 5. HYBRID ENCRYPTION (AES-256-GCM + RSA-OAEP)
console.log("\n[5/6] 🔐 Encrypting Payload on Sender Device (Zero-Knowledge E2EE)...");
const ephemeralAesKey = crypto.randomBytes(32);
const nonce = crypto.randomBytes(12);
const messageId = "msg_" + Date.now();
const aad = `v1|${messageId}|${senderDeviceId}|${receiverDeviceId}|${simulatedSms.timestamp}`;

const cipher = crypto.createCipheriv("aes-256-gcm", ephemeralAesKey, nonce);
cipher.setAAD(Buffer.from(aad, "utf8"));
const ciphertext = Buffer.concat([cipher.update(`${simulatedSms.sender}\n${simulatedSms.body}`, "utf8"), cipher.final()]);
const authTag = cipher.getAuthTag();
const fullCiphertext = Buffer.concat([ciphertext, authTag]);

const encryptedKey = crypto.publicEncrypt(
  {
    key: receiverKeyPair.publicKey,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: "sha256",
  },
  ephemeralAesKey
);

console.log(`      Ephemeral AES-256 Key: [GENERATED & WRAPPED WITH RECEIVER RSA PUBKEY]`);
console.log(`      Ciphertext (Base64): ${fullCiphertext.toString("base64").slice(0, 40)}...`);
console.log(`      AAD Authentication Tag: ${authTag.toString("hex")}`);

// 6. BACKEND RELAY & RECEIVER DECRYPTION
console.log("\n[6/6] 🚀 Relaying via Firebase Cloud Relay & Decrypting on Receiver...");
const decryptedAesKey = crypto.privateDecrypt(
  {
    key: receiverKeyPair.privateKey,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: "sha256",
  },
  encryptedKey
);

const receivedCiphertext = fullCiphertext.subarray(0, fullCiphertext.length - 16);
const receivedTag = fullCiphertext.subarray(fullCiphertext.length - 16);

const decipher = crypto.createDecipheriv("aes-256-gcm", decryptedAesKey, nonce);
decipher.setAAD(Buffer.from(aad, "utf8"));
decipher.setAuthTag(receivedTag);

const decryptedPlaintext = Buffer.concat([decipher.update(receivedCiphertext), decipher.final()]).toString("utf8");
const [sender, ...bodyParts] = decryptedPlaintext.split("\n");
const body = bodyParts.join("\n");

// OTP Extraction
const otpMatch = body.match(/(?:otp|code|is)\s*(?:is\s*)?[:\-]?\s*([0-9]{4,8})/i) || body.match(/\b([0-9]{6})\b/);
const extractedOtp = otpMatch ? otpMatch[1] : "N/A";

console.log("\n===============================================================");
console.log(" ✅ TEST PASSED: RECEIVER RECEIVED & DECRYPTED NOTIFICATION");
console.log("===============================================================");
console.log(` 🔔 Notification Title : 🔑 OTP: ${extractedOtp} (${sender})`);
console.log(` 💬 Notification Body  : ${body}`);
console.log(` 🔘 Quick Action       : [ Copy ${extractedOtp} ]`);
console.log("===============================================================\n");
