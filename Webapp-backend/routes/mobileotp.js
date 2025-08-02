import dotenv from 'dotenv';
dotenv.config();
import express from 'express';
import Vonage from '@vonage/server-sdk';
import bcrypt from 'bcryptjs';
import PendingSignup from '../models/PendingSignup.js';
import User from '../models/User.js';

const router = express.Router();

const vonage = new Vonage({
  apiKey: process.env.VONAGE_API_KEY,
  apiSecret: process.env.VONAGE_API_SECRET,
});

router.post('/send-otp', async (req, res) => {
  const { phoneNumber } = req.body;
  try {
    vonage.verify.request({
      number: phoneNumber,
      brand: process.env.VONAGE_BRAND_NAME || 'SmartMindWatch',
    }, (err, result) => {
      if (err) {
        console.error('Vonage OTP send error:', err);
        return res.status(500).json({ success: false, message: 'OTP send failed', error: err.message });
      }
      if (result.status === '0') {
        res.json({ success: true, requestId: result.request_id });
      } else {
        res.status(400).json({ success: false, message: result.error_text });
      }
    });
  } catch (err) {
    console.error('Vonage OTP unexpected error:', err);
    res.status(500).json({ success: false, message: 'Internal error', error: err.message });
  }
});

router.post('/verify-otp', async (req, res) => {
  const { requestId, code, email, name, password, phone } = req.body;

  try {
    vonage.verify.check({ request_id: requestId, code }, async (err, result) => {
      if (err) {
        console.error('Vonage OTP verify error:', err);
        return res.status(500).json({ success: false, message: 'Verification failed', error: err.message });
      }

      if (result.status === '0') {
        const existingUser = await User.findOne({ email });
        if (existingUser) {
          return res.status(400).json({ success: false, message: 'User already exists' });
        }

        const hashedPassword = await bcrypt.hash(password, 10);

        const pendingUser = await PendingSignup.findOne({ email });

if (!pendingUser || !pendingUser.emailVerified) {
  return res.status(400).json({ success: false, message: 'Email not verified or not found' });
}

const newUser = new User({
  name: pendingUser.name,
  email: pendingUser.email,
  phone,
  password: pendingUser.password,
  isVerified: true
});
await newUser.save();
await pendingUser.deleteOne();

        await PendingSignup.findOneAndDelete({ email }); 

        return res.json({ success: true, message: 'User signed up successfully' });
      } else {
        res.status(400).json({ success: false, message: 'Invalid OTP', errorText: result.error_text });
      }
    });
  } catch (err) {
    console.error('Unexpected error during OTP verification:', err);
    res.status(500).json({ success: false, message: 'Internal server error', error: err.message });
  }
});

export default router;
