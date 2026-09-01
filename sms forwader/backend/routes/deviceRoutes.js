const express = require('express');
const router = express.Router();
const {
  registerDevice,
  getDevices,
  deviceHeartbeat,
  deleteDevice
} = require('../controllers/deviceController');

router.post('/register-device', registerDevice);
router.get('/devices', getDevices);
router.post('/devices/:id/heartbeat', deviceHeartbeat);
router.delete('/devices/:id', deleteDevice);

module.exports = router;
