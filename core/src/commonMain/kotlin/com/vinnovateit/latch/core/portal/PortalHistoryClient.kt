package com.vinnovateit.latch.core.portal

import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.platform.HttpTransport
import com.vinnovateit.latch.core.platform.NetworkHandle
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class PortalHistoryClient(
    private val transport: HttpTransport
) {
    companion object {
        const val DEFAULT_PORTAL_HOST = "136.233.9.110"
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 8000
    }

    fun fetchHistory(
        userId: String,
        password: String,
        handle: NetworkHandle? = null,
        host: String = DEFAULT_PORTAL_HOST
    ): Result<List<PortalSessionRecord>> {
        return try {
            val mainUrl = URL("http://$host/registration/Main.jsp?wispId=1")
            val conn1 = transport.open(mainUrl, handle)
            conn1.connectTimeout = CONNECT_TIMEOUT_MS
            conn1.readTimeout = READ_TIMEOUT_MS
            conn1.instanceFollowRedirects = false
            conn1.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")

            val mainHtml = conn1.inputStream.bufferedReader().use { it.readText() }
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
            conn2.inputStream.bufferedReader().use { it.readText() }

            // Step 3: GET CustomerSessionHistory.jsp
            val historyUrl = URL("http://$host/registration/main.do?content_key=%2FCustomerSessionHistory.jsp")
            val conn3 = transport.open(historyUrl, handle)
            conn3.requestMethod = "GET"
            conn3.instanceFollowRedirects = true
            conn3.connectTimeout = CONNECT_TIMEOUT_MS
            conn3.readTimeout = READ_TIMEOUT_MS
            conn3.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            if (cookie.isNotEmpty()) {
                conn3.setRequestProperty("Cookie", cookie)
            }

            val historyHtml = conn3.inputStream.bufferedReader().use { it.readText() }
            val records = PortalHistoryParser.parse(historyHtml)
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
