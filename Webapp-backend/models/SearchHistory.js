import mongoose from 'mongoose';

const searchHistorySchema = new mongoose.Schema({
  userId: { type: String, required: true, index: true },
  userEmail: { type: String, required: true },
  childName: { type: String, required: true, index: true }, 
  query: { type: String, required: true },
  dateAndTime: { type: Date, required: true }, 
  isHarmful: { type: Boolean, required: true },
  predictedResult: { type: String },
  sentimentScore: { type: Number },
  totalTimeSpent: { type: Number, default: 0 },
  timestamp: { type: Date, default: Date.now }
},{ collection: 'searches' }); ;

searchHistorySchema.index({ userId: 1, childName: 1 });

searchHistorySchema.pre('save', function(next) {
  if (this.predictedResult) {
    this.predictedResult = this.predictedResult.toLowerCase().trim();
  }
  next();
});

export default mongoose.models.SearchHistory || mongoose.model('SearchHistory', searchHistorySchema);
