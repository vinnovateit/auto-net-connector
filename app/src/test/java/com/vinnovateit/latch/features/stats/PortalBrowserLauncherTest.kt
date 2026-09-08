package com.vinnovateit.latch.features.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalBrowserLauncherTest {

    @Test
    fun testEscapeHtml() {
        assertEquals("&amp;&quot;&lt;&gt;", PortalBrowserLauncher.escapeHtml("&\"<>"))
        assertEquals("normal_user", PortalBrowserLauncher.escapeHtml("normal_user"))
        assertEquals("user&amp;pass", PortalBrowserLauncher.escapeHtml("user&pass"))
    }

    @Test
    fun testGenerateAutologinHtml() {
        val actionUrl = "http://136.233.9.110/registration/chooseAuth.do;jsessionid=12345"
        val userId = "22BCE1001"
        val password = "mySecretPassword&123"

        val html = PortalBrowserLauncher.generateAutologinHtml(actionUrl, userId, password)

        assertTrue(html.contains("""action="$actionUrl""""))
        assertTrue(html.contains("""name="loginUserId" value="22BCE1001""""))
        assertTrue(html.contains("""name="authType" value="Pronto""""))
        assertTrue(html.contains("""name="loginPassword" value="mySecretPassword&amp;123""""))
        assertTrue(html.contains("""name="submit" value="Login""""))
        assertTrue(html.contains("""onload="document.getElementById('portalForm').submit()""""))
    }
}
