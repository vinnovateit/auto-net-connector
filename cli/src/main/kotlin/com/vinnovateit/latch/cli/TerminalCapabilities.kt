package com.vinnovateit.latch.cli

import java.nio.charset.Charset

/**
 * What the attached terminal can be asked to do. Everything that draws to the
 * terminal degrades against this rather than assuming a capable emulator.
 */
data class TerminalCapabilities(
    val interactive: Boolean,
    val ansi: Boolean,
    val trueColor: Boolean,
    val noColor: Boolean,
    val unicode: Boolean,
)

/**
 * Every non-ASCII character the banner draws with: half blocks, the medium
 * shade used for shadows without colour, and the panel's box drawing.
 */
private const val BANNER_GLYPHS = "\u2588\u2580\u2584\u2592\u256d\u256e\u2570\u256f\u2500\u2502"

fun detectTerminalCapabilities(
    terminal: TerminalIO,
    environment: Map<String, String> = System.getenv(),
    osName: String = System.getProperty("os.name", ""),
    // The console's own charset, not file.encoding or the default charset:
    // packaging pins those to UTF-8, so both report UTF-8 on a US-ASCII console
    // and the banner goes out as a wall of question marks.
    charset: Charset? = System.console()?.charset(),
): TerminalCapabilities {
    val interactive = terminal.interactive
    val noColor = environment.containsKey("NO_COLOR")
    val term = environment["TERM"].orEmpty()
    val windows = osName.startsWith("Windows", ignoreCase = true)
    // Windows consoles only handle escapes in terminals that advertise it;
    // cmd.exe without one of these renders them as literal garbage.
    val windowsAnsi = environment.containsKey("WT_SESSION") ||
        environment.containsKey("ANSICON") ||
        environment["ConEmuANSI"].equals("ON", ignoreCase = true) ||
        term.contains("xterm", ignoreCase = true)
    val ansi = interactive && !noColor && !term.equals("dumb", ignoreCase = true) && (!windows || windowsAnsi)
    val colorTerm = environment["COLORTERM"].orEmpty()
    val trueColor = ansi && (
        colorTerm.contains("truecolor", ignoreCase = true) ||
            colorTerm.contains("24bit", ignoreCase = true) ||
            environment.containsKey("WT_SESSION")
        )
    val unicode = charset != null &&
        runCatching { charset.newEncoder().canEncode(BANNER_GLYPHS) }.getOrDefault(false)
    return TerminalCapabilities(interactive, ansi, trueColor, noColor, unicode)
}
