import dotenv from 'dotenv';
import express from 'express';
import mongoose from 'mongoose';
import cors from 'cors';
import bcrypt from 'bcryptjs';
import axios from 'axios';
import { OAuth2Client } from 'google-auth-library';
import nodemailer from 'nodemailer';
import otpRoutes from './routes/mobileotp.js';
import signupEmailRoute from './routes/emailotp.js';
import signupRoutes from './routes/signup.js';
import SOSReportRoutes from './routes/SOSReport.js';
import User from './models/User.js';
import PendingSignup from './models/PendingSignup.js';
import SearchHistory from './models/SearchHistory.js';
import SOSReport from './models/SOSReport.js';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import multer from 'multer';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5000;
const GOOGLE_API_KEY = process.env.GOOGLE_API_KEY;
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const uploadDir = path.join(__dirname, 'uploads');
const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);
const otpStore = {};

app.use(cors({
  origin: 'http://localhost:3000',
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  exposedHeaders: ['Content-Length'],
  credentials: true,
  maxAge: 86400
}));

app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));
app.use('/api/otp', otpRoutes);
app.use('/signup', signupEmailRoute);
app.use('/api/signup', signupRoutes);
app.set('trust proxy', 1);

app.use('/uploads', express.static(uploadDir, {
  setHeaders: (res, path) => {
    res.setHeader('Cache-Control', 'public, max-age=31536000');
    res.setHeader('Access-Control-Allow-Origin', 'http://localhost:3000');
    res.setHeader('Access-Control-Allow-Methods', 'GET');
    res.removeHeader('X-Powered-By');
  }
}));

if (!process.env.GOOGLE_CLIENT_ID) {
  console.error('FATAL ERROR: Missing required environment variables');
  process.exit(1);
}

if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

mongoose.connect(process.env.MONGODB_URI, {
  dbName: 'SMW',
  serverSelectionTimeoutMS: 30000,
  socketTimeoutMS: 45000,
  retryWrites: true,
  w: 'majority',
  retryReads: true,
  connectTimeoutMS: 30000,
})
.then(() => console.log('MongoDB connected successfully'))
.catch(err => {
  console.error('MongoDB connection error:', err);
  setTimeout(() => {
    console.log('Retrying MongoDB connection...');
    mongoose.connect(process.env.MONGODB_URI);
  }, 5000);
});

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({ 
  storage,
  limits: {
    fileSize: 5 * 1024 * 1024
  },
  fileFilter: (req, file, cb) => {
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Only image files are allowed!'), false);
    }
  }
});

const transporter = nodemailer.createTransport({
  service: "Gmail",
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASSWORD,
  },
});

app.post('/login', async (req, res) => {
  const { email, password } = req.body;

  try {
    const user = await User.findOne({ email })
      .select('+password +children')
      .lean();
    
    if (!user) return res.status(401).json({ message: 'Invalid credentials' });

    if (!user.password) {
      return res.status(401).json({
        message: 'Account created with Google. Please sign in with Google.'
      });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) return res.status(401).json({ message: 'Invalid credentials' });

    res.json({
      success: true,
      user: {
        _id: user._id,
        email: user.email,
        name: user.name,
        children: user.children || []
      }
    });

  } catch (err) {
    console.error("Login error:", err);
    res.status(500).json({ 
      success: false,
      message: 'Server error' 
    });
  }
});

app.get('/api/user/profile', async (req, res) => {
  try {
    const userId = req.query.userId;
    if (!userId) return res.status(401).json({ message: 'User ID required' });

    const user = await User.findById(userId).lean();
    if (!user) return res.status(404).json({ message: 'User not found' });

    res.json({
      id: user._id,
      name: user.name,
      email: user.email,
      children: user.children || [],
      settings: user.settings
    });
  } catch (err) {
    console.error('Profile error:', err);
    res.status(500).json({ message: 'Server error' });
  }
});

