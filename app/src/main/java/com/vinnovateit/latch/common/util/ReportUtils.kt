package com.vinnovateit.latch.common.util

import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import java.io.OutputStream

inline fun generateHtmlReport(
    sessions: List<SessionSummary>,
    outputStream: OutputStream,
    appVersion: String
) = com.vinnovateit.latch.core.stats.generateHtmlReport(sessions, outputStream, appVersion)

inline fun generatePortalHtmlReport(
    sessions: List<PortalSessionRecord>,
    outputStream: OutputStream,
    appVersion: String,
    userId: String? = ""
) = com.vinnovateit.latch.core.stats.generatePortalHtmlReport(sessions, outputStream, appVersion, userId)
