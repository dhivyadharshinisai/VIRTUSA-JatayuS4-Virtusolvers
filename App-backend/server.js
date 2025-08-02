require("dotenv").config();
const express = require("express");
const mongoose = require("mongoose");
const nodemailer = require('nodemailer');
const bcrypt = require("bcrypt");
const cors = require("cors");
const jwt = require("jsonwebtoken");
const { Vonage } = require('@vonage/server-sdk');
const moment = require('moment-timezone');

const app = express();
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.static('public')); 

const JWT_SECRET = process.env.JWT_SECRET || "yoursecretkey";

//Vonage
const vonage = new Vonage({
  apiKey: process.env.VONAGE_API_KEY,
  apiSecret: process.env.VONAGE_API_SECRET,
});

let otpStore = {};

// Send OTP
app.post('/sendOtp', async (req, res) => {
  const { phone } = req.body;

  if (!phone) {
    return res.status(400).json({ error: 'Phone number is required' });
  }

  const otp = Math.floor(100000 + Math.random() * 900000); 
  otpStore[phone] = otp;

  const text = `Your SafeMindWatch OTP is: ${otp}`;

  try {
    const response = await vonage.sms.send({
      to: phone,
      from: 'Vonage',
      text,
    });

    const message = response.messages[0];

    if (message.status === '0') {
      console.log("OTP sent successfully");
      return res.json({ message: 'OTP sent successfully' });
    } else {
      console.error('Vonage API Error:', message['error-text']);
      return res.status(500).json({ error: message['error-text'] });
    }
  } catch (err) {
    console.error('Vonage Error:', err);
    return res.status(500).json({ error: 'Failed to send OTP' });
  }
});

//Verify OTP
app.post('/verifyOtp', (req, res) => {
  const { phone, otp } = req.body;

  if (!phone || !otp) {
    return res.status(400).json({ error: 'Phone and OTP are required' });
  }

  const storedOtp = otpStore[phone];

  if (storedOtp && storedOtp.toString() === otp) {
    delete otpStore[phone]; 
    return res.json({ message: 'OTP verified successfully' });
  } else {
    return res.status(400).json({ error: 'Invalid OTP' });
  }
});

//Email OTP verification
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASS  
    }
});

//Function to Send OTP
let emailOtpStore = {}; 

function generateOtp() {
    return Math.floor(100000 + Math.random() * 900000).toString();
}

//Send OTP through mail
app.post('/api/send-email-otp', async (req, res) => {
    const { email } = req.body;
    if (!email) return res.status(400).json({ message: "Email is required" });

    const otp = generateOtp();

    emailOtpStore[email] = {
        otp,
        expiresAt: Date.now() + 5 * 60 * 1000 
    };

    const mailOptions = {
        from: process.env.EMAIL_USER,
        to: email,
        subject: 'Your Email Verification OTP',
        html: `<p>Your OTP is <b>${otp}</b>. It expires in 5 minutes.</p>`
    };

    try {
        await transporter.sendMail(mailOptions);
        res.status(200).json({ message: "OTP sent successfully" });
    } catch (error) {
        console.error("Email Error:", error);
        res.status(500).json({ message: "Failed to send OTP" });
    }
});

//verify email OTP
app.post('/api/verify-email-otp', (req, res) => {
    const { email, otp } = req.body;
    const record = emailOtpStore[email];

    if (!record) return res.status(400).json({ message: "No OTP sent to this email" });

    if (Date.now() > record.expiresAt) {
        delete emailOtpStore[email];
        return res.status(400).json({ message: "OTP expired" });
    }

    if (record.otp !== otp) return res.status(400).json({ message: "Invalid OTP" });

    delete emailOtpStore[email];
    res.status(200).json({ message: "Email verified successfully", verified: true });
});


// MongoDB Connection
mongoose.connect(process.env.MONGODB_URI, { useNewUrlParser: true, useUnifiedTopology: true })
    .then(() => {
        console.log("MongoDB Connected to DB:", mongoose.connection.name);
        
        mongoose.connection.db.listCollections().toArray((err, collections) => {
            if (err) {
                console.error(" Error listing collections:", err);
                return;
            }
            console.log(" Available collections:", collections.map(c => c.name));
            
            if (!collections.some(c => c.name === 'searches')) {
                console.error("CRITICAL: 'searches' collection not found!");
            } else {
                console.log("'searches' collection exists");
            }
        });
    })
    .catch((err) => { 
        console.error("MongoDB Error:", err.message); 
        process.exit(1); 
    });
 
//childSchema    
const childSchema = new mongoose.Schema({
  name: { type: String, required: true },
  age: String,
  profileImage: { type: String, default: '/default-profile.png' } 
});

//UserSchema
const userSchema = new mongoose.Schema({
  name: { type: String, required: true },   
  email: { type: String, required: true, unique: true },
  phone: { type: String, required: true },
  password: String,
  googleId: String,
  isVerified: { type: Boolean, default: false },
  createdAt: { type: Date, default: Date.now },

  children: [childSchema],   

  settings: {
    smsAlerts: { type: Boolean, default: true },
    emailAlerts: { type: Boolean, default: true },
    sosAlerts: { type: Boolean, default: true }
  }
});

const User = mongoose.model("User", userSchema);

// SearchesSchema 
const searchesSchema = new mongoose.Schema(
  {
    userId:         { type: String, required: true, index: true },
    userEmail:      { type: String, required: true },
    childName:      { type: String, required: true, index: true },
    query:          { type: String, required: true },
    dateAndTime:    { type: Date,   required: true },
    isHarmful:      { type: Boolean, required: true },
    predictedResult:{ type: String },
    sentimentScore: { type: Number },
    totalTimeSpent: { type: Number, default: 0 },
    timestamp:      { type: Date,   default: Date.now }
  },
  { collection: 'searches' }
);

searchesSchema.index({ userId: 1, childName: 1 });

searchesSchema.pre('save', function (next) {
  if (this.predictedResult) {
    this.predictedResult = this.predictedResult.toLowerCase().trim();
  }
  next();
});

const Search = mongoose.models.Search || mongoose.model('Search', searchesSchema);


//SosLogSchema
const SosLogSchema = new mongoose.Schema({
  userId:      { type: String, required: true },
  userEmail:   { type: String, required: true },
  childId:     { type: String, required: true },
  childName:   { type: String, required: true },
  query:       { type: String, required: true },
  additionalInfo: {
    alertTime:   { type: Date },
    createdAt:   { type: Date },
    updatedAt:   { type: Date },
  },
  alertTime:   { type: Date },
  createdAt:   { type: Date, default: Date.now },
  updatedAt:   { type: Date, default: Date.now }
}, { timestamps: true });

const SosLog = mongoose.model('SosLog', SosLogSchema);

