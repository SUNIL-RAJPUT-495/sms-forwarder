package com.smsforwarder.oppo.receiver

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SmsBroadcastReceiver's sender ID normalisation.
 *
 * These tests verify that Indian TRAI operator prefixes are stripped
 * correctly before filter evaluation, without requiring any Android runtime.
 */
class SmsBroadcastReceiverTest {

    @Test
    fun `normaliseSenderId strips VM- prefix`() {
        assertEquals("SBIINB", SmsBroadcastReceiver.normaliseSenderId("VM-SBIINB"))
    }

    @Test
    fun `normaliseSenderId strips VD- prefix`() {
        assertEquals("HDFCBK", SmsBroadcastReceiver.normaliseSenderId("VD-HDFCBK"))
    }

    @Test
    fun `normaliseSenderId strips TP- prefix`() {
        assertEquals("ICICIB", SmsBroadcastReceiver.normaliseSenderId("TP-ICICIB"))
    }

    @Test
    fun `normaliseSenderId strips AT- prefix`() {
        assertEquals("AXISBK", SmsBroadcastReceiver.normaliseSenderId("AT-AXISBK"))
    }

    @Test
    fun `normaliseSenderId is a no-op for already-clean IDs`() {
        assertEquals("SBIINB", SmsBroadcastReceiver.normaliseSenderId("SBIINB"))
    }

    @Test
    fun `normaliseSenderId handles lowercase input`() {
        assertEquals("SBIINB", SmsBroadcastReceiver.normaliseSenderId("vm-sbiinb"))
    }

    @Test
    fun `normaliseSenderId does not strip unrecognised prefixes`() {
        // "XX-UNKNOWN" should pass through with prefix intact (uppercased)
        assertEquals("XX-UNKNOWN", SmsBroadcastReceiver.normaliseSenderId("xx-unknown"))
    }

    @Test
    fun `normaliseSenderId handles numeric sender IDs`() {
        assertEquals("+919876543210", SmsBroadcastReceiver.normaliseSenderId("+919876543210"))
    }
}
