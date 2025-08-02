import mongoose from 'mongoose';

const childSchema = new mongoose.Schema({
  name: { type: String, required: true },
  age: { type: Number, required: true, min: 0, max: 18 },
  profileImage: { type: String, default: '/default-profile.png' } 
});

const userSchema = new mongoose.Schema({
  name: { type: String, required: true },  
  email: { type: String, required: true, unique: true },
  phone: { type: String, required: true },
  password: { type: String, select: false },
  googleId: { type: String, unique: true, sparse: true },
  isVerified: { type: Boolean, default: false },
  createdAt: { type: Date, default: Date.now },
  googleId: { type: String, unique: true, sparse: true }, 
  children: [childSchema],   

  settings: {
    smsAlerts: { type: Boolean, default: true },
    emailAlerts: { type: Boolean, default: true },
    sosAlerts: { type: Boolean, default: true }
  }
});
userSchema.pre('save', function(next) {
  if (this.isNew && this._id && typeof this._id === 'string') {
    this._id = new mongoose.Types.ObjectId(this._id);
  }
  next();
});
export default mongoose.models.User || mongoose.model('User', userSchema);
