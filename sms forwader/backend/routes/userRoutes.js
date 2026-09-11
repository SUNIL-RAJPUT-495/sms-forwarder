import express from 'express';
import {
  createOrUpdateUser,
  getUserByMobile,
  getUsers,
  getUserById,
  deleteUser
} from '../controllers/userController.js';

const router = express.Router();

// User management endpoints
router.post('/users', createOrUpdateUser);
router.get('/users', getUsers);
router.get('/users/mobile/:mobileNumber', getUserByMobile);
router.get('/users/:id', getUserById);
router.delete('/users/:id', deleteUser);

export default router;
