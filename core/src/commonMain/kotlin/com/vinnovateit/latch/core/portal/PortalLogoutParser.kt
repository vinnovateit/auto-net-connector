package com.vinnovateit.latch.core.portal

import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.util.regex.Pattern

object PortalLogoutParser {
    private val TIME_SPENT_PATTERN = Pattern.compile(
        """Time\s+Spent\s*:\s*(\d+)\s*H\s*:\s*(\d+)\s*M\s*:\s*(\d+)\s*S""",
        Pattern.CASE_INSENSITIVE
    )
    private val BYTES_SENT_PATTERN = Pattern.compile(
        """Bytes\s+Sent\s*:\s*(\d+)""",
        Pattern.CASE_INSENSITIVE
    )
    private val BYTES_RECEIVED_PATTERN = Pattern.compile(
        """Bytes\s+Received\s*:\s*(\d+)""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(html: String, logoutTimeMillis: Long = System.currentTimeMillis()): PortalSessionRecord? {
        val timeMatcher = TIME_SPENT_PATTERN.matcher(html)
        if (!timeMatcher.find()) return null

        val hours = timeMatcher.group(1)?.toLongOrNull() ?: 0L
        val minutes = timeMatcher.group(2)?.toLongOrNull() ?: 0L
        val seconds = timeMatcher.group(3)?.toLongOrNull() ?: 0L

        val durationMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L

        val sentMatcher = BYTES_SENT_PATTERN.matcher(html)
        val uploadBytes = if (sentMatcher.find()) {
            sentMatcher.group(1)?.toLongOrNull() ?: 0L
        } else {
            0L
        }

        val receivedMatcher = BYTES_RECEIVED_PATTERN.matcher(html)
        val downloadBytes = if (receivedMatcher.find()) {
            receivedMatcher.group(1)?.toLongOrNull() ?: 0L
        } else {
            0L
        }

        // If neither sent nor received matched, or duration is 0 and bytes are 0, this is likely not a valid logout page
        if (!sentMatcher.hitEnd() && !receivedMatcher.hitEnd() && uploadBytes == 0L && downloadBytes == 0L && durationMillis == 0L) {
            // Checked if pattern found
        }

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
