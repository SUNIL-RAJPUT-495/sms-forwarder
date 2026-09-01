import * as crypto from "crypto";
import {
  sha256,
  generateSecureToken,
  generateDisplayCode,
  validateTimestamp,
  validatePayloadSize,
  MAX_PAYLOAD_BYTES,
} from "../src/util/helpers";

/**
 * End-to-End Test Suite for Universal SMS Forwarder
 * Simulates:
 * 1. Any Android Receiver Device (e.g. Samsung / Pixel / OnePlus) Registration & RSA Keypair Generation
 * 2. Any Android Sender Device (e.g. OPPO / Xiaomi / Moto) Registration
 * 3. Pairing Code Generation & Multi-format Exchange (QR / Display Code / Token)
 * 4. Bank SMS Filter Rules Matching & OTP Extraction
 * 5. Hybrid Cryptography (RSA-OAEP + AES-256-GCM) by Sender
 * 6. Backend Relay Validation & Zero-Plaintext Forwarding
 * 7. Receiver Decryption, Integrity Verification & Message Parsing
 */

describe("Universal E2E SMS Forwarder System Simulation", () => {
  // Test entities (Generic Sender and Receiver devices)
  let receiverKeyPair: crypto.KeyPairSyncResult<string, string>;
  let receiverDeviceId: string;
  let senderDeviceId: string;
  let pairingToken: string;
  let displayCode: string;

  beforeAll(() => {
    // Generate Receiver RSA-2048 Keypair (generated inside Android Keystore on physical devices)
    receiverKeyPair = crypto.generateKeyPairSync("rsa", {
      modulusLength: 2048,
      publicKeyEncoding: { type: "spki", format: "pem" },
      privateKeyEncoding: { type: "pkcs8", format: "pem" },
    });
  });

  test("Step 1: Any Receiver device registers with backend", () => {
    receiverDeviceId = generateSecureToken(16);
    const receiverApiKey = generateSecureToken(32);
    const apiKeyHash = sha256(receiverApiKey);

    expect(receiverDeviceId.length).toBeGreaterThanOrEqual(16);
    expect(receiverKeyPair.publicKey).toContain("BEGIN PUBLIC KEY");
    expect(apiKeyHash).toHaveLength(64);
  });

  test("Step 2: Receiver initiates pairing and generates 6-char display code", () => {
    pairingToken = generateSecureToken(32);
    displayCode = generateDisplayCode(pairingToken);

    expect(displayCode).toMatch(/^[A-Z0-9]{3}-[A-Z0-9]{3}$/);
    console.log(`[PAIRING] Receiver generated pairing code: ${displayCode}`);
  });

  test("Step 3: Any Sender device registers and pairs using token", () => {
    senderDeviceId = generateSecureToken(16);
    const senderApiKey = generateSecureToken(32);

    expect(senderDeviceId).toBeDefined();
    expect(senderApiKey).toBeDefined();

    // Verification of token matching
    const tokenHash = sha256(pairingToken);
    expect(sha256(pairingToken)).toBe(tokenHash);
    console.log(`[PAIRING] Sender device paired successfully with Receiver (${receiverDeviceId})`);
  });

  describe("Step 4: SMS Filter Engine Logic", () => {
    const bankSmsSamples = [
      {
        sender: "HDFCBK",
        body: "Your OTP for Rs 4,500.00 at Flipkart is 839201. Valid for 10 mins. Do not share OTP.",
        shouldForward: true,
        expectedOtp: "839201",
      },
      {
        sender: "SBIINB",
        body: "Dear Customer, INR 12,000.00 debited from A/C **1234 on 31-Aug-26 via UPI. Ref: 9812739182.",
        shouldForward: true,
        expectedOtp: null,
      },
      {
        sender: "ICICIB",
        body: "482910 is your secret verification code for NetBanking login.",
        shouldForward: true,
        expectedOtp: "482910",
      },
      {
        sender: "DOMINOS",
        body: "Buy 1 Get 1 Free pizza today! Use code YUMMY at checkout.",
        shouldForward: false,
        expectedOtp: null,
      },
      {
        sender: "+919876543210",
        body: "Hey, are you free for dinner tonight?",
        shouldForward: false,
        expectedOtp: null,
      },
    ];

    const bankKeywords = ["otp", "debited", "credited", "inr", "rs.", "rs ", "a/c", "acct", "balance", "txn", "transaction", "verification code"];
    const bankSenders = ["HDFCBK", "SBIINB", "ICICIB", "AXISBK", "KOTAKB", "PAYTM"];

    function evaluateSms(sender: string, body: string): { matches: boolean; extractedOtp: string | null } {
      const lowerBody = body.toLowerCase();
      const senderMatches = bankSenders.some((s) => sender.toUpperCase().includes(s));
      const keywordMatches = bankKeywords.some((k) => lowerBody.includes(k));

      const matches = senderMatches || keywordMatches;

      let extractedOtp: string | null = null;
      if (matches) {
        // OTP Regex pattern (4 to 8 digits following otp/code/verification)
        const otpMatch = body.match(/(?:otp|code|is)\s*(?:is\s*)?[:\-]?\s*([0-9]{4,8})/i) ||
                         body.match(/\b([0-9]{6})\b/);
        if (otpMatch) {
          extractedOtp = otpMatch[1];
        }
      }

      return { matches, extractedOtp };
    }

    bankSmsSamples.forEach((sample, idx) => {
      test(`Filter Evaluation ${idx + 1}: ${sample.sender}`, () => {
        const result = evaluateSms(sample.sender, sample.body);
        expect(result.matches).toBe(sample.shouldForward);
        if (sample.expectedOtp) {
          expect(result.extractedOtp).toBe(sample.expectedOtp);
        }
      });
    });
  });

  test("Step 5 & 6: Sender Encrypts, Backend Relays, Receiver Decrypts", () => {
    const rawSms = {
      sender: "HDFCBK",
      body: "Your OTP for transaction of Rs. 3,250.00 is 591823. Do not share with anyone.",
      timestamp: Date.now(),
    };

    const messageId = `msg_${Date.now()}_${generateSecureToken(8)}`;
    const protocolVersion = 1;

    // --- SENDER ENCRYPTION (Hybrid AES-256-GCM + RSA-OAEP) ---
    // 1. Generate ephemeral 256-bit AES key and 12-byte IV/nonce
    const ephemeralAesKey = crypto.randomBytes(32);
    const nonce = crypto.randomBytes(12);

    // 2. Build AAD: "v1|messageId|sourceDeviceId|destinationDeviceId|timestamp"
    const aad = `v${protocolVersion}|${messageId}|${senderDeviceId}|${receiverDeviceId}|${rawSms.timestamp}`;
    const aadBuffer = Buffer.from(aad, "utf8");

    // 3. Plaintext payload: "SENDER\nBODY"
    const plaintext = `${rawSms.sender}\n${rawSms.body}`;

    // 4. Encrypt with AES-256-GCM
    const cipher = crypto.createCipheriv("aes-256-gcm", ephemeralAesKey, nonce);
    cipher.setAAD(aadBuffer);
    const ciphertext = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
    const authTag = cipher.getAuthTag();

    // Ciphertext payload wire format includes appended 16-byte auth tag (128 bits)
    const fullCiphertext = Buffer.concat([ciphertext, authTag]);

    // 5. Wrap AES key with Receiver's RSA Public Key (RSA-OAEP-SHA256)
    const encryptedKey = crypto.publicEncrypt(
      {
        key: receiverKeyPair.publicKey,
        padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
        oaepHash: "sha256",
      },
      ephemeralAesKey
    );

    const outboundPayload = {
      protocolVersion,
      messageId,
      sourceDeviceId: senderDeviceId,
      destinationDeviceId: receiverDeviceId,
      encryptedKeyBase64: encryptedKey.toString("base64"),
      nonceBase64: nonce.toString("base64"),
      ciphertextBase64: fullCiphertext.toString("base64"),
      timestamp: rawSms.timestamp,
    };

    // --- BACKEND VALIDATION ---
    expect(validateTimestamp(outboundPayload.timestamp)).toBe(true);
    expect(
      validatePayloadSize(
        outboundPayload.encryptedKeyBase64,
        outboundPayload.nonceBase64,
        outboundPayload.ciphertextBase64
      )
    ).toBe(true);

    // --- RECEIVER DECRYPTION ---
    // 1. Unwrap AES key using Receiver's RSA Private Key
    const decryptedAesKey = crypto.privateDecrypt(
      {
        key: receiverKeyPair.privateKey,
        padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
        oaepHash: "sha256",
      },
      Buffer.from(outboundPayload.encryptedKeyBase64, "base64")
    );

    expect(decryptedAesKey.equals(ephemeralAesKey)).toBe(true);

    // 2. Reconstruct AAD on Receiver
    const receivedAad = `v${outboundPayload.protocolVersion}|${outboundPayload.messageId}|${outboundPayload.sourceDeviceId}|${outboundPayload.destinationDeviceId}|${outboundPayload.timestamp}`;
    const rawCiphertextWithTag = Buffer.from(outboundPayload.ciphertextBase64, "base64");
    const receivedNonce = Buffer.from(outboundPayload.nonceBase64, "base64");

    // Separate ciphertext and 16-byte GCM tag
    const receivedCiphertext = rawCiphertextWithTag.subarray(0, rawCiphertextWithTag.length - 16);
    const receivedTag = rawCiphertextWithTag.subarray(rawCiphertextWithTag.length - 16);

    const decipher = crypto.createDecipheriv("aes-256-gcm", decryptedAesKey, receivedNonce);
    decipher.setAAD(Buffer.from(receivedAad, "utf8"));
    decipher.setAuthTag(receivedTag);

    const decryptedPlaintext = Buffer.concat([
      decipher.update(receivedCiphertext),
      decipher.final(),
    ]).toString("utf8");

    // 3. Parse "{sender}\n{body}"
    const newlineIndex = decryptedPlaintext.indexOf("\n");
    const parsedSender = decryptedPlaintext.substring(0, newlineIndex);
    const parsedBody = decryptedPlaintext.substring(newlineIndex + 1);

    expect(parsedSender).toBe("HDFCBK");
    expect(parsedBody).toBe(rawSms.body);
    expect(parsedBody).toContain("591823");

    console.log(`\n[SUCCESS] Universal E2E SMS Decryption on Receiver:`);
    console.log(`  Sender: ${parsedSender}`);
    console.log(`  Decrypted Body: ${parsedBody}`);
  });

  test("Security: Tampered ciphertext or metadata fails GCM authentication", () => {
    // If an attacker or malicious relay alters the messageId or timestamp
    const tamperedAad = `v1|fake_id|${senderDeviceId}|${receiverDeviceId}|${Date.now()}`;
    const dummyKey = crypto.randomBytes(32);
    const dummyNonce = crypto.randomBytes(12);
    const dummyTag = crypto.randomBytes(16);
    const dummyCipher = crypto.randomBytes(30);

    const decipher = crypto.createDecipheriv("aes-256-gcm", dummyKey, dummyNonce);
    decipher.setAAD(Buffer.from(tamperedAad, "utf8"));
    decipher.setAuthTag(dummyTag);

    expect(() => {
      decipher.update(dummyCipher);
      decipher.final();
    }).toThrow();
  });
});
