const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());
app.use('/uploads', express.static('uploads'));
mongoose.connect(process.env.MONGO_URI)
  .then(() => {
    console.log("MongoDB Connected");
    console.log("Registered models:", mongoose.modelNames());
  })
  .catch(err => {
    console.error("MongoDB Connection Error:", err);
    process.exit(1);
  });

mongoose.connection.on('error', err => {
  console.error('MongoDB runtime error:', err);
});

const authRoutes = require('./routes/AuthRoutes');
const userRoutes = require('./routes/userRoutes');

app.use('/api', authRoutes);
app.use('/api', userRoutes);

app.listen(5000, () => {
  console.log("Server running on port 5000");
});