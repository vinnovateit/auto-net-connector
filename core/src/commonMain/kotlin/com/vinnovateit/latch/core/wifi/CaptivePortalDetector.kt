package com.vinnovateit.latch.core.wifi

import com.vinnovateit.latch.core.platform.HttpTransport
import com.vinnovateit.latch.core.platform.Logger
import com.vinnovateit.latch.core.platform.NetworkHandle
import java.net.URL
import java.net.UnknownHostException

/**
 * Typed outcome of a captive portal probe attempt.
 */
sealed interface PortalProbeResult {
    /** The network has real internet (HTTP 204). */
    data object Online : PortalProbeResult

    /** The network intercepted the probe with a captive portal. */
    data class Portal(val responseCode: Int, val location: String?) : PortalProbeResult

    /** DNS resolution failed, typically Private DNS blocking detection. */
    data object DnsBlocked : PortalProbeResult

    /** Network error or timeout. */
    data class Error(val message: String?) : PortalProbeResult
}

/**
 * Probes a known no-content endpoint to decide whether a captive portal is in the way.
 */
class CaptivePortalDetector(
    private val transport: HttpTransport,
    private val logger: Logger,
) {
    private companion object {
        private const val PROBE_URL = "http://clients3.google.com/generate_204"
        private const val TAG = "CaptivePortalDetector"
    }

    fun probe(handle: NetworkHandle? = null): PortalProbeResult {
        val start = System.currentTimeMillis()
        logger.d(TAG, "Probing portal endpoint: $PROBE_URL (handle=${handle?.id ?: "default"})")
        var connection: java.net.HttpURLConnection? = null
        return try {
            val conn = transport.open(URL(PROBE_URL), handle)
            connection = conn
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.useCaches = false
            conn.connect()

            val responseCode = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            val location = conn.getHeaderField("Location")

            logger.d(TAG, "Portal probe completed in ${elapsed}ms: HTTP $responseCode ${if (location != null) "(Location: $location)" else ""}")
            if (responseCode == 204) {
                PortalProbeResult.Online
            } else {
                PortalProbeResult.Portal(responseCode, location)
            }
        } catch (e: UnknownHostException) {
            val elapsed = System.currentTimeMillis() - start
            logger.e(TAG, "Portal check failed after ${elapsed}ms: DNS resolution failed for $PROBE_URL (${e.message})")
            PortalProbeResult.DnsBlocked
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.e(TAG, "Portal check failed after ${elapsed}ms with exception: ${e::class.simpleName}: ${e.message}")
            PortalProbeResult.Error(e.message)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {}
        }
    }
}
