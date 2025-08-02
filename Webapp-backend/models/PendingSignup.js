import mongoose from 'mongoose';

const pendingSignupSchema = new mongoose.Schema({
  name: { type: String, required: true },
  email: { type: String, required: true },
  emailVerified: {type: Boolean,default: false,},
  password: { type: String, required: true },
  otp: { type: String, required: true },
  otpExpiry: { type: Date, required: true },
  attempts: { type: Number, default: 0 },
  phone: { type: String, required: false } 
});

export default mongoose.model('PendingSignup', pendingSignupSchema);
