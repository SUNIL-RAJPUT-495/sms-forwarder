const mongoose = require('mongoose');

const connectDB = async () => {
  try {
    const mongoURI = process.env.MONGODBURL || process.env.MONGODB_URI;
    if (!mongoURI) {
      console.warn("⚠️ Warning: MONGODBURL not found in .env. Falling back to local storage.");
      return false;
    }

    const conn = await mongoose.connect(mongoURI, {
      serverSelectionTimeoutMS: 5000,
    });

    console.log(`🍃 MongoDB Connected: ${conn.connection.host}`);
    return true;
  } catch (error) {
    console.error(`❌ MongoDB Connection Error: ${error.message}`);
    console.warn(`⚠️ App will continue running with persistent fallback storage.`);
    return false;
  }
};

module.exports = connectDB;
