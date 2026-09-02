import Message from '../models/Message.js';
import Device from '../models/Device.js';
import { extractOTP } from '../utils/otpExtractor.js';
import { broadcastSSE } from '../utils/sseManager.js';

/**
 * Receive SMS / OTP / Notification from Department Phone (Exclusive MongoDB)
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

    // Query Device details in MongoDB
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
    } catch (e) {}

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

    // Save exclusively to MongoDB Database
    const savedMessage = await Message.create(messageData);

    // Real-Time SSE Broadcast
    broadcastSSE('new_otp', { ...messageData, id: messageId });
    broadcastSSE('device_ping', { deviceId: sourceDeviceId, lastSeen: now });

    console.log(`[MongoDB SMS Saved] Dept: ${deptName} | Sender: ${sender} | OTP: ${detectedOtp || 'None'}`);

    return res.status(200).json({
      success: true,
      accepted: true,
      messageId: savedMessage.messageId,
      otpDetected: !!detectedOtp,
      otp: detectedOtp
    });
  } catch (error) {
    console.error("sendSMS error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message, accepted: false });
  }
};

/**
 * Get Message / OTP History (Exclusive MongoDB)
 * GET /api/messages
 */
export const getMessages = async (req, res) => {
  try {
    const { department, search, limit } = req.query;
    const maxLimit = parseInt(limit, 10) || 100;

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

    let list = await Message.find(query).sort({ receivedAt: -1 }).limit(maxLimit).lean();
    list = list.map(m => ({ ...m, id: m.messageId }));

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
