package com.vinnovateit.latch.cli

import kotlinx.coroutines.delay

/** Columns the full banner needs before it is worth drawing. */
internal const val INTRO_MIN_WIDTH = 46

// Short on purpose: the hooks only move a little, and more frames spend the
// budget on movement too small to see.
private const val MARK_FRAMES = 7
private const val PANEL_FRAMES = 4

/**
 * Terminal width in columns.
 *
 * A plain JVM process has no portable way to ask without a terminal library, so
 * this reads COLUMNS and otherwise assumes the conventional 80. Being wrong is
 * bounded: too narrow and the banner drops to its one-line form, which is never
 * worse than a wrapped banner smearing as it redraws.
 */
internal fun terminalWidth(environment: Map<String, String> = System.getenv()): Int =
    environment["COLUMNS"]?.toIntOrNull()?.takeIf { it > 0 } ?: 80

/**
 * The one-line form, for terminals too narrow to hold the banner.
 */
internal fun compactIntro(state: IntroState): List<List<IntroSpan>> = buildList {
    add(listOf(IntroSpan("Latch", IntroStyle.BRAND), IntroSpan(" v${state.version}", IntroStyle.MUTED)))
    state.rows.forEach { row ->
        add(
            listOf(
                IntroSpan("  ${row.label.padEnd(10)}", IntroStyle.MUTED),
                IntroSpan(row.value ?: "unknown"),
            ),
        )
    }
    if (state.hint.isNotEmpty()) add(listOf(IntroSpan("  ${state.hint}", IntroStyle.MUTED)))
}

/**
 * Draws the intro. Non-interactive output gets nothing, a terminal without ANSI
 * gets the settled banner printed once, and everything else gets the mark
 * closing shut followed by the panel drawing itself.
 */
suspend fun showIntro(
    terminal: TerminalIO,
    capabilities: TerminalCapabilities,
    state: IntroState,
    renderer: IntroRenderer = IntroRenderer(),
    width: Int = terminalWidth(),
    frameDelayMillis: Long = 45,
) {
    if (!capabilities.interactive) return

    // Too narrow to hold the banner, or a console that cannot encode the
    // glyphs it is drawn from. Either way the mark cannot be shown.
    if (width < INTRO_MIN_WIDTH || !capabilities.unicode) {
        compactIntro(state).forEach { terminal.println(it.render(capabilities)) }
        return
    }

    if (!capabilities.ansi || capabilities.noColor) {
        // Printed once, so drop the padding the animation needs.
        renderer.lines(state, trimMark = true).forEach { terminal.println(it.render(capabilities)) }
        return
    }

    val settled = renderer.lines(state)

    // Ctrl+C during the animation kills the process before the finally below can
    // run, which would leave the cursor hidden for the rest of the session.
    val restore = Thread { terminal.print(Ansi.SHOW_CURSOR) }
    Runtime.getRuntime().addShutdownHook(restore)

    val height = settled.size
    var painted = false
    fun paint(frame: List<List<IntroSpan>>) {
        check(frame.size == height) { "intro frame changed height: ${frame.size} != $height" }
        if (painted) terminal.print(Ansi.cursorUp(height))
        painted = true
        // Clear to end of line on every row, or a shorter frame leaves the tail
        // of the previous one behind.
        terminal.print(frame.joinToString("\n", postfix = "\n") { it.render(capabilities) + Ansi.CLEAR_LINE })
    }

    terminal.print(Ansi.HIDE_CURSOR)
    try {
        // Starts at zero: both hooks are on canvas from the first frame, sitting
        // apart along the diagonal, and the animation closes the gap.
        repeat(MARK_FRAMES + 1) { index ->
            paint(
                renderer.lines(
                    state,
                    markReveal = index.toDouble() / MARK_FRAMES,
                    visibleRows = 0,
                    panelReveal = 0.0,
                ),
            )
            delay(frameDelayMillis)
        }
        repeat(PANEL_FRAMES) { index ->
            paint(renderer.lines(state, visibleRows = 0, panelReveal = (index + 1).toDouble() / PANEL_FRAMES))
            delay(frameDelayMillis)
        }
        state.rows.indices.forEach { index ->
            paint(renderer.lines(state, visibleRows = index + 1))
            delay(frameDelayMillis)
        }
        paint(settled)
    } finally {
        terminal.print(Ansi.RESET + Ansi.SHOW_CURSOR)
        runCatching { Runtime.getRuntime().removeShutdownHook(restore) }
    }
}
