import express from 'express';
import { sendSMS, getMessages, testDatabase } from '../controllers/messageController.js';
import { addClient } from '../utils/sseManager.js';

const router = express.Router();

router.post('/send-sms', sendSMS);
router.get('/messages', getMessages);
router.get('/stream', addClient);
router.get('/db-test', testDatabase);

export default router;
