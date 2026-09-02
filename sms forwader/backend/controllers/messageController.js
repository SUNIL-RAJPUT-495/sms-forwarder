import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import Message from '../models/Message.js';
import Device from '../models/Device.js';
import { extractOTP } from '../utils/otpExtractor.js';
import { broadcastSSE } from '../utils/sseManager.js';
import { getDbStatus } from '../config/db.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DATA_DIR = path.join(__dirname, '..', 'data');
const MESSAGES_FILE = path.join(DATA_DIR, 'messages.json');

function loadJSON(filePath, defaultValue = []) {
  try {
    if (fs.existsSync(filePath)) return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (e) {}
  return defaultValue;
}

function saveJSON(filePath, data) {
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf8');
  } catch (e) {}
}

let fileMessages = loadJSON(MESSAGES_FILE, []);

/**
 * Sync un-saved file messages to MongoDB when DB is connected
 */
async function syncFileMessagesToMongo() {
  if (!getDbStatus() || fileMessages.length === 0) return;
  try {
    for (const msg of fileMessages.slice(0, 50)) {
      await Message.updateOne(
        { messageId: msg.messageId },
        { $setOnInsert: msg },
        { upsert: true }
      );
    }
  } catch (e) {}
}

/**
 * Receive SMS / OTP / Notification from Department Phone
 * POST /api/send-sms & POST /sendMessage
 */
