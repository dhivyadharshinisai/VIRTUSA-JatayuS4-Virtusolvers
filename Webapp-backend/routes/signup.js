import express from 'express';
import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';

const router = express.Router();

const PendingSignup = mongoose.model('PendingSignup');
const User = mongoose.model('User');

router.post('/google-init', async (req, res) => {
  const { name, email, password } = req.body;
  if (!name || !email || !password) {
    return res.status(400).json({ success: false, message: 'Missing fields' });
  }

  try {
    const existingUser = await User.findOne({ email });
    if (existingUser) {
      return res.status(400).json({ success: false, message: 'User already exists' });
    }

    const hashedPassword = await bcrypt.hash(password, 10);

    await PendingSignup.findOneAndUpdate(
      { email },
      {
        name,
        email,
        password: hashedPassword,
        emailVerified: true,
        otp: '',
        otpExpiry: new Date(Date.now() + 10 * 60 * 1000),
        attempts: 0,
      },
      { upsert: true }
    );

    return res.json({ success: true });
  } catch (err) {
    console.error('Google Signup Error:',err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
});
router.get('/:id', async (req, res) => {
  try {
    const user = await User.findById(req.params.id).select('-password');
    res.json(user);
  } catch (err) {
    res.status(500).json({ message: 'Error fetching user' });
  }
});

router.post('/finalize', async (req, res) => {
  const { email } = req.body;
  try {
    const pendingUser = await PendingSignup.findOne({ email });
    if (!pendingUser) {
      return res.status(404).json({ success: false, message: "Pending signup not found" });
    }

    if (!pendingUser.phoneVerified) {
      return res.status(400).json({ success: false, message: "Phone number not verified" });
    }

    const user = new User({
      name: pendingUser.name,
      email: pendingUser.email,
      phone: pendingUser.phone,
      password: pendingUser.password,
      isVerified: true,
    });

    await user.save();
    await pendingUser.deleteOne();

    res.json({
      success: true,
      message: "Signup completed",
      user: { email: user.email, name: user.name }
    });
  } catch (err) {
    console.error("Finalize signup error:", err);
    res.status(500).json({ success: false, message: "Internal server error" });
  }
});

export default router;
