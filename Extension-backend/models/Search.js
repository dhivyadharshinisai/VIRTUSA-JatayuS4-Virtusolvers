const mongoose = require('mongoose');

const timeSpentUpdateSchema = new mongoose.Schema({
  timeSpent: { type: Number, required: true },
  timestamp: { type: Date, required: true, default: Date.now },
  notificationSent: { type: Boolean, default: false }
});

const searchSchema = new mongoose.Schema({
  userId: { type: String, required: true }, 
  userEmail: { type: String, required: true },
  childId: { type: String },
  childName: { type: String },
  query: { type: String, required: true },
  dateAndTime: { type: String, required: true },
  isHarmful: { type: Boolean, default: false },
  predictedResult: { type: String },
  sentimentScore: { type: Number },
  totalTimeSpent: { type: Number, default: 0 },
  timeSpentUpdates: [timeSpentUpdateSchema], 
  timestamp: { type: Date, default: Date.now }
}, { timestamps: true });

module.exports = mongoose.model('Search', searchSchema);