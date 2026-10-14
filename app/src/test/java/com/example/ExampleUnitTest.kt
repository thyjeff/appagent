package com.example

import com.example.security.SecurityManager
import com.example.util.UsageStatsHelper
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testGenerateSecureTokenFormat() {
        val token = SecurityManager.generateSecureToken()
        assertTrue(token.startsWith("fp_"))
        assertEquals(51, token.length) // "fp_" (3) + 48 hex chars (24 bytes) = 51
    }

    @Test
    fun testFormatDuration() {
        assertEquals("1h 30m", UsageStatsHelper.formatDuration(90 * 60 * 1000L))
        assertEquals("45m", UsageStatsHelper.formatDuration(45 * 60 * 1000L))
        assertEquals("30s", UsageStatsHelper.formatDuration(30 * 1000L))
    }
}
