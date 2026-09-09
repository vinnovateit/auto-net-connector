package com.vinnovateit.latch.core.portal

import com.vinnovateit.latch.core.model.PortalSessionRecord

object PortalLogoutParser {
    private val TIME_SPENT_REGEX = Regex("""Time\s+Spent\s*:\s*(\d+)\s*H\s*:\s*(\d+)\s*M\s*:\s*(\d+)\s*S""", RegexOption.IGNORE_CASE)
    private val BYTES_SENT_REGEX = Regex("""Bytes\s+Sent\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val BYTES_RECEIVED_REGEX = Regex("""Bytes\s+Received\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(html: String, logoutTimeMillis: Long = System.currentTimeMillis()): PortalSessionRecord? {
        val timeMatch = TIME_SPENT_REGEX.find(html) ?: return null

        val hours = timeMatch.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
        val minutes = timeMatch.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
        val seconds = timeMatch.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L

        val durationMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L

        val uploadBytes = BYTES_SENT_REGEX.find(html)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val downloadBytes = BYTES_RECEIVED_REGEX.find(html)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

        val durationFormatted = formatDuration(hours, minutes, seconds)
        val loginTime = (logoutTimeMillis - durationMillis).coerceAtLeast(0L)

        return PortalSessionRecord(
            location = "Portal Logout",
            macAddress = "",
            loginTime = loginTime,
            logoutTime = logoutTimeMillis,
            durationFormatted = durationFormatted,
            durationMillis = durationMillis,
            uploadBytes = uploadBytes,
            downloadBytes = downloadBytes,
            totalBytes = uploadBytes + downloadBytes,
            isManual = true
        )
    }

    private fun formatDuration(hours: Long, minutes: Long, seconds: Long): String {
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("$hours hr")
        if (minutes > 0) parts.add("$minutes min")
        if (seconds > 0 || parts.isEmpty()) parts.add("$seconds sec")
        return parts.joinToString(" ")
    }
}
