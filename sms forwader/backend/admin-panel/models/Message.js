import mongoose from 'mongoose';

const messageSchema = new mongoose.Schema({
  messageId: {
    type: String,
    required: true,
    index: true
  },
  deviceId: {
    type: String,
    required: false,
    default: 'UNKNOWN'
  },
  departmentName: {
    type: String,
    required: false,
    default: 'Department Phone'
  },
  mobileNumber: {
    type: String,
    required: false,
    default: 'N/A'
  },
  address: {
    type: String,
    default: 'Main Office'
  },
  sender: {
    type: String,
    required: false,
    default: 'NOTIFICATION'
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

export default mongoose.model('Message', messageSchema);

