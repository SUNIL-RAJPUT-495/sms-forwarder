import express from 'express';
import {
  registerDevice,
  getDevices,
  deviceHeartbeat,
  deleteDevice
} from '../controllers/deviceController.js';

const router = express.Router();

router.post('/register-device', registerDevice);
router.get('/devices', getDevices);
router.post('/devices/:id/heartbeat', deviceHeartbeat);
router.delete('/devices/:id', deleteDevice);

export default router;

