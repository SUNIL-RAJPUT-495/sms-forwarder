const express = require('express');
const router = express.Router();
const { sendSMS, getMessages } = require('../controllers/messageController');
const { addClient } = require('../utils/sseManager');

router.post('/send-sms', sendSMS);
router.get('/messages', getMessages);
router.get('/stream', addClient);

module.exports = router;
