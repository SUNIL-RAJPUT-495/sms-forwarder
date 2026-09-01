import mongoose from 'mongoose';

const connectDB = async () => {
  try {
    const mongoURI = process.env.MONGODBURL || process.env.MONGODB_URI;
    if (!mongoURI) {
      console.warn("⚠️ MONGODBURL / MONGODB_URI not set in .env. Falling back to persistent file storage.");
      return null;
    }

    const conn = await mongoose.connect(mongoURI, {
      serverSelectionTimeoutMS: 2000,
    });

    console.log(`🍃 MongoDB Connected: ${conn.connection.host}`);
    return conn;
  } catch (error) {
    console.warn(`⚠️ MongoDB Connection Error (${error.message}). App will use persistent local storage fallback.`);
    return null;
  }
};

export default connectDB;
