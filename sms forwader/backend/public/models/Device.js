import mongoose from 'mongoose';

const deviceSchema = new mongoose.Schema({
  deviceId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  deviceApiKey: {
    type: String,
    required: true
  },
  deviceName: {
    type: String,
    default: 'Department Device'
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
  role: {
    type: String,
    enum: ['SOURCE', 'DESTINATION', 'DUAL'],
    default: 'SOURCE'
  },
  publicKeyPem: {
    type: String,
    default: null
  },
  status: {
    type: String,
    enum: ['ONLINE', 'OFFLINE'],
    default: 'ONLINE'
  },
  lastSeen: {
    type: Date,
    default: Date.now
  },
  messageCount: {
    type: Number,
    default: 0
  }
}, {
  timestamps: true
});

export default mongoose.model('Device', deviceSchema);

