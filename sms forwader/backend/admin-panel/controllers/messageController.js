import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import Message from '../models/Message.js';
import Device from '../models/Device.js';
import { extractOTP } from '../utils/otpExtractor.js';
import { broadcastSSE } from '../utils/sseManager.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Fallback JSON file storage
const DATA_DIR = path.join(__dirname, '..', 'data');
const MESSAGES_FILE = path.join(DATA_DIR, 'messages.json');
const DEVICES_FILE = path.join(DATA_DIR, 'devices.json');

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
let fileDevices = loadJSON(DEVICES_FILE, []);

/**
 * Receive SMS / OTP / Notification from Department Phone
 * POST /api/send-sms & POST /sendMessage
 */
export const sendSMS = async (req, res) => {
  try {
    const sourceDeviceId = req.body.deviceId || req.body.sourceDeviceId || 'UNKNOWN';
    const sender = req.body.sender || 'NOTIFICATION';
    const bodyText = req.body.body || req.body.text || (req.body.ciphertext ? `[Encrypted Message: ${req.body.ciphertext.substring(0, 30)}...]` : null);

    if (!bodyText) {
      return res.status(400).json({ error: "SMS body content is required", accepted: false });
    }

    const now = new Date();
    const detectedOtp = extractOTP(bodyText);
    const messageId = req.body.messageId || ('MSG-' + Date.now() + '-' + Math.floor(Math.random() * 1000));

    let deptName = req.body.departmentName || 'Department Phone';
    let mobNo = req.body.mobileNumber || 'N/A';
    let addr = req.body.address || 'Main Office';

    // Try finding device in DB or file storage
    try {
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
    } catch (e) {
      const fDev = fileDevices.find(d => d.deviceId === sourceDeviceId || (d.mobileNumber === mobNo && mobNo !== 'N/A'));
      if (fDev) {
        deptName = fDev.departmentName;
        mobNo = fDev.mobileNumber;
        addr = fDev.address;
        fDev.lastSeen = now.toISOString();
        fDev.status = 'ONLINE';
        fDev.messageCount = (fDev.messageCount || 0) + 1;
        saveJSON(DEVICES_FILE, fileDevices);
      }
    }

    const messageData = {
      messageId,
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

    // Dual save to MongoDB AND persistent JSON file storage
    try {
      await Message.create(messageData);
    } catch (dbErr) {
      console.warn("MongoDB save warning, writing to JSON file:", dbErr.message);
    }

    // Always keep persistent JSON up to date
    const existingMsgIndex = fileMessages.findIndex(m => m.messageId === messageId || m.id === messageId);
    if (existingMsgIndex === -1) {
      fileMessages.unshift({ ...messageData, id: messageId });
      if (fileMessages.length > 1000) fileMessages = fileMessages.slice(0, 1000);
      saveJSON(MESSAGES_FILE, fileMessages);
    }

    // Real-Time SSE Broadcast to Admin Controller & Admin App
    broadcastSSE('new_otp', { ...messageData, id: messageId });
    broadcastSSE('device_ping', { deviceId: sourceDeviceId, lastSeen: now });

    console.log(`[SMS/Notification Received] Dept: ${deptName} | Sender: ${sender} | OTP: ${detectedOtp || 'None'}`);

    return res.status(200).json({
      success: true,
      accepted: true,
      messageId,
      otpDetected: !!detectedOtp,
      otp: detectedOtp
    });
  } catch (error) {
    console.error("sendSMS error:", error);
    return res.status(500).json({ error: "Server Error", accepted: false });
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
    let mongoList = [];
    let fileList = loadJSON(MESSAGES_FILE, []);

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
      mongoList = await Message.find(query).sort({ receivedAt: -1 }).limit(maxLimit).lean();
      mongoList = mongoList.map(m => ({ ...m, id: m.messageId }));
    } catch (dbErr) {}

    // Merge both Mongo and Local JSON records cleanly
    const combinedMap = new Map();
    [...mongoList, ...fileList].forEach(m => {
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
        m.body.toLowerCase().includes(q) ||
        m.sender.toLowerCase().includes(q) ||
        m.departmentName.toLowerCase().includes(q) ||
        m.mobileNumber.toLowerCase().includes(q) ||
        (m.otp && m.otp.includes(q))
      );
    }
    list = list.slice(0, maxLimit);

    return res.json(list);
  } catch (error) {
    console.error("getMessages error:", error);
    return res.status(500).json({ error: "Server Error" });
  }
};

export default {
  sendSMS,
  getMessages
};
