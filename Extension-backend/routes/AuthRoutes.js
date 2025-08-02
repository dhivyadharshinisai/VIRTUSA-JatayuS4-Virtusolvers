const express = require('express');
const router = express.Router();
const { OAuth2Client } = require('google-auth-library');
const bcrypt = require('bcrypt');
const User = require('../models/User'); 
const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

router.post('/login', async (req, res) => {
  console.log('Login route hit with body:', req.body);
  
  try {
    const { email, password } = req.body;
    console.log('User model type:', typeof User);
    console.log('User.findOne exists:', User.findOne ? 'YES' : 'NO');
    
    const user = await User.findOne({ email }).select('+password');
    if (!user) {
      console.log('User not found for email:', email);
      return res.status(400).json({ success: false, message: "User not found" });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      console.log('Password mismatch for user:', email);
      return res.status(401).json({ success: false, message: "Incorrect password" });
    }

    user.password = undefined;
    res.json({ success: true, user });
    
  } catch (err) {
    console.error("Full login error:", err);
    res.status(500).json({ 
      success: false, 
      message: "Server error",
      error: err.message,
      stack: err.stack 
    });
  }
});
router.post('/auth/google', async (req, res) => {
  const { idToken } = req.body;
  if (!idToken) return res.status(400).json({ success: false, message: "No token provided" });

  try {
    const googleRes = await fetch(`https://www.googleapis.com/oauth2/v2/userinfo?access_token=${idToken}`);
    if (!googleRes.ok) throw new Error(`Failed to fetch Google profile: ${googleRes.status}`);
    
    const profile = await googleRes.json();
    if (profile.error) throw new Error(profile.error.message);

    const { email } = profile;

    const user = await User.findOne({ email });
    if (!user) {
      return res.status(403).json({ 
        success: false, 
        message: "This Google account is not registered. Please register first." 
      });
    }
    res.json({ success: true, user });

  } catch (err) {
    console.error('Google auth error:', err);
    res.status(500).json({ success: false, message: 'Google authentication failed', error: err.message });
  }
});

module.exports = router;
