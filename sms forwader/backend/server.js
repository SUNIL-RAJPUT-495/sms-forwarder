import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import bodyParser from 'body-parser';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

import connectDB from './config/db.js';
import deviceRoutes from './routes/deviceRoutes.js';
import messageRoutes from './routes/messageRoutes.js';
import { registerDevice } from './controllers/deviceController.js';
import { sendSMS } from './controllers/messageController.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

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
app.post('/registerDevice', registerDevice);
app.post('/sendMessage', sendSMS);

// Serve Admin Panel Static UI
const ADMIN_PANEL_PATH = path.join(__dirname, '..', 'admin-panel');
if (fs.existsSync(ADMIN_PANEL_PATH)) {
  app.use(express.static(ADMIN_PANEL_PATH));
}

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`====================================================`);
  console.log(`🚀 SMS Relay Enterprise Server Ready!`);
  console.log(`🌐 Web Dashboard: http://localhost:${PORT}`);
  console.log(`🍃 Database Status: MongoDB (Exclusive Data Source)`);
  console.log(`====================================================`);
});

