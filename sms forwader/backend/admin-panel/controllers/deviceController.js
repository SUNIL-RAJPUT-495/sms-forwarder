import crypto from 'crypto';
import Device from '../models/Device.js';
import { broadcastSSE } from '../utils/sseManager.js';

let inMemoryDevices = [];

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
      role: role || 'SOURCE',
      publicKeyPem: publicKeyPem || null,
      status: 'ONLINE',
      lastSeen: now,
      registeredAt: now,
      messageCount: 0
    };

    // Update in-memory cache
    const existingIdx = inMemoryDevices.findIndex(d =>
      d.deviceId === deviceId || (d.mobileNumber === mobileNumber && mobileNumber !== 'N/A')
    );
    if (existingIdx !== -1) {
      inMemoryDevices[existingIdx] = { ...inMemoryDevices[existingIdx], ...deviceData, deviceId: inMemoryDevices[existingIdx].deviceId };
    } else {
      inMemoryDevices.push(deviceData);
    }

    // Save/Update in MongoDB asynchronously
    Device.findOne({
      $or: [
        { deviceId },
        { mobileNumber: mobileNumber && mobileNumber !== 'N/A' ? mobileNumber : 'NON_EXISTENT' }
      ]
    }).then(async (existing) => {
      if (existing) {
        Object.assign(existing, deviceData, { deviceId: existing.deviceId });
        await existing.save();
      } else {
        await Device.create(deviceData);
      }
    }).catch(e => {
      console.warn("MongoDB Device save warning:", e.message);
    });

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

    try {
      dbList = await Device.find().sort({ updatedAt: -1 }).lean().exec();
    } catch (e) {
      console.warn("getDevices DB error:", e.message);
    }

    const combinedMap = new Map();
    [...dbList, ...inMemoryDevices].forEach(d => {
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

    Device.findOneAndUpdate(
      { deviceId: id },
      { status: 'ONLINE', lastSeen: now },
      { new: true }
    ).catch(e => {});

    const dev = inMemoryDevices.find(d => d.deviceId === id);
    if (dev) {
      dev.lastSeen = now.toISOString();
      dev.status = 'ONLINE';
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
    Device.deleteOne({ deviceId: id }).catch(e => {});
    inMemoryDevices = inMemoryDevices.filter(d => d.deviceId !== id);

    broadcastSSE('device_deleted', { deviceId: id });
    return res.json({ success: true, message: "Device removed" });
  } catch (error) {
    console.error("deleteDevice error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

export default {
  registerDevice,
  getDevices,
  deviceHeartbeat,
  deleteDevice
};
