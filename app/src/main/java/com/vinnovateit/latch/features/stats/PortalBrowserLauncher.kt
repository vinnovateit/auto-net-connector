package com.vinnovateit.latch.features.stats

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vinnovateit.latch.platform.LatchAppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.regex.Pattern

object PortalBrowserLauncher {
    const val PORTAL_HOST = "136.233.9.110"
    const val PORTAL_LOGIN_PAGE = "http://$PORTAL_HOST/registration/Main.jsp?wispId=1"

    internal fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    internal fun generateAutologinHtml(actionUrl: String, userId: String, password: String): String {
        val escapedUser = escapeHtml(userId)
        val escapedPass = escapeHtml(password)

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Opening Captive Portal...</title>
              <style>
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  min-height: 80vh;
                  margin: 0;
                  background-color: #f7f9fc;
                  color: #333;
                  text-align: center;
                }
                .loader {
                  border: 4px solid #e2e8f0;
                  border-top: 4px solid #3b82f6;
                  border-radius: 50%;
                  width: 36px;
                  height: 36px;
                  animation: spin 1s linear infinite;
                  margin-bottom: 16px;
                }
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
                input[type="submit"] {
                  margin-top: 12px;
                  padding: 8px 16px;
                  background: #3b82f6;
                  color: white;
                  border: none;
                  border-radius: 6px;
                  font-size: 14px;
                  cursor: pointer;
                }
              </style>
            </head>
            <body onload="document.getElementById('portalForm').submit()">
              <div class="loader"></div>
              <p>Logging into captive portal...</p>
              <form id="portalForm" method="POST" action="$actionUrl">
                <input type="hidden" name="loginUserId" value="$escapedUser" />
                <input type="hidden" name="authType" value="Pronto" />
                <input type="hidden" name="loginPassword" value="$escapedPass" />
                <input type="hidden" name="submit" value="Login" />
                <noscript>
                  <input type="submit" value="Click here to continue" />
                </noscript>
              </form>
            </body>
            </html>
        """.trimIndent()
    }

    fun launchManageAccount(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val credentials = LatchAppGraph.platform.credentials
            val userId = if (credentials.exists()) credentials.userId() else null
            val password = if (credentials.exists()) credentials.password() else null

            if (userId.isNullOrBlank() || password.isNullOrBlank()) {
                openUrlInBrowser(context, PORTAL_LOGIN_PAGE)
                return@launch
            }

            try {
                // Fetch Main.jsp to grab the session-aware action URL
                val actionPath = try {
                    val mainUrl = URL(PORTAL_LOGIN_PAGE)
                    val conn = mainUrl.openConnection()
                    conn.connectTimeout = 2500
                    conn.readTimeout = 3000
                    val html = conn.inputStream.bufferedReader().use { it.readText() }
                    val matcher = Pattern.compile("action=[\"']([^\"']*chooseAuth\\.do[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (matcher.find()) matcher.group(1) else "/registration/chooseAuth.do"
                } catch (_: Exception) {
                    "/registration/chooseAuth.do"
                }

                val actionUrl = if (actionPath.startsWith("http")) actionPath else "http://$PORTAL_HOST$actionPath"

                // Start ephemeral loopback server on localhost with any free port
                val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
                serverSocket.soTimeout = 15000 // 15s timeout
                val port = serverSocket.localPort

                withContext(Dispatchers.Main) {
                    openUrlInBrowser(context, "http://127.0.0.1:$port")
                }

                try {
                    val clientSocket = serverSocket.accept()
                    clientSocket.soTimeout = 5000
                    val reader = clientSocket.getInputStream().bufferedReader()
                    reader.readLine() // Read request line

                    val htmlBody = generateAutologinHtml(actionUrl, userId, password)
                    val bodyBytes = htmlBody.toByteArray(Charsets.UTF_8)

                    val writer = OutputStreamWriter(clientSocket.getOutputStream(), Charsets.UTF_8)
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: text/html; charset=utf-8\r\n")
                    writer.write("Content-Length: ${bodyBytes.size}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.write(htmlBody)
                    writer.flush()
                    clientSocket.close()
                } finally {
                    serverSocket.close()
                }
            } catch (_: Exception) {
                // Fallback to regular portal page
                openUrlInBrowser(context, PORTAL_LOGIN_PAGE)
            }
        }
    }

    private fun openUrlInBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignored
        }
    }
}
