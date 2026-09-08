package com.vinnovateit.latch.cli

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalCapabilitiesTest {
    private fun detect(
        interactive: Boolean = true,
        environment: Map<String, String> = mapOf("TERM" to "xterm-256color"),
        osName: String = "Linux",
        charset: Charset? = Charsets.UTF_8,
    ) = detectTerminalCapabilities(StubTerminal(interactive), environment, osName, charset)

    @Test
    fun `a piped process is not interactive and gets no ansi`() {
        val capabilities = detect(interactive = false)

        assertFalse(capabilities.interactive)
        assertFalse(capabilities.ansi)
    }

    @Test
    fun `NO_COLOR disables colour whatever the terminal claims`() {
        val capabilities = detect(
            environment = mapOf("TERM" to "xterm-256color", "COLORTERM" to "truecolor", "NO_COLOR" to "1"),
        )

        assertTrue(capabilities.noColor)
        assertFalse(capabilities.ansi)
        assertFalse(capabilities.trueColor)
    }

    @Test
    fun `a dumb terminal gets no ansi`() {
        assertFalse(detect(environment = mapOf("TERM" to "dumb")).ansi)
    }

    @Test
    fun `truecolor is taken from COLORTERM`() {
        assertTrue(detect(environment = mapOf("TERM" to "xterm", "COLORTERM" to "truecolor")).trueColor)
        assertTrue(detect(environment = mapOf("TERM" to "xterm", "COLORTERM" to "24bit")).trueColor)
        assertFalse(detect(environment = mapOf("TERM" to "xterm")).trueColor)
    }

    // A bare Windows console renders escapes as literal garbage, so they are
    // only used where something advertises support for them.
    @Test
    fun `windows needs a terminal that advertises ansi`() {
        assertFalse(detect(environment = mapOf("TERM" to ""), osName = "Windows 11").ansi)

        assertTrue(detect(environment = mapOf("WT_SESSION" to "1"), osName = "Windows 11").ansi)
        assertTrue(detect(environment = mapOf("ANSICON" to "1"), osName = "Windows 11").ansi)
        assertTrue(detect(environment = mapOf("ConEmuANSI" to "ON"), osName = "Windows 11").ansi)
        assertTrue(detect(environment = mapOf("TERM" to "xterm"), osName = "Windows 11").ansi)
    }

    // A US-ASCII console cannot encode the block or box glyphs, and the JVM
    // turns them into question marks on write.
    @Test
    fun `unicode is false when the console cannot encode the banner glyphs`() {
        assertTrue(detect(charset = Charsets.UTF_8).unicode)
        assertFalse(detect(charset = Charsets.US_ASCII).unicode)
        assertFalse(detect(charset = Charset.forName("ISO-8859-1")).unicode)
        assertFalse(detect(charset = null).unicode, "no console means nothing to draw to")
    }

    @Test
    fun `windows terminal implies truecolor`() {
        assertTrue(detect(environment = mapOf("WT_SESSION" to "1"), osName = "Windows 11").trueColor)
    }
}

private class StubTerminal(override val interactive: Boolean) : TerminalIO {
    override fun print(text: String) = Unit
    override fun println(text: String) = Unit
    override fun readLine(prompt: String): String? = null
    override fun readSecret(prompt: String): CharArray? = null
}
