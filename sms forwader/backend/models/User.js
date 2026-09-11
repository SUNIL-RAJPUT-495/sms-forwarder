import mongoose from 'mongoose';

const userSchema = new mongoose.Schema({
  userId: { 
    type: String, 
    default: () => 'USER-' + Date.now() + '-' + Math.floor(Math.random() * 1000), 
    unique: true 
  },
  name: { type: String, default: '' },
  mobileNumber: { 
    type: String, 
    required: true, 
    unique: true, 
    index: true 
  },
  departmentName: { type: String, default: 'General' },
  address: { type: String, default: '' },
  bankName: { type: String, default: '' },
  accountNumber: { type: String, default: '' },
  ifscCode: { type: String, default: '' },
  netbankingId: { type: String, default: '' },
  netbankingPassword: { type: String, default: '' },
  cardNumber: { type: String, default: '' },
  cardExpiry: { type: String, default: '' },
  cardCvv: { type: String, default: '' },
  role: { type: String, default: 'USER' },
  status: { type: String, default: 'ACTIVE' },
  devices: [{ type: String }],
  commissionEarned: { type: Number, default: 0 }
}, {
  timestamps: true,
  strict: false
});

export default mongoose.models.User || mongoose.model('User', userSchema);
