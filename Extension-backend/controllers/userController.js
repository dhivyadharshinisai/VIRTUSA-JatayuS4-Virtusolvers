require('dotenv').config();
const User = require('../models/User');
const mongoose = require('mongoose');
const Search = require('../models/Search');
const nodemailer = require('nodemailer');
const { Vonage } = require('@vonage/server-sdk');
const fetch = (...args) => import('node-fetch').then(({default: fetch}) => fetch(...args));
const SosLog = require("../models/SosLog");
const vonage = new Vonage({
  apiKey: process.env.VONAGE_API_KEY,
  apiSecret: process.env.VONAGE_API_SECRET,
});

const formatTime = (seconds) => {
  if (!seconds) return "0 seconds";
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  
  let timeString = [];
  if (hours > 0) timeString.push(`${hours} hour${hours > 1 ? 's' : ''}`);
  if (minutes > 0) timeString.push(`${minutes} minute${minutes > 1 ? 's' : ''}`);
  if (secs > 0 || timeString.length === 0) timeString.push(`${secs} second${secs !== 1 ? 's' : ''}`);
  
  return timeString.join(' ');
};

const formatDateTime = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = (now.getMonth() + 1).toString().padStart(2, '0');
  const day = now.getDate().toString().padStart(2, '0');
  let hours = now.getHours();
  const minutes = now.getMinutes().toString().padStart(2, '0');
  const seconds = now.getSeconds().toString().padStart(2, '0');
  const ampm = hours >= 12 ? 'PM' : 'AM';
  
  hours = hours % 12;
  hours = hours ? hours : 12; 
  
  return `${year}-${month}-${day}, ${hours}:${minutes}:${seconds} ${ampm}`;
};

exports.getRiskLevelFromSentiment = (score) => {
  if (score === undefined || score === null) return 'Unknown';

  const numScore = Number(score);
  if (isNaN(numScore)) return 'Invalid Score';

  if (numScore > -0.3) return `Low (${numScore.toFixed(2)})`;
  if (numScore <= -0.6) return `High (${numScore.toFixed(2)})`;
  return `Medium (${numScore.toFixed(2)})`;
};

