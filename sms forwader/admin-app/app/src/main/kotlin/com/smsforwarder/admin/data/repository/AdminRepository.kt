package com.smsforwarder.admin.data.repository

import com.smsforwarder.admin.domain.model.AdminDeviceDto
import com.smsforwarder.admin.domain.model.AdminMessageDto
import com.smsforwarder.admin.network.AdminApiService
import com.smsforwarder.admin.network.DirectSmsRequest
import com.smsforwarder.admin.network.RegisterDeviceRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val apiService: AdminApiService
) {

    suspend fun fetchDevices(): Result<List<AdminDeviceDto>> {
        return try {
            val response = apiService.getAdminDevices()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch devices: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMessages(): Result<List<AdminMessageDto>> {
        return try {
            val response = apiService.getAdminMessages()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch messages: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun simulateRegisterDevice(
        departmentName: String,
        mobileNumber: String,
        address: String
    ): Result<AdminDeviceDto> {
        return try {
            val request = RegisterDeviceRequest(
                deviceName = departmentName,
                departmentName = departmentName,
                mobileNumber = mobileNumber,
                address = address,
                role = "SOURCE"
            )
            val response = apiService.registerAdminDevice(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Simulation failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun simulateSendSms(
        deviceId: String,
        departmentName: String,
        mobileNumber: String,
        address: String,
        sender: String,
        body: String
    ): Result<Unit> {
        return try {
            val request = DirectSmsRequest(
                deviceId = deviceId,
                departmentName = departmentName,
                mobileNumber = mobileNumber,
                address = address,
                sender = sender,
                body = body
            )
            val response = apiService.sendDirectSms(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Send SMS failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
