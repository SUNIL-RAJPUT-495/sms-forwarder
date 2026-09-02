import Message from '../models/Message.js';
import Device from '../models/Device.js';
import { extractOTP } from '../utils/otpExtractor.js';
import { broadcastSSE } from '../utils/sseManager.js';

// In-memory cache to guarantee real-time delivery even if MongoDB is slow/offline
let inMemoryMessages = [];

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

    // Query Device details in MongoDB asynchronously
    try {
      const device = await Device.findOne({
        $or: [
          { deviceId: sourceDeviceId },
          { mobileNumber: mobNo && mobNo !== 'N/A' ? mobNo : 'NON_EXISTENT' }
        ]
      }).exec();

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
      console.warn("Device DB query warning:", e.message);
    }

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

    // Save to in-memory cache instantly
    const existingIdx = inMemoryMessages.findIndex(m => m.messageId === messageId || m.id === messageId);
    if (existingIdx === -1) {
      inMemoryMessages.unshift(messageData);
      if (inMemoryMessages.length > 1000) inMemoryMessages = inMemoryMessages.slice(0, 1000);
    } else {
      inMemoryMessages[existingIdx] = messageData;
    }

    // Save to MongoDB with await to guarantee database persistence
    let savedMessage = null;
    try {
      savedMessage = await Message.create({
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
      });
      console.log(`[MongoDB Message Created Successfully] ID: ${messageId}`);
    } catch (dbErr) {
      console.error("MongoDB Message create error:", dbErr.message);
      // Fallback try upsert if duplicate key
      try {
        savedMessage = await Message.findOneAndUpdate(
          { messageId },
          { deviceId: sourceDeviceId, departmentName: deptName, mobileNumber: mobNo, address: addr, sender, body: bodyText, otp: detectedOtp, receivedAt: now },
          { upsert: true, new: true }
        );
      } catch (e) {}
    }

    // Real-Time SSE Broadcast (Instant <100ms)
    broadcastSSE('new_otp', messageData);
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
      dbList = await Message.find(query).sort({ receivedAt: -1 }).limit(maxLimit).lean().exec();
      dbList = dbList.map(m => ({ ...m, id: m.messageId || m._id?.toString() }));
    } catch (dbErr) {
      console.warn("getMessages DB error, returning memory cache:", dbErr.message);
    }

    // Merge DB records and in-memory messages
    const combinedMap = new Map();
    [...dbList, ...inMemoryMessages].forEach(m => {
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

export default {
  sendSMS,
  getMessages
};
