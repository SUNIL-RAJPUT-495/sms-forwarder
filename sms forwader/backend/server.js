require('dotenv').config();
const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');
const connectDB = require('./config/db');

// Import Route Modules
const deviceRoutes = require('./routes/deviceRoutes');
const messageRoutes = require('./routes/messageRoutes');

const app = express();
const PORT = process.env.PORT || 3000;

// Initialize Database Connection
connectDB();

// Middleware
app.use(cors());
app.use(bodyParser.json({ limit: '10mb' }));
app.use(bodyParser.urlencoded({ extended: true, limit: '10mb' }));

// Mount Modular API Routes
app.use('/api', deviceRoutes);
app.use('/api', messageRoutes);

// Legacy Root Endpoints Compatibility for Android App
const { registerDevice } = require('./controllers/deviceController');
const { sendSMS } = require('./controllers/messageController');
app.post('/registerDevice', registerDevice);
app.post('/sendMessage', sendSMS);

// Serve Admin Panel Static UI
const ADMIN_PANEL_PATH = path.join(__dirname, '..', 'admin-panel');
if (require('fs').existsSync(ADMIN_PANEL_PATH)) {
  app.use(express.static(ADMIN_PANEL_PATH));
}

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`====================================================`);
  console.log(`🚀 SMS Relay Enterprise Server Ready!`);
  console.log(`🌐 Web Dashboard: http://localhost:${PORT}`);
  console.log(`🍃 Database Status: ${process.env.MONGODBURL ? 'MongoDB Active' : 'Local Fallback'}`);
  console.log(`====================================================`);
});