exports.analyzeQuery = async (req, res) => {
  try {
    let { userId, query, childId, childName } = req.body;

    query = query.replace(/( - Google Search| - Yahoo India Search Results)/g, "").trim();
    console.log(`Analyzing query: "${query}" for user: ${userId}`);

    const user = await User.findById(userId);
    if (!user) {
      console.error(`User not found: ${userId}`);
      return res.status(404).json({ error: "User not found" });
    }

    let childProfile = childId ? user.children.id(childId) : user.children[0] || { name: childName || "N/A" };
    console.log(`Child profile: ${childProfile.name}`);

    console.log('Calling GenAI API with query:', query);
    const response = await fetch('http://127.0.0.1:5001/predict', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        userId,
        query,
        metadata: {
          childName: childProfile.name,
          timestamp: new Date().toISOString()
        }
      })
    });
    
    const responseText = await response.text();
    console.log('GenAI API Response:', responseText);

    if (!response.ok) {
      throw new Error(`API Error: ${response.status} - ${responseText}`);
    }

    const result = JSON.parse(responseText);
    console.log('Parsed GenAI Result:', result);

    const predictedResult = (
      result.predictedRisk || 
      result.prediction || 
      result.result || 
      'unknown'
    ).toString().toLowerCase().replace(/_/g, ' ').trim();

    const sentimentScore = parseFloat(
      result.sentimentScore || 
      result.sentiment || 
      result.score || 
      0
    );

    const isHarmful = !['no risk', 'unknown', 'safe', 'neutral'].includes(predictedResult);

    res.json({
      success: true,
      status: predictedResult,
      isHarmful,
      sentimentScore,
      predictedResult,
      message: "Analysis completed",
      timestamp: new Date().toISOString()
    });

  } catch (error) {
    console.error('Analyze Query Error:', error);
    res.status(500).json({ 
      error: 'Analysis failed',
      details: error.message,
      timestamp: new Date().toISOString()
    });
  }
};
exports.logTimeSpent = async (req, res) => {
  try {
    const { userId, query, timeSpent, childId, analysisData = {} } = req.body;
    if (!userId || !query || timeSpent == null) {
      return res.status(400).json({ error: "Missing required fields" });
    }
    const user = await User.findOne({ _id: userId }).select('email children phone settings');
if (!user) {
  return res.status(404).json({ error: "User not found" });
}

const emailEnabled = user.settings?.emailAlerts;
const smsEnabled = user.settings?.smsAlerts;
const sosEnabled = user.settings?.sosAlerts;

console.log("🔧 Alert Settings:", { emailEnabled, smsEnabled, sosEnabled });

    const cleanedQuery = query.replace(/( - Google Search| - Yahoo India Search Results)/g, "").trim();
    const timeToAdd = Math.max(0, Math.floor(Number(timeSpent)) || 0);
    const now = new Date();
    const formattedTime = formatDateTime();

    const { 
      isHarmful = false, 
      predictedResult = 'unknown', 
      sentimentScore = 0 
    } = analysisData;

    const childProfile = childId ? 
      user.children.id(childId) : 
      user.children[0] || { name: "Unknown Child", _id: null };

    console.log('Processing search for:', {
      user: user.email,
      child: childProfile.name,
      query: cleanedQuery,
      timeSpent: timeToAdd,
      isHarmful
    });

    const fiveMinutesAgo = new Date(now - 5 * 60 * 1000);
    
    let searchDoc = await Search.findOne({
      userId: userId,
      childId: childId || null,
      query: cleanedQuery,
      timestamp: { $gte: fiveMinutesAgo }
    });

    let shouldSendAlert = false;
    let isNewRecord = false;
    console.log("shouldsendalert: ",shouldSendAlert);
    
    if (searchDoc) {
      console.log('Updating existing search record:', searchDoc._id);
      
      const newTotalTime = searchDoc.totalTimeSpent + timeToAdd;
      
      if (isHarmful) {
        shouldSendAlert = !searchDoc.alertSent && 
                        searchDoc.totalTimeSpent < 10 && 
                        newTotalTime >= 10;
      }
      
      searchDoc.totalTimeSpent = newTotalTime;
      searchDoc.timeSpentUpdates.push({
        timeSpent: timeToAdd,
        timestamp: now
      });
      searchDoc.isHarmful = isHarmful;
      searchDoc.predictedResult = predictedResult;
      searchDoc.sentimentScore = sentimentScore;
      
      if (shouldSendAlert) {
        searchDoc.alertSent = true;
        searchDoc.alertTime = now;
      }
      
      await searchDoc.save();
    } else {
      console.log('Creating new search record');
      isNewRecord = true;
      if (isHarmful) {
        shouldSendAlert = timeToAdd >= 10;
      }
      
      searchDoc = new Search({
        userId: userId,
        userEmail: user.email,
        childId: childId || null,
        childName: childProfile.name,
        query: cleanedQuery,
        dateAndTime: formattedTime,
        isHarmful,
        predictedResult,
        sentimentScore,
        totalTimeSpent: timeToAdd,
        timeSpentUpdates: [{
          timeSpent: timeToAdd,
          timestamp: now
        }],
        alertSent: shouldSendAlert,
        alertTime: shouldSendAlert ? now : null,
        timestamp: now
      });
      
      await searchDoc.save();
    }
    
   if (shouldSendAlert) {
  console.log('Sending harmful content alert (10+ seconds)');

  if (emailEnabled) {
    try {
      const alertMessage = `
<div style="font-family: Arial, sans-serif; max-width: 600px;">
  <h2 style="color: #ff0000;"> HARMFUL CONTENT ALERT </h2>
  <p><strong>Child:</strong> ${childProfile.name}</p>
  <p><strong>Query:</strong> "${cleanedQuery}"</p>
  <p><strong>Prediction:</strong> ${predictedResult.toUpperCase()}</p>
  <p><strong>Risk Level:</strong> ${exports.getRiskLevelFromSentiment(sentimentScore)}</p>
  <hr style="border: 1px solid #ddd;">
  <p><strong>Note about Risk Levels:</strong></p>
  <ul>
    <li>Score above -0.3 → Low Risk</li>
    <li>Score -0.3 to -0.6 → Medium Risk</li>
    <li>Score below -0.6 → High Risk</li>
  </ul>
  <hr style="border: 1px solid #ddd;">
  <p style="color: #ff0000;">Immediate attention recommended!</p>
</div>`.trim();

      const transporter = nodemailer.createTransport({
        service: 'gmail',
        auth: {
          user: process.env.ALERT_EMAIL,
          pass: process.env.ALERT_EMAIL_PASS,
        },
      });

      await transporter.sendMail({
        from: process.env.ALERT_EMAIL,
        to: user.email,
        subject: " SafeMind Alert: Harmful Content Detected",
        html: alertMessage,
        text: alertMessage.replace(/<[^>]*>?/gm, ''),
      });

      console.log('Alert email sent successfully');
    } catch (emailError) {
      console.error('Failed to send alert:', emailError);
    }
  } else {
    console.log("📭 Email alert not sent - disabled in settings");
  }

  if (smsEnabled) {
    try {
      const message = `Harmful query by ${childProfile.name}: "${cleanedQuery}" (Risk: ${predictedResult})`;
    
      await vonage.sms.send({
        to: user.phone,
        from: "SafeMind",
        text: message
      });

      console.log("📱 SMS sent to:", user.phone);
      const messageStatus = response.messages?.[0]?.status;
  if (messageStatus !== "0") {
    console.error(" Vonage SMS Error Code:", messageStatus, "-", response.messages?.[0]?.errorText);
  } else {
    console.log(" SMS sent successfully to", user.phone);
  }
    } catch (smsErr) {
      console.error("");
    }
  } else {
    console.log("SMS alert not sent - disabled in settings");
  }

  if (sosEnabled && predictedResult.includes('suicide') && searchDoc.totalTimeSpent >= 10) {
    try {
      console.log(" Triggering SOS alert for suicide query...");

      const sosResponse = await fetch('http://localhost:3000/api/sos-alert', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: user._id.toString(),
          childName: childProfile.name,
          query: cleanedQuery
        })
      });

      const sosJson = await sosResponse.json();
      console.log(" SOS Triggered:", sosJson);

    
      await SosLog.create({
        userId: user._id.toString(),
        userEmail: user.email,
        childId: childProfile._id ? childProfile._id.toString() : null,
        childName: childProfile.name,
        query: cleanedQuery,
        additionalInfo: sosJson
      });
      

    } catch (sosErr) {
      console.error("SOS Alert Failed:", sosErr.message);
    }
  } else {
    console.log("SOS not triggered - either not suicide or disabled in settings");
  }
}

    res.json({ 
      success: true,
      message: "Time logged successfully",
      totalTimeSpent: searchDoc.totalTimeSpent,
      isHarmful: searchDoc.isHarmful,
      alertSent: searchDoc.alertSent || false,
      timestamp: now.toISOString()
    });

  } catch (error) {
    console.error('logTimeSpent Error:', error);
    res.status(500).json({ 
      error: 'Failed to log time',
      details: error.message,
      timestamp: new Date().toISOString()
    });
  }
};

exports.createChildProfile = async (req, res) => {
  try {
    const { userId, name, age, phone } = req.body;
    const user = await User.findById(userId);
    if (!user) return res.status(404).json({ error: "User not found" });

    user.children.push({ name, age, phone });
    await user.save();

    res.json({ 
      success: true,
      message: "Child profile created successfully",
      childId: user.children[user.children.length - 1]._id,
      timestamp: new Date().toISOString()
    });
   } catch (error) {
    console.error(' logTimeSpent Error:', error);
    res.status(500).json({ 
      error: 'Failed to log time',
      details: error.message,
      timestamp: new Date().toISOString()
    });
  }
};

async function performFreshAnalysis(userId, query, childId) {
  return new Promise(async (resolve, reject) => {
    const mockRes = {
      json: resolve,
      status: () => mockRes,
      statusCode: 200
    };
    
    try {
      await exports.analyzeQuery({
        body: { userId, query, childId }
      }, mockRes);
    } catch (e) {
      reject(e);
    }
  });
}

exports.validateLogTimeSpent = (req, res, next) => {
  console.log('Validating request:', req.body); 
  
  if (!req.body) {
    return res.status(400).json({ 
      error: "Request body is missing",
      received: req.body 
    });
  }
}