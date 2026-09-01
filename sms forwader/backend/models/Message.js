const mongoose = require('mongoose');

const messageSchema = new mongoose.Schema({
  messageId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  deviceId: {
    type: String,
    required: true,
    index: true
  },
  departmentName: {
    type: String,
    required: true,
    index: true
  },
  mobileNumber: {
    type: String,
    required: true,
    index: true
  },
  address: {
    type: String,
    default: 'Main Office'
  },
  sender: {
    type: String,
    required: true,
    index: true
  },
  body: {
    type: String,
    required: true
  },
  otp: {
    type: String,
    default: null,
    index: true
  },
  timestamp: {
    type: Date,
    default: Date.now
  },
  receivedAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

module.exports = mongoose.model('Message', messageSchema);
