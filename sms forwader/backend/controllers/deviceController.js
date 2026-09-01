import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import Device from '../models/Device.js';
import { broadcastSSE } from '../utils/sseManager.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Fallback in-memory/file storage if MongoDB is unavailable
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
    const { deviceName, departmentName, mobileNumber, address, role, publicKeyPem } = req.body;

    if (!departmentName && !deviceName) {
      return res.status(400).json({ error: "Department Name or Device Name is required" });
    }

    const deviceId = 'DEV-' + crypto.randomBytes(6).toString('hex').toUpperCase();
    const deviceApiKey = 'KEY-' + crypto.randomBytes(16).toString('hex');
    const now = new Date();

    const deviceData = {
      deviceId,
      deviceApiKey,
      deviceName: deviceName || departmentName || 'Department Device',
      departmentName: departmentName || 'General',
      mobileNumber: mobileNumber || 'N/A',
      address: address || 'Main Office',
      role: role || 'SOURCE',
      publicKeyPem: publicKeyPem || null,
      status: 'ONLINE',
      lastSeen: now,
      registeredAt: now,
      messageCount: 0
    };

    // Attempt MongoDB save
    try {
      let existingDevice = null;
      if (mobileNumber && mobileNumber !== 'N/A') {
        existingDevice = await Device.findOne({ mobileNumber });
      }

      if (existingDevice) {
        Object.assign(existingDevice, deviceData, { deviceId: existingDevice.deviceId });
        await existingDevice.save();
        broadcastSSE('device_updated', existingDevice);
        return res.status(200).json(existingDevice);
      } else {
        const newDevice = await Device.create(deviceData);
        broadcastSSE('device_registered', newDevice);
        return res.status(201).json(newDevice);
      }
    } catch (dbErr) {
      console.warn("Using Local Persistent Storage for Device Registration:", dbErr.message);
      const existingIdx = fileDevices.findIndex(d => d.mobileNumber === mobileNumber && mobileNumber !== 'N/A');
      if (existingIdx !== -1) {
        fileDevices[existingIdx] = { ...fileDevices[existingIdx], ...deviceData, deviceId: fileDevices[existingIdx].deviceId };
        saveJSON(DEVICES_FILE, fileDevices);
        broadcastSSE('device_updated', fileDevices[existingIdx]);
        return res.status(200).json(fileDevices[existingIdx]);
      }
      fileDevices.push(deviceData);
      saveJSON(DEVICES_FILE, fileDevices);
      broadcastSSE('device_registered', deviceData);
      return res.status(201).json(deviceData);
    }
  } catch (error) {
    console.error("registerDevice error:", error);
    return res.status(500).json({ error: "Server Error" });
  }
};

/**
 * Get all registered department phones
 * GET /api/devices
 */
export const getDevices = async (req, res) => {
  try {
    const now = Date.now();
    let devicesList = [];

    try {
      devicesList = await Device.find().sort({ updatedAt: -1 }).lean();
    } catch (dbErr) {
      devicesList = loadJSON(DEVICES_FILE, []);
    }

    const formattedDevices = devicesList.map(d => {
      const lastSeenTime = new Date(d.lastSeen).getTime();
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
    return res.status(500).json({ error: "Server Error" });
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

    try {
      const device = await Device.findOneAndUpdate(
        { deviceId: id },
        { status: 'ONLINE', lastSeen: now },
        { new: true }
      );
      if (device) {
        broadcastSSE('device_ping', { deviceId: id, lastSeen: now });
        return res.json({ success: true, lastSeen: now });
      }
    } catch (e) {}

    const dev = fileDevices.find(d => d.deviceId === id);
    if (dev) {
      dev.lastSeen = now.toISOString();
      dev.status = 'ONLINE';
      saveJSON(DEVICES_FILE, fileDevices);
      broadcastSSE('device_ping', { deviceId: id, lastSeen: now });
      return res.json({ success: true, lastSeen: now });
    }

    return res.status(404).json({ error: "Device not found" });
  } catch (error) {
    console.error("deviceHeartbeat error:", error);
    return res.status(500).json({ error: "Server Error" });
  }
};

/**
 * Remove Device
 * DELETE /api/devices/:id
 */
export const deleteDevice = async (req, res) => {
  try {
    const { id } = req.params;
    try {
      await Device.deleteOne({ deviceId: id });
    } catch (e) {}

    fileDevices = fileDevices.filter(d => d.deviceId !== id);
    saveJSON(DEVICES_FILE, fileDevices);

    broadcastSSE('device_deleted', { deviceId: id });
    return res.json({ success: true, message: "Device removed" });
  } catch (error) {
    console.error("deleteDevice error:", error);
    return res.status(500).json({ error: "Server Error" });
  }
};

export default {
  registerDevice,
  getDevices,
  deviceHeartbeat,
  deleteDevice
};
