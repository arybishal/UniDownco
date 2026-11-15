package com.unidownco

import com.unidownco.core.logging.LogEntry
import com.unidownco.core.logging.LogLevel
import com.unidownco.core.logging.UniLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UniLogTest {

    @Before
    fun setUp() {
        UniLog.clearLogs()
    }

    @Test
    fun testRedact_SensitiveTokenAndSecret() {
        val input = "GET https://example.com/api?token=secret123&api_key=AIzaSyD-abc456&sig=xyz789"
        val redacted = UniLog.redact(input)
        assertFalse(redacted.contains("secret123"))
        assertFalse(redacted.contains("AIzaSyD-abc456"))
        assertFalse(redacted.contains("xyz789"))
        assertTrue(redacted.contains("token=[REDACTED]"))
        assertTrue(redacted.contains("api_key=[REDACTED]"))
        assertTrue(redacted.contains("sig=[REDACTED]"))
    }

    @Test
    fun testRedact_AuthorizationHeaderAndCookie() {
        val authHeader = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val cookieHeader = "Cookie: session_id=abc123456; theme=dark"
        val redactedAuth = UniLog.redact(authHeader)
        val redactedCookie = UniLog.redact(cookieHeader)

        assertFalse(redactedAuth.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertTrue(redactedAuth.contains("Authorization: Bearer [REDACTED]"))

        assertFalse(redactedCookie.contains("session_id=abc123456"))
        assertTrue(redactedCookie.contains("Cookie: [REDACTED]"))
    }

    @Test
    fun testLogFiltering_ByLevel() {
        val e1 = LogEntry(1, System.currentTimeMillis(), LogLevel.DEBUG, "Network", "Debug message")
        val e2 = LogEntry(2, System.currentTimeMillis(), LogLevel.INFO, "HomeViewModel", "Info message")
        val e3 = LogEntry(3, System.currentTimeMillis(), LogLevel.WARN, "DownloadEngine", "Warning message")
        val e4 = LogEntry(4, System.currentTimeMillis(), LogLevel.ERROR, "DownloadRepository", "Error message")

        val list = listOf(e1, e2, e3, e4)

        val debugOnly = list.filter { it.level == LogLevel.DEBUG }
        val infoOnly = list.filter { it.level == LogLevel.INFO }
        val errorsOnly = list.filter { it.level == LogLevel.ERROR }

        assertEquals(1, debugOnly.size)
        assertEquals(1, infoOnly.size)
        assertEquals(1, errorsOnly.size)
        assertEquals(e4.tag, errorsOnly.first().tag)
    }

    @Test
    fun testLogSearch_ByKeywordAndTag() {
        val e1 = LogEntry(1, System.currentTimeMillis(), LogLevel.INFO, "HomeViewModel", "Extraction SUCCESS: title=Me at the zoo")
        val e2 = LogEntry(2, System.currentTimeMillis(), LogLevel.INFO, "DownloadEngine", "Executing yt-dlp download: format=137")
        val e3 = LogEntry(3, System.currentTimeMillis(), LogLevel.ERROR, "NetworkClient", "Connection timeout on socket")

        val list = listOf(e1, e2, e3)

        val searchExtraction = list.filter { it.message.contains("Extraction", ignoreCase = true) || it.tag.contains("Extraction", ignoreCase = true) }
        assertEquals(1, searchExtraction.size)
        assertEquals("HomeViewModel", searchExtraction.first().tag)

        val searchByTag = list.filter { it.tag.contains("DownloadEngine", ignoreCase = true) }
        assertEquals(1, searchByTag.size)

        val searchNonExistent = list.filter { it.message.contains("NonExistentString123", ignoreCase = true) }
        assertTrue(searchNonExistent.isEmpty())
    }

    @Test
    fun testLogExport_FormatsHeaderAndEntries() {
        UniLog.i("TestRunner", "Sample test log entry for export")
        val exported = UniLog.exportToString()

        assertTrue(exported.contains("UniDownco Diagnostic Logs Export"))
        assertTrue(exported.contains("Total Visible Entries: 1 / 1"))
        assertTrue(exported.contains("[INFO] [TestRunner] Sample test log entry for export"))
    }
}
