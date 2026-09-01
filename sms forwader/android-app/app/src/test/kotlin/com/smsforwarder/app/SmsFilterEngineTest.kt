package com.smsforwarder.app

import com.smsforwarder.app.data.repository.FilterRepository
import com.smsforwarder.app.domain.model.FilterRule
import com.smsforwarder.app.domain.model.FilterRuleType
import com.smsforwarder.app.domain.model.SmsMessageData
import com.smsforwarder.app.filter.SmsFilterEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmsFilterEngineTest {

    private lateinit var filterRepository: FilterRepository
    private lateinit var filterEngine: SmsFilterEngine

    @Before
    fun setup() {
        filterRepository = mockk()
        val rules = listOf(
            FilterRule(
                id = 1,
                name = "Banks",
                type = FilterRuleType.SENDER_MATCH,
                pattern = "HDFCBK|SBIINB|ICICIB|AXISBK|KOTAKB|PAYTM|CHASE|BOA",
                isEnabled = true,
                extractOtp = true
            ),
            FilterRule(
                id = 2,
                name = "OTP Keywords",
                type = FilterRuleType.KEYWORD_CONTAINS,
                pattern = "otp|verification code|one time password",
                isEnabled = true,
                extractOtp = true
            )
        )
        coEvery { filterRepository.getActiveRules() } returns rules
        filterEngine = SmsFilterEngine(filterRepository)
    }

    @Test
    fun `matching bank SMS forwards and extracts OTP`() = runBlocking {
        val sms = SmsMessageData(
            sender = "HDFCBK",
            body = "Your OTP for Rs 4,500.00 at Amazon is 938102. Valid for 10 mins.",
            timestampMs = System.currentTimeMillis()
        )

        val result = filterEngine.evaluate(sms)

        assertTrue(result.shouldForward)
        assertEquals("938102", result.extractedOtp)
        assertEquals("Banks", result.matchedRuleName)
    }

    @Test
    fun `matching keyword SMS forwards and extracts OTP`() = runBlocking {
        val sms = SmsMessageData(
            sender = "CUSTOM_AUTH",
            body = "Your verification code is 482910. Do not share this code.",
            timestampMs = System.currentTimeMillis()
        )

        val result = filterEngine.evaluate(sms)

        assertTrue(result.shouldForward)
        assertEquals("482910", result.extractedOtp)
    }

    @Test
    fun `promotional SMS without bank sender or OTP keyword is rejected`() = runBlocking {
        val sms = SmsMessageData(
            sender = "DOMINOS",
            body = "Buy 1 Get 1 pizza free today! Use code YUMMY at checkout.",
            timestampMs = System.currentTimeMillis()
        )

        val result = filterEngine.evaluate(sms)

        assertFalse(result.shouldForward)
    }
}
