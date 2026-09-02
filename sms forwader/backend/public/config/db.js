import mongoose from 'mongoose';

let isConnected = false;

const connectDB = async () => {
  const mongoURI = process.env.MONGODBURL || process.env.MONGODB_URI || "mongodb://127.0.0.1:27017/sms_forwarder";

  try {
    const conn = await mongoose.connect(mongoURI, {
      serverSelectionTimeoutMS: 5000,
    });
    isConnected = true;
    console.log(`🍃 MongoDB Connected Successfully: ${conn.connection.host}`);
    return conn;
  } catch (error) {
    isConnected = false;
    console.warn(`⚠️ MongoDB Connection Warning (${error.message}). Auto-retrying in 5 seconds...`);
    setTimeout(connectDB, 5000);
    return null;
  }
};

mongoose.connection.on('disconnected', () => {
  isConnected = false;
  console.warn('⚠️ MongoDB Disconnected. Retrying connection...');
  setTimeout(connectDB, 5000);
});

export const getDbStatus = () => isConnected;

export default connectDB;