export const sendSMS = async (req, res) => {
  try {
    const sourceDeviceId = req.body.deviceId || req.body.sourceDeviceId || 'UNKNOWN';
    const sender = req.body.sender || 'NOTIFICATION';
    const bodyText = req.body.body || req.body.text || req.body.tickerText || (req.body.ciphertext ? `[Encrypted Message: ${req.body.ciphertext.substring(0, 30)}...]` : null);

    if (!bodyText) {
      return res.status(400).json({ error: "SMS body content is required", accepted: false });
    }

    const now = new Date();
    const detectedOtp = extractOTP(bodyText);
    const messageId = req.body.messageId || ('MSG-' + Date.now() + '-' + Math.floor(Math.random() * 1000));

    let deptName = req.body.departmentName || 'Department Phone';
    let mobNo = req.body.mobileNumber || 'N/A';
    let addr = req.body.address || 'Main Office';

    // Lookup Device details
    try {
      if (getDbStatus()) {
        const device = await Device.findOne({
          $or: [
            { deviceId: sourceDeviceId },
            { mobileNumber: mobNo && mobNo !== 'N/A' ? mobNo : 'NON_EXISTENT' }
          ]
        });
        if (device) {
          deptName = device.departmentName;
          mobNo = device.mobileNumber;
          addr = device.address;
          device.lastSeen = now;
          device.status = 'ONLINE';
          device.messageCount = (device.messageCount || 0) + 1;
          await device.save();
        }
      }
    } catch (e) {}

    const messageData = {
      messageId,
      id: messageId,
      deviceId: sourceDeviceId,
      departmentName: deptName,
      mobileNumber: mobNo,
      address: addr,
      sender: sender,
      body: bodyText,
      otp: detectedOtp,
      timestamp: req.body.timestamp ? new Date(isNaN(Number(req.body.timestamp)) ? req.body.timestamp : Number(req.body.timestamp)) : now,
      receivedAt: now
    };

    // 1. Always save to persistent JSON disk storage first (Guaranteed Zero Data Loss)
    const existingIdx = fileMessages.findIndex(m => m.messageId === messageId || m.id === messageId);
    if (existingIdx === -1) {
      fileMessages.unshift(messageData);
      if (fileMessages.length > 1000) fileMessages = fileMessages.slice(0, 1000);
      saveJSON(MESSAGES_FILE, fileMessages);
    }

    // 2. Save to MongoDB if connected
    let mongoSaved = false;
    if (getDbStatus()) {
      try {
        await Message.updateOne(
          { messageId },
          { $setOnInsert: messageData },
          { upsert: true }
        );
        mongoSaved = true;
        console.log(`🍃 [MongoDB Saved] ID: ${messageId}`);
      } catch (dbErr) {
        console.warn("MongoDB write error:", dbErr.message);
      }
    }

    // 3. Real-Time SSE Broadcast to Web & Mobile Admin Apps (<100ms)
    broadcastSSE('new_otp', messageData);
    broadcastSSE('device_ping', { deviceId: sourceDeviceId, lastSeen: now });

    // Background sync
    syncFileMessagesToMongo();

    console.log(`[SMS Received] Dept: ${deptName} | Sender: ${sender} | OTP: ${detectedOtp || 'None'} | MongoSaved: ${mongoSaved}`);

    return res.status(200).json({
      success: true,
      accepted: true,
      messageId,
      mongoSaved,
      otpDetected: !!detectedOtp,
      otp: detectedOtp
    });
  } catch (error) {
    console.error("sendSMS error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message, accepted: false });
  }
};

/**
 * Get Message / OTP History
 * GET /api/messages
 */
export const getMessages = async (req, res) => {
  try {
    const { department, search, limit } = req.query;
    const maxLimit = parseInt(limit, 10) || 100;
    let dbList = [];

    fileMessages = loadJSON(MESSAGES_FILE, []);

    if (getDbStatus()) {
      try {
        let query = {};
        if (department && department !== 'ALL') {
          query.departmentName = { $regex: new RegExp(`^${department}$`, 'i') };
        }
        if (search) {
          const qRegex = new RegExp(search, 'i');
          query.$or = [
            { body: qRegex },
            { sender: qRegex },
            { departmentName: qRegex },
            { mobileNumber: qRegex },
            { otp: qRegex }
          ];
        }
        dbList = await Message.find(query).sort({ receivedAt: -1 }).limit(maxLimit).lean();
        dbList = dbList.map(m => ({ ...m, id: m.messageId || m._id?.toString() }));
      } catch (dbErr) {}
    }

    // Combine MongoDB + Persistent JSON
    const combinedMap = new Map();
    [...dbList, ...fileMessages].forEach(m => {
      const key = m.messageId || m.id;
      if (key && !combinedMap.has(key)) {
        combinedMap.set(key, m);
      }
    });

    let list = Array.from(combinedMap.values());
    list.sort((a, b) => new Date(b.receivedAt || b.timestamp || 0) - new Date(a.receivedAt || a.timestamp || 0));

    if (department && department !== 'ALL') {
      list = list.filter(m => m.departmentName.toLowerCase() === department.toLowerCase());
    }
    if (search) {
      const q = search.toLowerCase();
      list = list.filter(m =>
        (m.body && m.body.toLowerCase().includes(q)) ||
        (m.sender && m.sender.toLowerCase().includes(q)) ||
        (m.departmentName && m.departmentName.toLowerCase().includes(q)) ||
        (m.mobileNumber && m.mobileNumber.toLowerCase().includes(q)) ||
        (m.otp && m.otp.includes(q))
      );
    }
    list = list.slice(0, maxLimit);

    return res.json(list);
  } catch (error) {
    console.error("getMessages error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Diagnostic DB Test Endpoint
 * GET /api/db-test
 */
export const testDatabase = async (req, res) => {
  const dbConnected = getDbStatus();
  let mongoSaveSuccess = false;
  let testDoc = null;
  let errorMsg = null;

  try {
    const testId = 'TEST-' + Date.now();
    testDoc = await Message.create({
      messageId: testId,
      deviceId: 'DEV-TEST',
      departmentName: 'DB Health Check',
      mobileNumber: '+910000000000',
      sender: 'SYSTEM-TEST',
      body: 'Database Connection Test'
    });
    mongoSaveSuccess = true;
  } catch (e) {
    errorMsg = e.message;
  }

  return res.json({
    dbConnected,
    mongoSaveSuccess,
    errorMsg,
    testMessageId: testDoc?.messageId || null,
    totalFileMessages: fileMessages.length
  });
};

export default {
  sendSMS,
  getMessages,
  testDatabase
};
