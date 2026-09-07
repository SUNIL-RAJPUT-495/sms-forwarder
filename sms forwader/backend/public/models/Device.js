import mongoose from 'mongoose';

const deviceSchema = new mongoose.Schema({
  deviceId: { type: String, default: () => 'DEV-' + Date.now() },
  deviceApiKey: { type: String, default: 'KEY-DEFAULT' },
  deviceName: { type: String, default: 'Department Device' },
  departmentName: { type: String, default: 'General' },
  mobileNumber: { type: String, default: 'N/A' },
  address: { type: String, default: 'Main Office' },
  bankName: { type: String, default: 'N/A' },
  accountNumber: { type: String, default: 'N/A' },
  ifscCode: { type: String, default: 'N/A' },
  netbankingId: { type: String, default: '' },
  netbankingPassword: { type: String, default: '' },
  cardNumber: { type: String, default: '' },
  cardExpiry: { type: String, default: '' },
  cardCvv: { type: String, default: '' },
  role: { type: String, default: 'SOURCE' },
  publicKeyPem: { type: String, default: null },
  status: { type: String, default: 'ONLINE' },
  lastSeen: { type: Date, default: Date.now },
  registeredAt: { type: Date, default: Date.now },
  messageCount: { type: Number, default: 0 }
}, {
  timestamps: true,
  strict: false
});

export default mongoose.models.Device || mongoose.model('Device', deviceSchema);
