package com.vinnovateit.latch.features.stats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.vinnovateit.latch.platform.LatchAppGraph

object PortalBrowserLauncher {
    const val PORTAL_HOST = "136.233.9.110"
    const val PORTAL_LOGIN_PAGE = "http://$PORTAL_HOST/registration/Main.jsp?wispId=1"

    fun launchManageAccount(context: Context) {
        val wifi = LatchAppGraph.platform.wifi
        if (!wifi.isConnectedToWifi()) {
            Toast.makeText(context, "Connect to campus Wi-Fi first", Toast.LENGTH_SHORT).show()
            return
        }

        val credentials = LatchAppGraph.platform.credentials
        val password = if (credentials.exists()) credentials.password() else null
        if (!password.isNullOrBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Portal Password", password))
            Toast.makeText(context, "Opening portal. Password copied to clipboard.", Toast.LENGTH_SHORT).show()
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL_LOGIN_PAGE)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No web browser found", Toast.LENGTH_SHORT).show()
        }
    }
}