app.post('/api/forgot-password', async (req, res) => {
  const { email } = req.body;
  try {
    const user = await User.findOne({ email });
    if (!user) return res.status(404).json({ success: false, message: "User not found" });

    const name = user.name || "User";
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    otpStore[email] = { otp, expires: Date.now() + 5 * 60 * 1000 };

    const mailOptions = {
      from: process.env.EMAIL_USER,
      to: email,
      subject: 'Reset Password OTP - SafeMindWatcher',
      html: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
        <h2>Password Reset Request</h2>
        <p>Hello ${name},</p>
        <p>Your OTP for password reset is:</p>
        <div style="background-color: #f2f2f2; text-align: center; padding: 15px; font-size: 24px; font-weight: bold; letter-spacing: 3px;">
          ${otp}
        </div>
        <p style="color: purple; font-weight: bold; margin-top: 20px;">This OTP is valid for 5 minutes only.</p>
        <p>If you didn't request this password reset, please ignore this email.</p>
        <p>Thanks,<br/>SafeMindWatcher Team</p>
      </div>`
    };

    await transporter.sendMail(mailOptions);
    return res.json({ success: true, message: "OTP sent to your email" });

  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, message: "Server error" });
  }
});

app.post('/api/verify-otp', (req, res) => {
  const { email, otp } = req.body;
  const record = otpStore[email];

  if (!record) return res.status(400).json({ success: false, message: "No OTP request found" });
  if (Date.now() > record.expires) return res.status(400).json({ success: false, message: "OTP expired" });

  if (record.otp === otp.toString()) {
    delete otpStore[email];
    return res.json({ success: true, message: "OTP verified" });
  } else {
    return res.status(400).json({ success: false, message: "Invalid OTP" });
  }
});

app.post("/api/reset-password", async (req, res) => {
  const { email, newPassword } = req.body;

  try {
    const user = await User.findOne({ email });
    if (!user) return res.status(404).json({ success: false, message: "User not found" });

    const hashedPassword = await bcrypt.hash(newPassword, 10);
    user.password = hashedPassword;
    await user.save();

    return res.status(200).json({ success: true, message: "Password updated successfully" });
  } catch (error) {
    console.error("Reset Password Error:", error);
    return res.status(500).json({ success: false, message: "Internal Server Error" });
  }
});

app.post('/api/auth/google', async (req, res) => {
  const { token } = req.body;

  try {
    const ticket = await googleClient.verifyIdToken({
      idToken: token,
      audience: process.env.GOOGLE_CLIENT_ID
    });

    const { name, email, sub: googleId } = ticket.getPayload();
    const session = await mongoose.startSession();
    session.startTransaction();

    try {
      let user = await User.findOne({ 
        $or: [{ email }, { googleId }] 
      }).session(session);

      if (!user) {
        user = new User({ 
          name, 
          email, 
          googleId,
          isVerified: true 
        });
      } else {
        if (!user.googleId) user.googleId = googleId;
        if (!user.isVerified) user.isVerified = true;
      }

      await user.save({ session });
      await session.commitTransaction();

      res.json({
        success: true,
        user: {
          id: user._id.toString(),
          name: user.name,
          email: user.email,
          children: user.children || []
        }
      });

    } catch (error) {
      await session.abortTransaction();
      throw error;
    } finally {
      session.endSession();
    }

  } catch (error) {
    console.error('Google auth error:', error);
    res.status(401).json({ 
      success: false,
      message: 'Google authentication failed',
      error: error.message 
    });
  }
});

const calculateDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return +(R * c).toFixed(1);
};

app.get('/api/geocode', async (req, res) => {
  try {
    const { place } = req.query;
    const response = await axios.get('https://maps.googleapis.com/maps/api/geocode/json', {
      params: { address: place, key: GOOGLE_API_KEY }
    });

    if (response.data.status !== 'OK') {
      return res.status(400).json({ error: 'Invalid location' });
    }

    res.json(response.data);
  } catch (err) {
    console.error('Geocode error:', err);
    res.status(500).json({ error: 'Geocode failed' });
  }
});

app.get('/api/nearby', async (req, res) => {
  try {
    const { lat, lng } = req.query;
    const nearbyRes = await axios.get('https://maps.googleapis.com/maps/api/place/nearbysearch/json', {
      params: {
        location: `${lat},${lng}`,
        radius: 10000,
        keyword: 'child psychologist',
        key: GOOGLE_API_KEY
      }
    });

    const PONDICHERRY_LAT = 11.9139;
    const PONDICHERRY_LNG = 79.8145;

    const detailPromises = nearbyRes.data.results.map(async (place) => {
      const details = await axios.get('https://maps.googleapis.com/maps/api/place/details/json', {
        params: {
          place_id: place.place_id,
          fields: 'name,formatted_phone_number,opening_hours,photos,geometry,vicinity,rating',
          key: GOOGLE_API_KEY
        }
      });

      const result = details.data.result;
      const idx = new Date().getDay() === 0 ? 6 : new Date().getDay() - 1;

      let postalCode = '';
      try {
        const geoRes = await axios.get('https://maps.googleapis.com/maps/api/geocode/json', {
          params: {
            latlng: `${result.geometry.location.lat},${result.geometry.location.lng}`,
            key: GOOGLE_API_KEY
          }
        });

        const components = geoRes.data.results?.[0]?.address_components || [];
        const postalObj = components.find(c => c.types.includes('postal_code'));
        if (postalObj) postalCode = ` - ${postalObj.long_name}`;
      } catch (err) {
        console.error('Postal code fetch error:', err.message);
      }

      const distanceFromPondicherry = calculateDistance(
        PONDICHERRY_LAT,
        PONDICHERRY_LNG,
        result.geometry.location.lat,
        result.geometry.location.lng
      );

      const photoRef = result.photos?.[0]?.photo_reference;
      return {
        name: result.name,
        place_id: place.place_id,
        address: result.vicinity + postalCode,
        phone: result.formatted_phone_number || 'N/A',
        openNow: result.opening_hours?.open_now,
        hours: result.opening_hours?.weekday_text?.[idx],
        photoUrl: photoRef
          ? `https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photoreference=${photoRef}&key=${GOOGLE_API_KEY}`
          : null,
        lat: result.geometry.location.lat,
        lng: result.geometry.location.lng,
        rating: result.rating || null,
        distance: `${distanceFromPondicherry} km`
      };
    });

    const doctors = await Promise.all(detailPromises);
    res.json(doctors);
  } catch (err) {
    console.error('Nearby doctors error:', err.message || err);
    res.status(500).json({ error: 'Failed to fetch doctors' });
  }
});

app.get('/api/reviews', async (req, res) => {
  const { placeId } = req.query;
  if (!placeId) return res.status(400).json({ error: 'Missing placeId' });

  try {
    const response = await axios.get('https://maps.googleapis.com/maps/api/place/details/json', {
      params: {
        place_id: placeId,
        fields: 'reviews',
        key: GOOGLE_API_KEY
      }
    });

    const reviews = response.data.result.reviews || [];
    res.json({ reviews });
  } catch (err) {
    console.error('Review fetch error:', err);
    res.status(500).json({ error: 'Failed to fetch reviews' });
  }
});

app.get('/api/users', async (req, res) => {
  try {
    const users = await User.find({}, '-password -googleId');
    res.json(users);
  } catch (err) {
    res.status(500).send('Server Error');
  }
});

app.get('/api/users/:id', async (req, res) => {
  try {
    const user = await User.findById(req.params.id)
      .select('-password -googleId')
      .lean();

    if (!user) return res.status(404).json({ message: 'User not found' });

    res.json(user);
  } catch (err) {
    console.error('Error fetching user:', err);
    res.status(500).json({ message: 'Server error' });
  }
});

app.put('/api/users/:id', async (req, res) => {
  try {
    const updated = await User.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json({ success: true, updated });
  } catch (err) {
    console.error('Update profile error:', err);
    res.status(500).json({ success: false, message: 'Update failed' });
  }
});

app.get('/api/sos-reports', async (req, res) => {
  try {
    const { userId, childName, date, startDate, endDate } = req.query;
    
    let query = {
      userId: userId,
      childName: { $regex: new RegExp(`^${childName}$`, 'i') }
    };

    if (date) {
      const start = new Date(date);
      start.setHours(0, 0, 0, 0);
      const end = new Date(date);
      end.setHours(23, 59, 59, 999);
      query.alertTime = { $gte: start, $lte: end };
    } 
    else if (startDate && endDate) {
      const start = new Date(startDate);
      start.setHours(0, 0, 0, 0);
      const end = new Date(endDate);
      end.setHours(23, 59, 59, 999);
      query.alertTime = { $gte: start, $lte: end };
    }

    const reports = await mongoose.connection.db.collection('soslogs')
      .find(query)
      .sort({ alertTime: -1 })
      .toArray();

    res.json({ success: true, data: reports });
    
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

app.get('/api/searches/child', async (req, res) => {
  try {
    const { userId, childName } = req.query;
    
    if (!userId || !childName) {
      return res.status(400).json({ 
        success: false,
        message: 'User ID and child name are required' 
      });
    }

    const histories = await SearchHistory.find({ 
      userId: userId.trim(),
      childName: { $regex: new RegExp(`^${childName.trim()}$`, 'i') }
    })
    .sort({ timestamp: -1 })
    .lean();

    res.json({
      success: true,
      count: histories.length,
      data: histories
    });
    
  } catch (err) {
    console.error('Error fetching child search history:', err);
    res.status(500).json({ 
      success: false,
      message: 'Server error',
      error: err.message 
    });
  }
});

app.post('/api/users/:userId/children', upload.single('profileImage'), async (req, res) => {
  try {
    const { userId } = req.params;
    const { name, age } = req.body;
    
    if (!name || !age) return res.status(400).json({ message: 'Name and age are required' });

    let profileImage = null;
    if (req.file) profileImage = `/uploads/${req.file.filename}`;

    const updatedUser = await User.findByIdAndUpdate(
      userId,
      {
        $push: {
          children: {
            name,
            age: parseInt(age),
            profileImage
          }
        }
      },
      { new: true }
    );
    
    res.json({
      success: true,
      user: updatedUser
    });
    
  } catch (error) {
    console.error('Error adding child:', error);
    res.status(500).json({
      success: false,
      message: 'Failed to add child profile',
      error: error.message
    });
  }
});

setInterval(async () => {
  try {
    await PendingSignup.deleteMany({ otpExpiry: { $lt: new Date() } });
  } catch (err) {
    console.error("OTP cleanup error:", err.message);
  }
}, 10 * 60 * 1000);

app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({
    message: 'Internal Server Error',
    error: process.env.NODE_ENV === 'development' ? err.message : 'Something went wrong'
  });
});

app.listen(PORT, () => {
  console.log(`Server running on http://localhost:${PORT}`);
});