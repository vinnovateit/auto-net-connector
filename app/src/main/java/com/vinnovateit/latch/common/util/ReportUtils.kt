package com.vinnovateit.latch.common.util

import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.io.OutputStream


fun generatePortalHtmlReport(
    sessions: List<PortalSessionRecord>,
    outputStream: OutputStream,
    appVersion: String,
    userId: String? = ""
) = com.vinnovateit.latch.core.stats.generatePortalHtmlReport(sessions, outputStream, appVersion, userId)
