import express from 'express';
import bcrypt from 'bcryptjs';
import rateLimit from 'express-rate-limit';
import nodemailer from 'nodemailer';
import dotenv from 'dotenv';
import PendingSignup from '../models/PendingSignup.js';
import User from '../models/User.js';

dotenv.config();

const router = express.Router();

const otpLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 1,
  message: { success: false, message: "Please wait 1 minute before requesting another OTP" }
});

const generateOTP = () => Math.floor(100000 + Math.random() * 900000).toString();

const transporter = nodemailer.createTransport({
  service: "Gmail",
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASSWORD,
  },
});

const sendOTPEmail = async (email, otp, name) => {
  const mailOptions = {
    from: `"SmartMindWatch" <${process.env.EMAIL_USER}>`,
    to: email,
    subject: "SmartMindWatch - Email Verification OTP",
    html: `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <h2 style="color: #333;">Email Verification</h2>
        <p>Hello ${name},</p>
        <p>Your OTP for SmartMindWatch account verification is:</p>
        <div style="background: #f0f0f0; padding: 20px; text-align: center; font-size: 24px; font-weight: bold; color: #333; margin: 20px 0;">
          ${otp}
        </div>
        <p><strong>This OTP is valid for 5 minutes only.</strong></p>
        <p>If you didn't request this, please ignore this email.</p>
        <p>Thanks,<br>SmartMindWatch Team</p>
      </div>
    `
  };

  await transporter.sendMail(mailOptions);
};

router.post('/request-email-otp', otpLimiter, async (req, res) => {
  try {
    console.log("Request body:", req.body);
    const { email, name, password } = req.body;
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!email || !name || !password) {
      return res.status(400).json({ success: false, message: "All fields are required" });
    }

    if (!emailRegex.test(email)) {
      return res.status(400).json({ success: false, message: "Invalid email format" });
    }

    const existingUser = await User.findOne({ email });
    if (existingUser) {
      return res.status(400).json({ success: false, message: "User already exists" });
    }

    const otp = generateOTP();
    const otpExpiry = new Date(Date.now() + 5 * 60 * 1000);
    const hashedPassword = await bcrypt.hash(password, 10);

    await PendingSignup.findOneAndDelete({ email });

    await new PendingSignup({
  email,
  name,
  password: hashedPassword,
  otp,
  otpExpiry
}).save();

    await sendOTPEmail(email, otp, name);
    res.json({ success: true, message: "OTP sent successfully" });

  } catch (error) {
    console.error("OTP request error:", error);
    res.status(500).json({ success: false, message: "Failed to process request" });
  }
});

router.post('/verify-email-otp', async (req, res) => {
  try {
    const { email, otp } = req.body;
    const pendingUser = await PendingSignup.findOne({ email });

    if (!pendingUser) return res.status(400).json({ success: false, message: "Invalid request" });

    if (Date.now() > pendingUser.otpExpiry) {
      await pendingUser.deleteOne();
      return res.status(400).json({ success: false, message: "OTP expired" });
    }

    if (pendingUser.attempts >= 3) {
      await pendingUser.deleteOne();
      return res.status(400).json({ success: false, message: "Too many attempts" });
    }

    if (pendingUser.otp !== otp) {
      pendingUser.attempts += 1;
      await pendingUser.save();
      return res.status(400).json({
        success: false,
        message: `Invalid OTP. ${3 - pendingUser.attempts} attempts remaining`
      });
    }

    pendingUser.emailVerified = true;
await pendingUser.save();

res.json({
  success: true,
  message: "Email verified successfully"
});


  } catch (error) {
    console.error("OTP verification error:", error);
    res.status(500).json({ success: false, message: "Verification failed" });
  }
});

router.post('/send-reset-otp', otpLimiter, async (req, res) => {
  try {
    const { email } = req.body;
    
    console.log('Sending reset OTP to:', email);
    
    const user = await User.findOne({ email });
    if (!user) {
      console.log('User not found for email:', email);
      return res.status(404).json({ 
        success: false, 
        message: "User not found" 
      });
    }

    const otp = generateOTP();
    const otpExpiry = new Date(Date.now() + 5 * 60 * 1000); 

    user.resetPasswordOTP = otp;
    user.resetPasswordExpires = otpExpiry;
    await user.save();

    console.log('Generated OTP:', otp, 'for email:', email);
    
    await sendPasswordResetEmail(email, otp, user.name);

console.log('Sending verification:', { email, otp });
    res.json({ 
      success: true, 
      message: "Password reset OTP sent successfully",
      otp: otp 
    });
  } catch (error) {
    console.error("Password reset OTP error:", error);
    res.status(500).json({ 
      success: false, 
      message: "Failed to send reset OTP" 
    });
  }
});

router.post('/verify-reset-otp', async (req, res) => {
  try {
    const { email, otp } = req.body;
    const userExists = await User.exists({ email });
    if (!userExists) {
      return res.status(400).json({
        success: false,
        message: "No account found with this email"
      });
    }
    const user = await User.findOne({
      email,
      resetPasswordExpires: { $gt: Date.now() }
    });
    if (!user) {
      return res.status(400).json({
        success: false,
        message: "OTP expired or invalid"
      });
    }
    if (user.resetPasswordOTP !== otp) {
      return res.status(400).json({
        success: false,
        message: "Invalid OTP code"
      });
    }
    user.resetPasswordVerified = true;
    await user.save();

    res.json({
      success: true,
      message: "OTP verified successfully"
    });
  } catch (error) {
    console.error("Verify OTP error:", error);
    res.status(500).json({ 
      success: false, 
      message: "OTP verification failed" 
    });
  }
});

router.get('/check-user/:email', async (req, res) => {
  const user = await User.findOne({ email: req.params.email });
  res.json({ exists: !!user });
});
router.post('/reset-password', async (req, res) => {
  try {
    const { email, newPassword, confirmPassword } = req.body;

    if (newPassword !== confirmPassword) {
      return res.status(400).json({ 
        success: false, 
        message: "Passwords do not match" 
      });
    }

    const user = await User.findOne({ 
      email,
      resetPasswordVerified: true,
      resetPasswordExpires: { $gt: Date.now() }
    });

    if (!user) {
      return res.status(400).json({ 
        success: false, 
        message: "Invalid request or OTP expired" 
      });
    }

    const salt = await bcrypt.genSalt(10);
    user.password = await bcrypt.hash(newPassword, salt);
    
    user.resetPasswordOTP = undefined;
    user.resetPasswordExpires = undefined;
    user.resetPasswordVerified = undefined;
    
    await user.save();

    res.json({ success: true, message: "Password reset successfully" });
  } catch (error) {
    console.error("Password reset error:", error);
    res.status(500).json({ success: false, message: "Password reset failed" });
  }
});

const sendPasswordResetEmail = async (email, otp, name) => {
  const mailOptions = {
    from: `"SmartMindWatch" <${process.env.EMAIL_USER}>`,
    to: email,
    subject: "SmartMindWatch - Password Reset OTP",
    html: `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <h2 style="color: #333;">Password Reset Request</h2>
        <p>Hello ${name},</p>
        <p>Your OTP for password reset is:</p>
        <div style="background: #f0f0f0; padding: 20px; text-align: center; font-size: 24px; font-weight: bold; color: #333; margin: 20px 0;">
          ${otp}
        </div>
        <p><strong>This OTP is valid for 5 minutes only.</strong></p>
        <p>If you didn't request this password reset, please ignore this email.</p>
        <p>Thanks,<br>SmartMindWatch Team</p>
      </div>
    `
  };

  await transporter.sendMail(mailOptions);
};

export default router;
