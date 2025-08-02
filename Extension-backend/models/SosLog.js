const mongoose = require('mongoose');

const SosLogSchema = new mongoose.Schema({
  userId: { type: String, required: true },
  userEmail: { type: String, required: true },
  childId: { type: String },
  childName: { type: String, required: true },
  query: { type: String },
  alertTime: { type: Date, default: Date.now },
  additionalInfo: { type: Object }
}, { timestamps: true });

module.exports = mongoose.models.SosLog || mongoose.model('SosLog', SosLogSchema);
