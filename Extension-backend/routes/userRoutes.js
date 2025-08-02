const express = require('express');
const router = express.Router();
const User = require('../models/User');
const userController = require('../controllers/userController');

router.post('/users/analyzeQuery', userController.analyzeQuery);
router.post('/users/logTimeSpent', userController.logTimeSpent);
router.get('/get-user/:userId', async (req, res) => {
  try {
    const user = await User.findById(req.params.userId);
    res.json(user);
  } catch (err) {
    console.error(err);
    res.status(500).send("Server error");
  }
});

router.post('/create-child-profile', userController.createChildProfile);

module.exports = router;