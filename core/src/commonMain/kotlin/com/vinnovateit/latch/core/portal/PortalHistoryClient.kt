package com.vinnovateit.latch.core.portal

import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.platform.HttpTransport
import com.vinnovateit.latch.core.platform.Logger
import com.vinnovateit.latch.core.platform.NetworkHandle
import com.vinnovateit.latch.core.platform.Platform
import com.vinnovateit.latch.core.platform.logger
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class PortalHistoryClient(
    private val transport: HttpTransport,
    private val logger: Logger = Platform.logger,
) {
    companion object {
        private const val TAG = "PortalHistoryClient"
        const val DEFAULT_PORTAL_HOST = "136.233.9.110"
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 8000
    }

    internal data class DateFilter(
        val startYear: String,
        val startMonth: String,
        val startDay: String,
        val endYear: String,
        val endMonth: String,
        val endDay: String,
    )

    internal fun computeDateFilter(userId: String): DateFilter {
        val regYearMatch = Regex("^(\\d{2})").find(userId.trim())
        val startYear = if (regYearMatch != null) {
            val yr = regYearMatch.groupValues[1].toIntOrNull() ?: 24
            val yearNum = 2000 + yr
            if (yearNum < 2024) "2024" else yearNum.toString()
        } else {
            "2024" // Oldest year in filter dropdown
        }
        val startMonth = "00" // Jan (0-indexed)
        val startDay = "01"

        val cal = java.util.Calendar.getInstance()
        val endYear = cal.get(java.util.Calendar.YEAR).toString()
        val endMonth = String.format(java.util.Locale.US, "%02d", cal.get(java.util.Calendar.MONTH))
        val endDay = String.format(java.util.Locale.US, "%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))

        return DateFilter(
            startYear = startYear,
            startMonth = startMonth,
            startDay = startDay,
            endYear = endYear,
            endMonth = endMonth,
            endDay = endDay,
        )
    }

    fun fetchHistory(
        userId: String,
        password: String,
        handle: NetworkHandle? = null,
        host: String = DEFAULT_PORTAL_HOST
    ): Result<List<PortalSessionRecord>> {
        return try {
            val mainUrl = URL("http://$host/registration/Main.jsp?wispId=1")
            logger.d(TAG, "Requesting portal history URL: $mainUrl")
            val conn1 = transport.open(mainUrl, handle)
            conn1.connectTimeout = CONNECT_TIMEOUT_MS
            conn1.readTimeout = READ_TIMEOUT_MS
            conn1.instanceFollowRedirects = false
            conn1.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")

            val mainHtml = conn1.inputStream.bufferedReader().use { it.readText() }
            logger.d(TAG, "Response code: ${conn1.responseCode}")
            val setCookieHeader = conn1.getHeaderField("Set-Cookie")
            var cookie = setCookieHeader?.substringBefore(";") ?: ""

            // Extract chooseAuth.do action URL (which may contain ;jsessionid=)
            val actionMatcher = Pattern.compile("action=[\"']([^\"']*chooseAuth\\.do[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(mainHtml)
            val actionPath = if (actionMatcher.find()) actionMatcher.group(1) else "/registration/chooseAuth.do"
            val loginUrl = if (actionPath.startsWith("http")) URL(actionPath) else URL("http://$host$actionPath")

            // Step 2: POST credentials
            val postData = buildString {
                append("loginUserId=").append(URLEncoder.encode(userId, "UTF-8"))
                append("&authType=").append(URLEncoder.encode("Pronto", "UTF-8"))
                append("&loginPassword=").append(URLEncoder.encode(password, "UTF-8"))
                append("&submit=").append(URLEncoder.encode("Login", "UTF-8"))
            }

            val conn2 = transport.open(loginUrl, handle)
            conn2.requestMethod = "POST"
            conn2.doOutput = true
            conn2.instanceFollowRedirects = false
            conn2.connectTimeout = CONNECT_TIMEOUT_MS
            conn2.readTimeout = READ_TIMEOUT_MS
            conn2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn2.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            if (cookie.isNotEmpty()) {
                conn2.setRequestProperty("Cookie", cookie)
            }

            conn2.outputStream.bufferedWriter().use { it.write(postData) }
            val conn2Cookie = conn2.getHeaderField("Set-Cookie")
            if (!conn2Cookie.isNullOrEmpty()) {
                cookie = conn2Cookie.substringBefore(";")
            }
            val responseCode2 = conn2.responseCode
            val stream2 = if (responseCode2 in 200..399) conn2.inputStream else conn2.errorStream
            stream2?.bufferedReader()?.use { it.readText() }

            // Step 3: Query history with registration number date range
            val dateFilter = computeDateFilter(userId)
            val filterPostData = buildString {
                append("location=").append(URLEncoder.encode("allLocations", "UTF-8"))
                append("&parameter=").append(URLEncoder.encode("custom", "UTF-8"))
                append("&customStartMonth=").append(URLEncoder.encode(dateFilter.startMonth, "UTF-8"))
                append("&customStartDay=").append(URLEncoder.encode(dateFilter.startDay, "UTF-8"))
                append("&customStartYear=").append(URLEncoder.encode(dateFilter.startYear, "UTF-8"))
                append("&customEndMonth=").append(URLEncoder.encode(dateFilter.endMonth, "UTF-8"))
                append("&customEndDay=").append(URLEncoder.encode(dateFilter.endDay, "UTF-8"))
                append("&customEndYear=").append(URLEncoder.encode(dateFilter.endYear, "UTF-8"))
                append("&button=").append(URLEncoder.encode("View", "UTF-8"))
            }

            val filterUrl = URL("http://$host/registration/customerSessionHistory.do")
            logger.d(TAG, "Requesting portal history URL: $filterUrl")
            val conn3 = transport.open(filterUrl, handle)
            conn3.requestMethod = "POST"
            conn3.doOutput = true
            conn3.instanceFollowRedirects = true
            conn3.connectTimeout = CONNECT_TIMEOUT_MS
            conn3.readTimeout = READ_TIMEOUT_MS
            conn3.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn3.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            if (cookie.isNotEmpty()) {
                conn3.setRequestProperty("Cookie", cookie)
            }

            conn3.outputStream.bufferedWriter().use { it.write(filterPostData) }
            var historyHtml = conn3.inputStream.bufferedReader().use { it.readText() }
            logger.d(TAG, "Response code: ${conn3.responseCode}")
            var records = PortalHistoryParser.parse(historyHtml)
            logger.d(TAG, "Parsed ${records.size} records")

            // Fallback to default GET if filtered request returns no records
            if (records.isEmpty()) {
                val fallbackUrl = URL("http://$host/registration/main.do?content_key=%2FCustomerSessionHistory.jsp")
                logger.d(TAG, "Requesting portal history URL: $fallbackUrl")
                val connFallback = transport.open(fallbackUrl, handle)
                connFallback.requestMethod = "GET"
                connFallback.instanceFollowRedirects = true
                connFallback.connectTimeout = CONNECT_TIMEOUT_MS
                connFallback.readTimeout = READ_TIMEOUT_MS
                connFallback.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                if (cookie.isNotEmpty()) {
                    connFallback.setRequestProperty("Cookie", cookie)
                }
                historyHtml = connFallback.inputStream.bufferedReader().use { it.readText() }
                logger.d(TAG, "Response code: ${connFallback.responseCode}")
                records = PortalHistoryParser.parse(historyHtml)
                logger.d(TAG, "Parsed ${records.size} records")
            }

            Result.success(records)
        } catch (e: Exception) {
            logger.e(TAG, "Error fetching portal history: ${e.message}", e)
            Result.failure(e)
        }
    }
}
