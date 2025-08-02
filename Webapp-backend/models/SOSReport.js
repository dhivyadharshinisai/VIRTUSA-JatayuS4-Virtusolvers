import mongoose from 'mongoose';

const sosReportSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  userEmail: {
    type: String,
    required: true
  },
  childId: {
    type: mongoose.Schema.Types.ObjectId,
    required: true
  },
  childName: {
    type: String,
    required: true
  },
  query: {
    type: String,
    required: true
  },
  additionalInfo: {
    type: Object,
    default: {}
  },
  alertTime: {
    type: Date,
    default: Date.now
  },
  createdAt: {
    type: Date,
    default: Date.now
  },
  updatedAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

sosReportSchema.index({
  query: 'text',
  childName: 'text'
});

const SOSReport = mongoose.model('SOSReport', sosReportSchema);

export default SOSReport;