import mongoose from 'mongoose';

const connectDB = async () => {
  try {
    const mongoURI = process.env.MONGODBURL || process.env.MONGODB_URI || "mongodb://127.0.0.1:27017/sms_forwarder";
    const conn = await mongoose.connect(mongoURI, {
      serverSelectionTimeoutMS: 5000,
    });

    console.log(`🍃 MongoDB Connected Successfully: ${conn.connection.host}`);
    return conn;
  } catch (error) {
    console.error(`❌ MongoDB Connection Error (${error.message}). Please ensure MongoDB is running.`);
    process.exit(1);
  }
};

export default connectDB;
