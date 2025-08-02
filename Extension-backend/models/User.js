const mongoose = require('mongoose');
const bcrypt = require('bcrypt');

const childSchema = new mongoose.Schema({
  name: { type: String, required: true },
  age: { type: Number, required: true, min: 0, max: 18 },
  profileImage: { type: String, default: '/default-profile.png' }
});

const userSchema = new mongoose.Schema({
  name: { type: String, required: true },
  email: { 
    type: String, 
    required: true, 
    unique: true 
  },
  phone: { type: String, required: true },
  password: { type: String, select: false },
  googleId: { 
    type: String, 
    unique: true, 
    sparse: true 
  },
  isVerified: { type: Boolean, default: false },
  createdAt: { type: Date, default: Date.now },
  children: [childSchema],
  settings: {
    smsAlerts: { type: Boolean, default: true },
    emailAlerts: { type: Boolean, default: true },
    sosAlerts: { type: Boolean, default: false }
  }
});

userSchema.pre('save', async function(next) {
  if (!this.isModified('password')) return next();
  try {
    this.password = await bcrypt.hash(this.password, 10);
    next();
  } catch (err) {
    next(err);
  }
});

const User = mongoose.model('User', userSchema);
module.exports = User;