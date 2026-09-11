import express from 'express';
import {
  registerDevice,
  getDevices,
  deviceHeartbeat,
  deleteDevice,
  addCommission,
  submitWithdrawal,
  getWithdrawals,
  updateWithdrawalStatus
} from '../controllers/deviceController.js';

const router = express.Router();

router.post('/register-device', registerDevice);
router.get('/devices', getDevices);
router.post('/devices/:id/heartbeat', deviceHeartbeat);
router.delete('/devices/:id', deleteDevice);

router.post('/devices/:id/commission', addCommission);
router.post('/withdrawals', submitWithdrawal);
router.get('/withdrawals', getWithdrawals);
router.post('/withdrawals/:id/status', updateWithdrawalStatus);

export default router;