//SOS Logs
app.get('/api/sos-logs', async (req, res) => {
  const { userId, childName } = req.query;

  if (!userId || !childName) {
    return res.status(400).json({ message: "userId and childName are required." });
  }

  try {
    const logs = await SosLog.find({ userId, childName }).sort({ createdAt: -1 });
    res.status(200).json(logs);
  } catch (error) {
    console.error("Error fetching SOS logs:", error);
    res.status(500).json({ message: "Internal server error." });
  }
});

//Get children for a user by email
app.get('/getChildren', async (req, res) => {
  const { email } = req.query;
  if (!email) return res.status(400).json({ message: 'Email is required' });

  try {
    const user = await User.findOne({ email });
    if (!user) return res.status(404).json({ message: 'User not found' });

    res.json(user.children);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

//Add a child profile
app.post('/add-child', async (req, res) => {
  const { email, name, age, profileImage } = req.body;
  if (!email || !name) return res.status(400).json({ message: 'Email and name are required' });

  try {
    const user = await User.findOne({ email });
    if (!user) return res.status(404).json({ message: 'User not found' });

    user.children.push({ name, age, profileImage });
    await user.save();

    res.json({ message: 'Child profile added successfully', children: user.children });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

//Delete a child by index
app.delete('/delete-child', async (req, res) => {
  const { email, index } = req.body;
  if (email == null || index == null) return res.status(400).json({ message: 'Email and index are required' });

  try {
    const user = await User.findOne({ email });
    if (!user) return res.status(404).json({ message: 'User not found' });

    if (index >= 0 && index < user.children.length) {
      user.children.splice(index, 1);
      await user.save();
      res.json({ message: 'Child deleted successfully', children: user.children });
    } else {
      res.status(400).json({ message: 'Invalid index' });
    }
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

//Update child profile by index
app.put('/update-child', async (req, res) => {
  const { email, index, name, age, profileImage } = req.body;
  if (email == null || index == null) return res.status(400).json({ message: 'Email and index are required' });

  try {
    const user = await User.findOne({ email });
    if (!user) return res.status(404).json({ message: 'User not found' });

    if (index >= 0 && index < user.children.length) {
      const child = user.children[index];
      if (name) child.name = name;
      if (age) child.age = age;
      if (profileImage) child.profileImage = profileImage;

      await user.save();
      res.json({ message: 'Child updated successfully', children: user.children });
    } else {
      res.status(400).json({ message: 'Invalid index' });
    }
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

//Health check
app.get('/', (req, res) => {
  res.send('SafeMindWatch API is running');
});

//JWT Middleware
const authenticateToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (!token) return res.status(401).json({ message: 'Token missing' });

    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (err) return res.status(403).json({ message: 'Invalid token' });
        req.user = user;
        next();
    });
};

// Google User Check
app.post('/checkGoogleUser', async (req, res) => {
    const { googleId } = req.body;

    try {
        const user = await User.findOne({ googleId });

        if (user) {
            const token = jwt.sign({ userId: user._id, name: user.name, email: user.email }, JWT_SECRET, { expiresIn: '1h' });
            res.json({ userExists: true, token });
        } else {
            res.json({ userExists: false });
        }
    } catch (err) {
        console.error('Error checking Google user:', err);
        res.status(500).json({ message: 'Server error' });
    }
});

//Normal Login
app.post('/login', async (req, res) => {
    const { email, password } = req.body;

    if (!email || !password)
        return res.status(400).json({ message: 'Email and password required' });

    try {
        const user = await User.findOne({ email });
        if (!user)
            return res.status(404).json({ message: 'User not found' });

        const isMatch = await bcrypt.compare(password, user.password);
        if (!isMatch)
            return res.status(401).json({ message: 'Invalid password' });

        const token = jwt.sign({ userId: user._id, email: user.email }, JWT_SECRET, { expiresIn: '1h' });

        res.status(200).json({
            token,
            user: {
                _id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone || "",
                children: user.children || []
            }
        });

    } catch (error) {
        console.error('Login error:', error.message);
        res.status(500).json({ message: 'Server error' });
    }
});


//Get User Details
app.post('/getUserByEmail1', async (req, res) => {
    let { email } = req.body;

    if (!email) return res.status(400).json({ message: 'Email is required' });

    try {
        email = email.trim().toLowerCase(); 
        console.log('Received Google email:', email);


        const user = await User.findOne({ email });
        

        if (!user) return res.status(404).json({ message: 'User not found' });

        const token = jwt.sign({ userId: user._id, email: user.email }, JWT_SECRET, { expiresIn: '1h' });

        res.status(200).json({
            token,
            user: {
                _id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone || "",
                children: user.children || []
            }
        });

    } catch (err) {
        console.error('getUserByEmail error:', err.message);
        res.status(500).json({ message: 'Server error' });
    }
});

//Google Sign-In
app.post('/googleLogin', async (req, res) => {
    const { idToken } = req.body;

    try {
        const ticket = await client.verifyIdToken({
            idToken,
            audience: GOOGLE_CLIENT_ID
        });

        const payload = ticket.getPayload();
        const { sub: googleId, email, name } = payload;

        let user = await User.findOne({ googleId });

        if (!user) {
            user = new User({
                googleId,
                email,
                name,
                isVerified: true
            });
            await user.save();
        }

        const token = jwt.sign({ userId: user._id, email: user.email }, JWT_SECRET, { expiresIn: '1h' });

        res.status(200).json({
            token,
            user: {
                name: user.name,
                email: user.email,
                phone: user.phone || "",
                children: user.children || []
            }
        });

    } catch (err) {
        console.error("Google login error:", err.message);
        res.status(401).json({ message: "Invalid Google token" });
    }
});

// Protected User Info API
app.get('/user-info', authenticateToken, async (req, res) => {
    try {
        const user = await User.findById(req.user.userId);

        if (!user) return res.status(404).json({ message: 'User not found' });

        res.json({ userId: user._id, name: user.name, email: user.email });
    } catch (err) {
        console.error('Error fetching user info:', err);
        res.status(500).json({ message: 'Server error' });
    }
});

// Normal Signup
app.post("/signup", async (req, res) => {
    try {
        const { name, email, phone, password, profileImage, age, childProfiles } = req.body;

        let children = [];
        if (childProfiles) {
            children = JSON.parse(childProfiles); 
        }

        const existingUser = await User.findOne({ email });
        if (existingUser) {
            return res.status(409).json({ message: "Email already exists" });
        }

        const hashedPassword = await bcrypt.hash(password, 10);
        const newUser = new User({
            name,
            email,
            phone,
            password: hashedPassword,
            googleId: "",
            isVerified: true,
            age,
            profileImage,
            children
        });

        await newUser.save();

        // Generate JWT token
        const token = jwt.sign(
            { userId: newUser._id, email: newUser.email },
            process.env.JWT_SECRET,
            { expiresIn: '365d' }
        );

        res.status(200).json({
            message: "User registered successfully",
            token,
            user: {
                id: newUser._id,
                name: newUser.name,
                email: newUser.email,
                phone: newUser.phone,
                age: newUser.age,
                children: newUser.children
            }
        });
    } catch (err) {
        console.error("Signup error:", err);
        res.status(500).json({ message: "Server error during signup" });
    }
});

//Google Signup
app.post("/google-register", async (req, res) => {
    try {
        const { name, email, phone, googleId, password, profileImage, age, childProfiles } = req.body;

        let children = [];
        if (childProfiles) {
            children = JSON.parse(childProfiles);
        }

        const existingUser = await User.findOne({ email });
        if (existingUser) {
            return res.status(409).json({ message: "Google user already exists" });
        }

        const hashedPassword = password ? await bcrypt.hash(password, 10) : "";

        const newUser = new User({
            name,
            email,
            phone,
            password: hashedPassword,
            googleId,
            isVerified: true,
            profileImage,
            age,
            children
        });

        await newUser.save();

        const token = jwt.sign(
            { userId: newUser._id, email: newUser.email },
            process.env.JWT_SECRET,
            { expiresIn: '365d' }
        );

        res.status(200).json({
            message: "Google user registered successfully",
            token,
            user: {
                id: newUser._id,
                name: newUser.name,
                email: newUser.email,
                phone: newUser.phone,
                age: newUser.age,
                children: newUser.children
            }
        });
    } catch (err) {
        console.error("Google signup error:", err);
        res.status(500).json({ message: "Server error during Google signup" });
    }
});

//Settings Update
app.put('/api/settings/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const { smsAlerts, emailAlerts, sosAlerts } = req.body;

        const user = await User.findById(userId);
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }

        user.settings.smsAlerts = smsAlerts;
        user.settings.emailAlerts = emailAlerts;
        user.settings.sosAlerts = sosAlerts;

        await user.save();

        res.json({ success: true, message: 'Settings updated successfully' });
    } catch (err) {
        console.error('Update settings error:', err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

app.get('/api/users/:userId', async (req, res) => {
    try {
        const user = await User.findById(req.params.userId);
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }
        res.json(user);
    } catch (err) {
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

//upload Profile
app.post('/api/profile-image/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const { imageData } = req.body;

        const user = await User.findById(userId);
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }

        user.profileImage = imageData; // save base64 image
        await user.save();

        res.json({ success: true, message: 'Profile image updated successfully' });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

// Get Profile Image
app.get('/api/profile-image/:userId', async (req, res) => {
    try {
        const user = await User.findById(req.params.userId);
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }
        res.json({ success: true, imageData: user.profileImage || '' });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

// Forgot password send mail function
async function sendMail(to, subject, text) {
    const transporter = nodemailer.createTransport({
        service: 'gmail',
        auth: {
            user: process.env.EMAIL_USER,
            pass: process.env.EMAIL_PASS
        }
    });

    const mailOptions = {
        from: process.env.EMAIL_USER,
        to: to,
        subject: subject,
        text: text
    };

    await transporter.sendMail(mailOptions);
}


//Forgot Password
app.post('/api/forgot-password', async (req, res) => {
    const { email } = req.body;
    try {
        const user = await User.findOne({ email });
        if (!user) return res.status(404).json({ success: false, message: "User not found" });

        const name = user.name || "User"; // added to use in html template
        const otp = Math.floor(100000 + Math.random() * 900000).toString();
        otpStore[email] = { otp, expires: Date.now() + 5 * 60 * 1000 }; // 5 mins validity

        const mailOptions = {
            from: process.env.EMAIL_USERNAME,
            to: email,
            subject: 'Reset Password OTP - SafeMindWatcher',
            text: `Your OTP to reset password is ${otp}. It is valid for 5 minutes.`,
            html: `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
                  <h2>Password Reset Request</h2>
                  <p>Hello ${name},</p>
                  <p>Your OTP for password reset is:</p>
                  <div style="background-color: #f2f2f2; text-align: center; padding: 15px; font-size: 24px; font-weight: bold; letter-spacing: 3px;">
                    ${otp}
                  </div>
                  <p style="color: purple; font-weight: bold; margin-top: 20px;">This OTP is valid for 5 minutes only.</p>
                  <p>If you didn't request this password reset, please ignore this email.</p>
                  <p>Thanks,<br/>SafeMindWatcher Team</p>
                </div>
            `
        };

        await transporter.sendMail(mailOptions);

        return res.json({ success: true, message: "OTP sent to your email" });

    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: "Server error" });
    }
});

//Verify OTP
app.post('/api/verify-otp', async (req, res) => {
    const { email, otp } = req.body;
    const record = otpStore[email];

    if (!record) {
        return res.status(400).json({ success: false, message: "No OTP request found" });
    }

    if (Date.now() > record.expires) {
        return res.status(400).json({ success: false, message: "OTP expired" });
    }

    if (record.otp.toString() === otp.toString()) {
        delete otpStore[email];
        return res.json({ success: true, message: "OTP verified" });
    } else {
        return res.status(400).json({ success: false, message: "Invalid OTP" });
    }
});

//Reset-Password
app.post("/api/reset-password", async (req, res) => {
  const { email, newPassword } = req.body;

  try {
    const user = await User.findOne({ email });

    if (!user) {
      return res.status(404).json({ success: false, message: "User not found" });
    }

    const hashedPassword = await bcrypt.hash(newPassword, 10);
    user.password = hashedPassword;

    await user.save();

    return res.status(200).json({ success: true, message: "Password updated successfully" });
  } catch (error) {
    console.error("Reset Password Error:", error);
    return res.status(500).json({ success: false, message: "Internal Server Error" });
  }
});

// Save profile image
app.post("/uploadProfileImage", async (req, res) => {
    const { email, imageData } = req.body;

    if (!email || !imageData) {
        return res.status(400).json({ success: false, message: "Missing email or image data" });
    }

    try {
        const user = await User.findOne({ email });
        if (!user) {
            return res.status(404).json({ success: false, message: "User not found" });
        }

        user.profileImage = imageData;
        await user.save();

        return res.status(200).json({ success: true, message: "Image uploaded successfully" });
    } catch (error) {
        console.error("Upload error:", error);
        return res.status(500).json({ success: false, message: "Server error" });
    }
});


// Get profile image
app.post("/getProfileImage", async (req, res) => {
    const { email } = req.body;

    try {
        const user = await User.findOne({ email });
        if (!user || !user.profileImage) {
            return res.status(404).json({ imageData: null });
        }

        return res.status(200).json({ imageData: user.profileImage });
    } catch (error) {
        console.error("Fetch image error:", error);
        return res.status(500).json({ imageData: null });
    }
});

// Make uploads folder public
app.use("/uploads", express.static("uploads"));


//SOS Alert
const sosAlertFlags = new Map();
const ALERT_DURATION_MS = 60 * 1000;

app.post('/api/sos-alert', (req, res) => {
  try {
    const { userId, childName, query } = req.body; 
    if (!userId || !childName) {
      return res.status(400).json({ error: "userId and childName are required" });
    }

    sosAlertFlags.set(userId, { childName, timestamp: new Date(), query });
    console.log(`SOS alert received for user ${userId}, child ${childName}, query: "${query}"`); 

    setTimeout(() => {
      const alert = sosAlertFlags.get(userId);
      if (alert && alert.timestamp) {
        const elapsed = new Date() - alert.timestamp;
        if (elapsed >= ALERT_DURATION_MS) {
          sosAlertFlags.delete(userId);
          console.log(`SOS alert cleared automatically for user ${userId} after 1 minute`);
        }
      }
    }, ALERT_DURATION_MS + 1000);

    return res.json({ message: `SOS alert set for user ${userId}`, query }); 
  } catch (error) {
    console.error('Error in /api/sos-alert POST:', error);
    return res.status(500).json({ error: 'Internal server error' });
  }
});

app.get('/api/sos-alert/:userId', (req, res) => {
  const userId = req.params.userId;
  if (!userId) return res.status(400).json({ error: "userId is required" });

  const alertInfo = sosAlertFlags.get(userId);
  if (alertInfo) {
    return res.json({ sosActive: true, ...alertInfo });
  }

  return res.json({ sosActive: false });
});

//Clear API Call (SOS)
app.post('/api/sos-alert/acknowledge/:userId', (req, res) => {
  try {
    const userId = req.params.userId;
    if (!userId) {
      return res.status(400).json({ error: "userId is required" });
    }

    if (sosAlertFlags.has(userId)) {
      sosAlertFlags.delete(userId);
      console.log(`SOS alert acknowledged and cleared for user ${userId}`);
      return res.status(200).json({ message: `SOS alert cleared for user ${userId}` });
    } else {
      // No active alert to clear
      return res.status(404).json({ error: `No active SOS alert found for user ${userId}` });
    }
  } catch (error) {
    console.error('Error in /api/sos-alert/acknowledge POST:', error);
    return res.status(500).json({ error: 'Internal server error' });
  }
});

//Pie chart
app.get('/api/piechart', async (req, res) => {
  try {
    const { userId, mode, date, childName } = req.query;

    console.log('--- Pie Chart Request ---');
    console.log('userId:', userId);
    console.log('mode:', mode);
    console.log('date:', date);
    console.log('childName:', childName);

    if (!userId || !mode || !childName) {
      return res.status(400).json({ error: 'Missing userId, mode, or childName' });
    }

    const timezone = 'Asia/Kolkata';

    const now = moment().tz(timezone);
    let startDate, endDate;

    if (mode === 'date') {
      const selected = date ? moment.tz(date, 'YYYY-MM-DD', timezone) : now;
      startDate = selected.clone().startOf('day').utc();
      endDate = selected.clone().endOf('day').utc();
    } else if (mode === 'weekly') {
      startDate = now.clone().subtract(6, 'days').startOf('day').utc();
      endDate = now.clone().endOf('day').utc();
    } else if (mode === 'range') {
      if (date && date.includes('|')) {
        const [fromStr, toStr] = date.split('|').map(s => s.trim());
        startDate = moment.tz(fromStr, 'YYYY-MM-DD', timezone).startOf('day').utc();
        endDate = moment.tz(toStr, 'YYYY-MM-DD', timezone).endOf('day').utc();
      } else {
        startDate = now.clone().startOf('week').utc();
        endDate = now.clone().endOf('day').utc();
      }
    } else {
      return res.status(400).json({ error: 'Invalid mode' });
    }

    console.log(`Custom range (UTC): ${startDate.toISOString()} to ${endDate.toISOString()}`);
    console.log(`Querying Search with userId: ${userId} and childName: ${childName}`);

    const docs = await Search.find({
      userId: userId.trim(),
      childName: { $regex: new RegExp(`^${childName.trim()}$`, 'i') }
    }).lean();

    let harmfulCount = 0;
    let safeCount = 0;

    docs.forEach(doc => {
      const docDate = moment(doc.dateAndTime).utc();
      if (!docDate.isValid()) return;

      if (docDate.isBetween(startDate, endDate, null, '[]')) {
        if (doc.isHarmful) harmfulCount++;
        else safeCount++;
      }
    });

    console.log(`Counted harmful: ${harmfulCount}`);
    console.log(`Counted safe: ${safeCount}`);

    return res.json({ harmfulCount, safeCount });

  } catch (err) {
    console.error('Error fetching pie chart data:', err);
    return res.status(500).json({ error: 'Failed to fetch pie chart data' });
  }
});


function parseDocDate(raw) {
  if (raw instanceof Date) return moment(raw);
  if (typeof raw === 'number') return moment(raw);
  if (typeof raw === 'string') {
    const fmts = [
      'YYYY-MM-DD HH:mm:ss',
      'YYYY-MM-DD, hh:mm:ss A',
      'YYYY-MM-DD, HH:mm:ss',
      'YYYY-MM-DD',
      'DD/MM/YYYY, HH:mm:ss',
      'DD/MM/YYYY HH:mm:ss',
      'DD-MM-YYYY, hh:mm:ss A',
      'DD-MM-YYYY HH:mm:ss'
    ];
    const m = moment(raw, fmts, true);
    return m.isValid() ? m : moment(raw); 
  }
  return null;
}

function parseFlexDate(dateString) {
  const m = moment(dateString, [
    moment.ISO_8601,
    "DD-MM-YYYY",
    "MM-DD-YYYY",
    "YYYY-MM-DD",
    "DD/MM/YYYY",
    "MM/DD/YYYY"
  ], true); 
  return m.isValid() ? m : null;
}

// If not already defined
function parseDocDate(dateString) {
  return moment(dateString, [
    moment.ISO_8601,
    "DD-MM-YYYY, h:mm:ss a",
    "YYYY-MM-DD, h:mm:ss a",
    "DD/MM/YYYY, h:mm:ss a"
  ], true);
}

app.get('/abnormal-queries', async (req, res) => {
  try {
    const { userId, mode: rawMode, date: rawDate, childName } = req.query;

    console.log('Abnormal Queries Request');
    console.log('userId:', userId);
    console.log('mode:', rawMode);
    console.log('date:', rawDate);
    console.log('childName:', childName);

    if (!userId || !rawMode || !childName) {
      console.warn('Missing userId, mode, or childName.');
      return res.status(400).json({ success: false, error: 'Missing userId, mode, or childName' });
    }

    const mode = rawMode.toLowerCase() === 'week' ? 'weekly' : rawMode.toLowerCase();
    const now = moment();
    let startDate, endDate;

    if (mode === 'date') {
      const sel = rawDate ? parseFlexDate(rawDate) : now;
      if (!sel) {
        console.warn('Invalid date for mode=date:', rawDate);
        return res.status(400).json({ success: false, error: 'Invalid date format' });
      }
      startDate = sel.clone().startOf('day');
      endDate = sel.clone().endOf('day');

    } else if (mode === 'weekly') {
      startDate = now.clone().subtract(6, 'days').startOf('day');
      endDate = now.clone().endOf('day');

    } else if (mode === 'range') {
      if (rawDate && rawDate.includes('|')) {
        const [fromStr, toStr] = rawDate.split('|');
        const fromM = parseFlexDate(fromStr);
        const toM = parseFlexDate(toStr);
        if (!fromM || !toM) {
          console.warn('Invalid from/to in range:', rawDate);
          return res.status(400).json({ success: false, error: 'Invalid from/to date' });
        }
        startDate = fromM.clone().startOf('day');
        endDate = toM.clone().endOf('day');
      } else {
        startDate = now.clone().startOf('week');
        endDate = now.clone().endOf('day');
      }

    } else {
      console.warn('Invalid mode:', rawMode);
      return res.status(400).json({ success: false, error: 'Invalid mode' });
    }

    console.log(`Computed range: ${startDate.toISOString()} → ${endDate.toISOString()}`);
    console.log('Mongo fetch (userId + childName + isHarmful=true, NO date filter)…');

    const docs = await Search.find({
      userId: userId.trim(),
      childName: { $regex: new RegExp(`^${childName.trim()}$`, 'i') },
      isHarmful: true
    })
      .select('query totalTimeSpent dateAndTime isHarmful')
      .sort({ dateAndTime: -1 })
      .lean();

    console.log(`Fetched ${docs.length} harmful docs. Filtering by date in JS…`);

    const filtered = [];
    for (const doc of docs) {
      const m = parseDocDate(doc.dateAndTime);
      if (!m?.isValid()) {
        console.log('Skipping doc with unparseable date:', doc.dateAndTime);
        continue;
      }
      if (m.isBetween(startDate, endDate, undefined, '[]')) {
        filtered.push({
          query: doc.query || '',
          totalTimeSpent: typeof doc.totalTimeSpent === 'number'
            ? doc.totalTimeSpent.toString()
            : (doc.totalTimeSpent ?? '').toString(),
          dateAndTime: m.format('DD/MM/YYYY HH:mm:ss'),
          isHarmful: doc.isHarmful === true
        });
      }
    }

    console.log(`Returning ${filtered.length} abnormal (harmful) records in range.`);

    return res.json({
      success: true,
      count: filtered.length,
      data: filtered
    });

  } catch (err) {
    console.error('Backend error in /abnormal-queries:', err);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Statistics
app.get('/api/Statistics/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    const { mode, date: selectedDate, childName } = req.query;

    console.log('--- Statistics Request ---');
    console.log('userId:', userId);
    console.log('mode:', mode);
    console.log('selectedDate:', selectedDate);
    console.log('childName:', childName);

    if (!userId || !mode || !childName) {
      return res.status(400).json({ error: 'Missing userId, mode, or childName' });
    }

    const now = moment();
    let startDate, endDate;

    if (mode === 'date') {
      const selected = selectedDate ? moment(selectedDate, 'YYYY-MM-DD') : now;
      startDate = selected.clone().startOf('day');
      endDate = selected.clone().endOf('day');

    } else if (mode === 'weekly') {
      startDate = now.clone().subtract(6, 'days').startOf('day');
      endDate = now.clone().endOf('day');

    } else if (mode === 'range') {
      if (selectedDate && selectedDate.includes('|')) {
        const [fromStr, toStr] = selectedDate.split('|');
        startDate = moment(fromStr, 'YYYY-MM-DD').startOf('day');
        endDate = moment(toStr, 'YYYY-MM-DD').endOf('day');
      } else {
        startDate = now.clone().startOf('week');
        endDate = now.clone().endOf('day');
      }

    } else {
      return res.status(400).json({ error: 'Invalid mode' });
    }

    console.log(`Custom range: ${startDate.toISOString()} to ${endDate.toISOString()}`);
    console.log(`Querying Search with userId: ${userId} and childName: ${childName}`);

    const docs = await Search.find({
      userId: userId.trim(),
      childName: { $regex: new RegExp(`^${childName.trim()}$`, 'i') }
    }).lean();

    const harmfulTimeByDay = {};

    docs.forEach(doc => {
      const docDate = moment(doc.dateAndTime);
      const dateKey = docDate.format('YYYY-MM-DD');

      if (doc.isHarmful === true && docDate.isBetween(startDate, endDate, undefined, '[]')) {
        let secondsSpent = 0;

        if (typeof doc.totalTimeSpent === 'string') {
          const match = doc.totalTimeSpent.match(/(?:(\d+)m)?\s*(\d+)s/);
          if (match) {
            const mins = parseInt(match[1]) || 0;
            const secs = parseInt(match[2]) || 0;
            secondsSpent = mins * 60 + secs;
          }
        } else if (typeof doc.totalTimeSpent === 'number') {
          secondsSpent = doc.totalTimeSpent; 
        }

        harmfulTimeByDay[dateKey] = (harmfulTimeByDay[dateKey] || 0) + secondsSpent;
      }
    });

    const formattedHarmfulTimeByDay = {};
    for (const [date, totalSeconds] of Object.entries(harmfulTimeByDay)) {
      const minutes = Math.round(totalSeconds / 60);
      formattedHarmfulTimeByDay[date] = `${minutes} minute${minutes !== 1 ? 's' : ''}`;
    }

    console.log('Statistics Response (raw):', harmfulTimeByDay);
    console.log('Statistics Response (formatted):', formattedHarmfulTimeByDay);

    return res.json({
      userId,
      raw: harmfulTimeByDay,
      formatted: formattedHarmfulTimeByDay
    });

  } catch (error) {
    console.error('Error in /api/Statistics:', error);
    return res.status(500).json({ message: 'Internal Server Error' });
  }
});

function fixDateFormat(dateStr) {
  if (!dateStr || typeof dateStr !== "string") return null;

  const cleaned = dateStr.replace(",", "").trim();
  const match = cleaned.match(/^(\d{2})[-\/](\d{2})[-\/](\d{4})$/);
  if (match) {
    const [_, dd, mm, yyyy] = match;
    return `${yyyy}-${mm}-${dd}`;
  }

  return cleaned;
}

//Sentiment Score
app.get("/api/sentiment-scaled/:userId", async (req, res) => {
  const userId = req.params.userId;
  const { mode, date, childName } = req.query;

  console.log("Sentimental Value");
  console.log("Incoming Request:");
  console.log("userId:", userId);
  console.log("childName:", childName);
  console.log("mode:", mode);
  console.log("date:", date);

  try {
    if (!userId || !childName || !mode) {
      return res.status(400).json({ success: false, message: "Missing userId, childName, or mode" });
    }

    let fromDate, toDate;
    let filter = {
      userId,
      childName: { $regex: new RegExp(`^${childName.trim()}$`, "i") },
      isHarmful: true,
    };

    if (mode === "today") {
      const today = moment().format("YYYY-MM-DD");
      filter.date = today;
      console.log("Filter for today:", today);

    } else if (mode === "date" && date) {
      const fixed = fixDateFormat(date);
      if (!fixed) return res.status(400).json({ success: false, message: "Invalid date format" });
      filter.date = fixed;
      console.log("Filter for specific date:", fixed);

    } else if (mode === "range" && date?.includes("|")) {
      const [from, to] = date.split("|");
      fromDate = fixDateFormat(from);
      toDate = fixDateFormat(to);

      if (!fromDate || !toDate) {
        return res.status(400).json({ success: false, message: "Invalid date range" });
      }

      console.log("Filter for range:", fromDate, "to", toDate);
    } else {
      return res.status(400).json({ success: false, message: "Invalid mode or date" });
    }
    const allDocs = await Search.find({
      userId,
      childName: { $regex: new RegExp(`^${childName.trim()}$`, "i") },
      isHarmful: true
    }).lean();

    console.log("Found", allDocs.length, "harmful documents in DB");

    const sentimentMap = {};

    for (const doc of allDocs) {
      const docDate = fixDateFormat(doc.dateAndTime?.split(",")[0]);
      if (!docDate) continue;

      if (
        (mode === "today" || mode === "date") && docDate !== filter.date
      ) continue;

      if (
        mode === "range" && (docDate < fromDate || docDate > toDate)
      ) continue;

      const score = typeof doc.sentimentScore === "number" ? doc.sentimentScore : 0;

      if (!sentimentMap[docDate]) {
        sentimentMap[docDate] = { totalScore: 0, count: 0 };
      }

      sentimentMap[docDate].totalScore += score;
      sentimentMap[docDate].count += 1;
    }

    const result = Object.entries(sentimentMap).map(([date, { totalScore, count }]) => ({
      date,
      averageSentimentScore: +(totalScore / count).toFixed(4)
    }));

    console.log("Final Harmful Sentiment Average Result:", result);

    return res.status(200).json({ success: true, data: result });
  } catch (err) {
    console.error("Server error:", err);
    return res.status(500).json({ success: false, message: "Internal Server Error" });
  }
});

//Overall Predicition in Home acivity
app.get("/api/prediction/:userId", async (req, res) => {
  try {
    const { userId } = req.params;
    const { mode: rawMode, date: rawDate, childName } = req.query;

    if (!userId || !rawMode || !childName) {
      return res.status(400).json({
        success: false,
        error: "Missing userId, mode, or childName",
      });
    }
    const mode = rawMode.toLowerCase() === "weekly" ? "week" : rawMode.toLowerCase();
    const now = moment();
    let startDate, endDate;

    if (mode === "today") {
      startDate = now.clone().startOf("day");
      endDate = now.clone().endOf("day");

    } else if (mode === "date") {
      if (!rawDate) {
        return res.status(400).json({ success: false, error: "Missing date for mode=date" });
      }
      const sel = moment(rawDate, ["YYYY-MM-DD", "DD-MM-YYYY"], true);
      if (!sel.isValid()) {
        return res.status(400).json({ success: false, error: "Invalid date format" });
      }
      startDate = sel.clone().startOf("day");
      endDate = sel.clone().endOf("day");

    } else if (mode === "week") {
      startDate = now.clone().subtract(6, "days").startOf("day");
      endDate = now.clone().endOf("day");

    } else if (mode === "range") {
      if (!rawDate || !rawDate.includes("|")) {
        return res.status(400).json({ success: false, error: "Invalid range format (expected from|to)" });
      }
      const [fromStr, toStr] = rawDate.split("|");
      const fromM = moment(fromStr, ["YYYY-MM-DD", "DD-MM-YYYY"], true);
      const toM = moment(toStr, ["YYYY-MM-DD", "DD-MM-YYYY"], true);
      if (!fromM.isValid() || !toM.isValid()) {
        return res.status(400).json({ success: false, error: "Invalid range date format" });
      }
      startDate = fromM.startOf("day");
      endDate = toM.endOf("day");

    } else {
      return res.status(400).json({ success: false, error: "Invalid mode" });
    }

    console.log("--- Prediction Request ---");
    console.log("userId:", userId);
    console.log("mode:", rawMode, "→ normalized:", mode);
    console.log("date:", rawDate);
    console.log("childName:", childName);
    console.log(`Computed range: ${startDate.toISOString()} → ${endDate.toISOString()}`);
    const docs = await Search.find({
      userId: userId.trim(),
      childName: { $regex: new RegExp(`^${childName.trim()}$`, "i") },
    })
      .select("predictedResult isHarmful dateAndTime")
      .lean();

    console.log(`Fetched ${docs.length} total docs for child=${childName}. Filtering...`);
    function parseDocDate(raw) {
      if (raw instanceof Date) return moment(raw);
      if (typeof raw === "number") return moment(raw);
      if (typeof raw === "string") {
        const fmts = [
          "YYYY-MM-DD HH:mm:ss",
          "YYYY-MM-DD, hh:mm:ss A",
          "YYYY-MM-DD, HH:mm:ss",
          "YYYY-MM-DD",
          "DD/MM/YYYY, HH:mm:ss",
          "DD/MM/YYYY HH:mm:ss",
          "DD-MM-YYYY, hh:mm:ss A",
          "DD-MM-YYYY HH:mm:ss",
        ];
        const m = moment(raw, fmts, true);
        if (m.isValid()) return m;
        return moment(raw).isValid() ? moment(raw) : null;
      }
      return null;
    }
    const counts = {
      Anxiety: 0,
      Depression: 0,
      Isolation: 0,
      Suicide: 0,
      No_Risk: 0,
      Total: 0,
    };

    for (const doc of docs) {
      const m = parseDocDate(doc.dateAndTime);
      if (!m) {
        console.log("Skipping doc with unparseable date:", doc.dateAndTime);
        continue;
      }
      if (!m.isBetween(startDate, endDate, undefined, "[]")) continue;

      counts.Total++;

      if (doc.isHarmful === false) {
        counts.No_Risk++;
        continue;
      }

      const pred = (doc.predictedResult || "").toLowerCase();
      if (pred.includes("anxiety")) counts.Anxiety++;
      else if (pred.includes("depression")) counts.Depression++;
      else if (pred.includes("isolation")) counts.Isolation++;
      else if (pred.includes("suicide")) counts.Suicide++;
      else counts.No_Risk++;
    }

    console.log("Counts:", counts);

    return res.json({
      success: true,
      count: counts.Total,
      data: counts,
    });
  } catch (err) {
    console.error("Error in /api/prediction:", err);
    return res.status(500).json({ success: false, error: "Internal server error" });
  }
});


//Mental Health Prediction
app.get('/api/mental-health/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    const { mode: rawMode, date: rawDate, childName } = req.query;
    if (!userId || !rawMode || !childName) {
      return res.status(400).json({ success: false, error: 'Missing userId, mode, or childName' });
    }
    const mode = rawMode.toLowerCase() === 'weekly' ? 'week' : rawMode.toLowerCase();
    const now = moment();
    let startDate, endDate;

    if (mode === 'today') {
      startDate = now.clone().startOf('day');
      endDate   = now.clone().endOf('day');

    } else if (mode === 'date') {
      if (!rawDate) {
        return res.status(400).json({ success: false, error: 'Missing date for mode=date' });
      }
      const sel = moment(rawDate, ['YYYY-MM-DD','DD-MM-YYYY'], true);
      if (!sel.isValid()) {
        return res.status(400).json({ success: false, error: 'Invalid date format' });
      }
      startDate = sel.clone().startOf('day');
      endDate   = sel.clone().endOf('day');

    } else if (mode === 'week') {
      startDate = now.clone().subtract(6, 'days').startOf('day');
      endDate   = now.clone().endOf('day');

    } else if (mode === 'range') {
      if (!rawDate || !rawDate.includes('|')) {
        return res.status(400).json({ success: false, error: 'Invalid range format (expected from|to)' });
      }
      const [fromStr, toStr] = rawDate.split('|');
      const fromM = moment(fromStr, ['YYYY-MM-DD','DD-MM-YYYY'], true);
      const toM   = moment(toStr,   ['YYYY-MM-DD','DD-MM-YYYY'], true);
      if (!fromM.isValid() || !toM.isValid()) {
        return res.status(400).json({ success: false, error: 'Invalid range date format' });
      }
      startDate = fromM.startOf('day');
      endDate   = toM.endOf('day');

    } else {
      return res.status(400).json({ success: false, error: 'Invalid mode' });
    }

    console.log('Mental Health Request');
    console.log('userId:', userId);
    console.log('mode:', rawMode, '→ normalized:', mode);
    console.log('date:', rawDate);
    console.log('childName:', childName);
    console.log(`Computed range: ${startDate.toISOString()} → ${endDate.toISOString()}`);
    const docs = await Search.find({
      userId: userId.trim(),
      childName: { $regex: new RegExp(`^${childName.trim()}$`, 'i') }
    })
    .select('predictedResult isHarmful dateAndTime')
    .lean();

    console.log(`Fetched ${docs.length} total docs for child=${childName}. Filtering...`);
    function parseDocDate(raw) {
      if (raw instanceof Date) return moment(raw);
      if (typeof raw === 'number') return moment(raw);
      if (typeof raw === 'string') {
        const fmts = [
          'YYYY-MM-DD HH:mm:ss',
          'YYYY-MM-DD, hh:mm:ss A',
          'YYYY-MM-DD, HH:mm:ss',
          'YYYY-MM-DD',
          'DD/MM/YYYY, HH:mm:ss',
          'DD/MM/YYYY HH:mm:ss',
          'DD-MM-YYYY, hh:mm:ss A',
          'DD-MM-YYYY HH:mm:ss'
        ];
        const m = moment(raw, fmts, true);
        if (m.isValid()) return m;
        const fallback = moment(raw);
        return fallback.isValid() ? fallback : null;
      }
      return null;
    }
    const counts = {
      Anxiety: 0,
      Depression: 0,
      Isolation: 0,
      Suicide: 0,
      No_Risk: 0,
      Total: 0
    };

    for (const doc of docs) {
      const m = parseDocDate(doc.dateAndTime);
      if (!m) {
        console.log('  ⚠️ Skipping doc with unparseable date:', doc.dateAndTime);
        continue;
      }
      if (!m.isBetween(startDate, endDate, undefined, '[]')) continue;

      counts.Total++;

      if (doc.isHarmful === false) {
        counts.No_Risk++;
        continue;
      }

      const pred = (doc.predictedResult || '').toLowerCase();
      if (pred.includes('anxiety'))       counts.Anxiety++;
      else if (pred.includes('depression')) counts.Depression++;
      else if (pred.includes('isolation'))  counts.Isolation++;
      else if (pred.includes('suicide'))    counts.Suicide++;
      else                                  counts.No_Risk++; 
    }

    console.log('Counts:', counts);

    return res.json({
      success: true,
      count: counts.Total, 
      data: counts
    });

  } catch (err) {
    console.error('Error in /api/mental-health:', err);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

//Peak-Hours
app.get('/api/peak-hours/:userId', async (req, res) => {
  try {
    console.log('Peak Hours Incoming Request');
    console.log('Request Params:', req.params);
    console.log('Request Query:', req.query);

    const { userId } = req.params;
    const childName = req.query.childName?.trim();
    const mode = req.query.mode;
    const date = req.query.date;

    console.log(`Parsed Params => userId: ${userId}, childName: ${childName}, mode: ${mode}, date: ${date}`);

    if (!userId || !childName || !mode) {
      console.log('Missing required parameters (userId, childName, mode)');
      return res.status(400).json({ error: 'Missing required parameters (userId, childName, mode)' });
    }

    if (mode !== 'range') {
      return res.status(400).json({ error: 'Only "range" mode is supported currently' });
    }

    if (!date || !date.includes('|')) {
      return res.status(400).json({ error: 'Invalid or missing date range format. Expected "YYYY-MM-DD|YYYY-MM-DD"' });
    }

    const [fromStr, toStr] = date.split('|');
    const startDateMoment = moment.tz(fromStr, 'YYYY-MM-DD', true, 'Asia/Kolkata').startOf('day');
    const endDateMoment = moment.tz(toStr, 'YYYY-MM-DD', true, 'Asia/Kolkata').endOf('day');

    if (!startDateMoment.isValid() || !endDateMoment.isValid()) {
      return res.status(400).json({ error: 'Invalid date format in range. Use YYYY-MM-DD' });
    }

    console.log('Resolved Date Range (Asia/Kolkata):', {
      start: startDateMoment.format(),
      end: endDateMoment.format()
    });
    const docs = await Search.find({
      userId,
      childName,
      isHarmful: true
    }).lean();

    console.log(`Retrieved documents count (before date filter): ${docs.length}`);

    function parseDocDate(raw) {
      if (!raw) return null;
      if (raw instanceof Date) return moment.tz(raw, 'Asia/Kolkata');
      if (typeof raw === 'string') {
        const formats = [
          'YYYY-MM-DD, h:mm:ss a',
          'YYYY-MM-DD, h:mm:ss A'
        ];
        for (const fmt of formats) {
          const m = moment.tz(raw.trim(), fmt, true, 'Asia/Kolkata');
          if (m.isValid()) return m;
        }
        console.warn(`⚠️ Failed to parse dateAndTime string: "${raw}"`);
        return null;
      }
      return null;
    }
    const filteredDocs = docs.filter(doc => {
      const m = parseDocDate(doc.dateAndTime);
      if (!m) return false;
      return m.isBetween(startDateMoment, endDateMoment, null, '[]'); 
    });

    console.log(`Filtered documents count (within date range): ${filteredDocs.length}`);
    const hourlyCounts = new Array(24).fill(0);

    filteredDocs.forEach(doc => {
      const m = parseDocDate(doc.dateAndTime);
      if (!m) return;
      const hour = m.hour();
      hourlyCounts[hour]++;
    });
    const output = {};
    for (let i = 0; i < 24; i++) {
      const hour12 = i % 12 === 0 ? 12 : i % 12;
      const suffix = i < 12 ? 'am' : 'pm';
      output[`${hour12}${suffix}`] = hourlyCounts[i];
    }

    console.log('Peak Hours Response:', output);
    return res.json({ data: output });
  } catch (error) {
    console.error('Peak Hours Error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
});

//DrillDown
app.post('/api/drilldown', async (req, res) => {
  try {
    const { userId, childName, label, mode, dateParam, drillType } = req.body;

    console.log("drilldown called with:", req.body);
    const query = { userId, childName };
    console.log("Drill Type:", drillType);
    console.log("Label:", label);
    function parse12HourLabelToHour(labelStr) {
      if (!labelStr || typeof labelStr !== 'string') return null;
      const match = labelStr.trim().toLowerCase().match(/^(\d{1,2})(am|pm)$/);
      if (!match) return null;
      let hour = parseInt(match[1], 10);
      const meridian = match[2];
      if (meridian === 'am') {
        if (hour === 12) hour = 0;
      } else if (meridian === 'pm') {
        if (hour !== 12) hour += 12;
      }
      return hour;
    }

    if (drillType === 'hourly' && mode === 'hour') {
      query.isHarmful = true;
    } else if (drillType === 'Statistics') {
      query.isHarmful = true;
    } else if (drillType === 'harmfulSafe') {
      const labelKey = label.toLowerCase().replace(/\s*searched\s*$/, '');
      if (labelKey === 'harmful') query.isHarmful = true;
      else if (labelKey === 'safe') query.isHarmful = false;
    } else if (drillType === 'prediction' || drillType === 'mentalHealth') {
      query.predictedResult = label.toLowerCase().trim();
    } else if (drillType.toLowerCase() === 'sentiment') {
      query.isHarmful = true;
      query.sentimentScore = { $exists: true };
    }

    console.log("Final MongoDB query:", query);
    const allData = await Search.find(query).sort({ dateAndTime: -1 });
    console.log("Total matching records before filter:", allData.length);

    let filtered = allData;

    if (mode && dateParam) {
      let from, to;

      if (mode === 'Today') {
        from = moment().startOf('day');
        to = moment().endOf('day');
      } else if (mode === 'Pick Date' || mode === 'date') {
        from = moment(dateParam, "YYYY-MM-DD", true).startOf('day');
        to = moment(dateParam, "YYYY-MM-DD", true).endOf('day');
      } else if (mode === 'Choose Range' || mode === 'range' || mode === 'hour') {
        const [fromStr, toStr] = dateParam.split(/→|\|/).map(s => s.trim());
        from = moment(fromStr, "YYYY-MM-DD", true).startOf('day');
        to = moment(toStr, "YYYY-MM-DD", true).endOf('day');
      } else {
        console.warn("Unknown mode for date filtering:", mode);
      }

      if (from && to && from.isValid() && to.isValid()) {
        console.log("🗓 Date filter from:", from.format(), "to:", to.format());
        filtered = filtered.filter(item => {
          const dt = moment(item.dateAndTime);
          return dt.isValid() && dt.isBetween(from, to, undefined, '[]');
        });
      } else {
        console.error("Invalid date range:", { from, to, dateParam, mode });
      }

      console.log("Records after date filter:", filtered.length);
    }

    if (drillType === 'hourly' && label) {
      const selectedHour = parse12HourLabelToHour(label);

      if (selectedHour === null) {
        console.warn("Could not parse hour from label:", label);
      } else {
        console.log(`Filtering records for hour: ${selectedHour}`);

        filtered = filtered.filter(item => {
          const dt = moment(item.dateAndTime);
          if (!dt.isValid()) return false;
          return dt.hour() === selectedHour;
        });

        console.log(`Records after hourly filter: ${filtered.length}`);
      }
    }

    if (drillType === 'Statistics' && dateParam && mode === 'date') {
      const day = moment(dateParam, "YYYY-MM-DD", true);
      if (day.isValid()) {
        const from = day.clone().startOf('day');
        const to = day.clone().endOf('day');
        filtered = filtered.filter(item => {
          const dt = moment(item.dateAndTime);
          return dt.isValid() && dt.isBetween(from, to, undefined, '[]');
        });
        console.log(`[Statistics] Filtered for single date: ${dateParam}, remaining: ${filtered.length}`);
      } else {
        console.warn("[Statistics] Could not parse dateParam for drill down:", dateParam);
      }
    }

    let result;
    if (drillType.toLowerCase() === 'sentiment') {
      result = filtered.map(doc => ({
        query: doc.query,
        dateAndTime: doc.dateAndTime,
        sentimentScore: doc.sentimentScore,
        isHarmful: doc.isHarmful
      }));
    } else {
      result = filtered.map(doc => ({
        query: doc.query,
        dateAndTime: doc.dateAndTime,
        isHarmful: doc.isHarmful,
        predictedResult: doc.predictedResult,
        sentimentScore: doc.sentimentScore,
      }));
    }

    res.status(200).json({ success: true, count: result.length, data: result });

  } catch (err) {
    console.error("Drilldown error:", err);
    res.status(500).json({ success: false, error: "Internal Server Error" });
  }
});




//  Start Server
const PORT = process.env.PORT || 3000;
app.listen(PORT, "0.0.0.0", () => console.log(`Server running on port ${PORT}`));
