import crypto from 'crypto';
import Device from '../models/Device.js';
import { broadcastSSE } from '../utils/sseManager.js';

/**
 * Register or Update a Department Device (Exclusive MongoDB)
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
  } catch (error) {
    console.error("registerDevice error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Get all registered department phones (Exclusive MongoDB)
 * GET /api/devices
 */
export const getDevices = async (req, res) => {
  try {
    const now = Date.now();
    const devicesList = await Device.find().sort({ updatedAt: -1 }).lean();

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
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Device Heartbeat Ping (Exclusive MongoDB)
 * POST /api/devices/:id/heartbeat
 */
export const deviceHeartbeat = async (req, res) => {
  try {
    const { id } = req.params;
    const now = new Date();

    const device = await Device.findOneAndUpdate(
      { deviceId: id },
      { status: 'ONLINE', lastSeen: now },
      { new: true }
    );

    if (device) {
      broadcastSSE('device_ping', { deviceId: id, lastSeen: now });
      return res.json({ success: true, lastSeen: now });
    }

    return res.status(404).json({ error: "Device not found" });
  } catch (error) {
    console.error("deviceHeartbeat error:", error);
    return res.status(500).json({ error: "Server Error: " + error.message });
  }
};

/**
 * Remove Device (Exclusive MongoDB)
 * DELETE /api/devices/:id
 */
export const deleteDevice = async (req, res) => {
  try {
    const { id } = req.params;
    await Device.deleteOne({ deviceId: id });
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
