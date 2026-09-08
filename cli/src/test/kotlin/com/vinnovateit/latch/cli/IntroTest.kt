package com.vinnovateit.latch.cli

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ESCAPE = '\u001b'

class IntroTest {
    private fun ink(mark: List<String>) = mark.sumOf { row -> row.count { it != ' ' } }

    /** Mean row index of the ink in the left and right halves of the mark. */
    private fun halfCentres(mark: List<String>): Pair<Double, Double> {
        val width = mark.first().length
        fun centre(columns: IntRange): Double {
            var weighted = 0.0
            var total = 0
            mark.forEachIndexed { row, line ->
                val count = columns.count { it < line.length && line[it] != ' ' }
                weighted += row * count
                total += count
            }
            return if (total == 0) 0.0 else weighted / total
        }
        return centre(0 until width / 2) to centre(width / 2 until width)
    }

    private val state = IntroState(
        version = "1.3.9",
        rows = listOf(
            IntroRow("network", "VIT-WIFI"),
            IntroRow("status", "latched"),
            IntroRow("account", "22BCE1234"),
        ),
    )

    private fun capabilities(
        interactive: Boolean = true,
        ansi: Boolean = true,
        trueColor: Boolean = true,
        noColor: Boolean = false,
        unicode: Boolean = true,
    ) = TerminalCapabilities(interactive, ansi, trueColor, noColor, unicode)

    // The banner is redrawn in place with a cursor-up, so a frame that changes
    // height walks the redraw up into the scrollback and smears the output.
    @Test
    fun `mark keeps one height at every point of travel`() {
        val renderer = IntroRenderer()
        val heights = (0..10).map { renderer.mark(it / 10.0).size }.toSet()

        assertEquals(1, heights.size, "mark heights varied: $heights")
    }

    @Test
    fun `banner keeps one height while the panel fills in`() {
        val renderer = IntroRenderer()
        val heights = buildSet {
            add(renderer.lines(state).size)
            (0..8).forEach { add(renderer.lines(state, markReveal = it / 8.0, visibleRows = 0, panelReveal = 0.0).size) }
            state.rows.indices.forEach { add(renderer.lines(state, visibleRows = it).size) }
        }

        assertEquals(1, heights.size, "banner heights varied: $heights")
    }

    // The design brief: the hooks must not draw themselves into existence, and
    // they must stay in frame. A reveal starts empty and accumulates ink, and a
    // clipped hook loses it, so holding the ink count near constant across the
    // whole motion covers both. It is near rather than exactly constant because
    // translating by fractions of a pixel changes which samples land inside the
    // shape; the wobble is a few percent, nothing like accumulation.
    @Test
    fun `the hooks stay whole and in frame for the entire motion`() {
        val renderer = IntroRenderer()
        val settled = ink(renderer.mark(1.0))
        assertTrue(settled > 0, "the settled mark should have ink")

        (0..10).forEach { step ->
            val amount = ink(renderer.mark(step / 10.0))
            val ratio = amount.toDouble() / settled
            assertTrue(
                ratio > 0.9 && ratio < 1.1,
                "ink at ${step / 10.0} was $amount against $settled settled: the mark is being drawn or clipped",
            )
        }
    }

    @Test
    fun `the hooks close along a diagonal, left high and right low`() {
        val renderer = IntroRenderer()

        val (leftAtRest, rightAtRest) = halfCentres(renderer.mark(0.0))
        assertTrue(
            leftAtRest < rightAtRest,
            "at rest the left hook should sit above the right one ($leftAtRest vs $rightAtRest)",
        )

        val (leftSettled, rightSettled) = halfCentres(renderer.mark(1.0))
        assertTrue(
            kotlin.math.abs(leftSettled - rightSettled) < kotlin.math.abs(leftAtRest - rightAtRest),
            "the hooks should level out as they connect",
        )
    }

    @Test
    fun `the hooks are further apart at rest than when connected`() {
        val renderer = IntroRenderer()
        fun spread(mark: List<String>): Int {
            val columns = mark.flatMap { row -> row.withIndex().filter { it.value != ' ' }.map { it.index } }
            return columns.max() - columns.min()
        }

        assertTrue(
            spread(renderer.mark(0.0)) > spread(renderer.mark(1.0)),
            "the hooks should occupy a wider span before they close",
        )
    }

    // The brand artwork fills the body #C01221 and the inner shadows #670002.
    // Rendering the mark in one flat red loses the depth those give it.
    @Test
    fun `the mark carries the artwork's inner shadows`() {
        val styles = IntroRenderer().markSpans(1.0).flatten().map { it.style }.toSet()

        assertTrue(IntroStyle.BRAND in styles, "the body should be drawn")
        assertTrue(IntroStyle.SHADOW in styles, "the inner shadows should be drawn")
    }

    @Test
    fun `shadows move with their hook`() {
        val renderer = IntroRenderer()

        // A map of which cells are shadow, so a change in position shows up
        // even when the shadows still span the same columns.
        fun shadowMask(progress: Double) = renderer.markSpans(progress).map { row ->
            buildString {
                row.forEach { span ->
                    repeat(span.text.length) { append(if (span.style == IntroStyle.SHADOW) 'S' else '.') }
                }
            }
        }

        assertFalse(
            shadowMask(0.0) == shadowMask(1.0),
            "the shadows should travel with the hooks rather than staying put",
        )
        assertTrue(shadowMask(1.0).any { it.contains('S') }, "the settled mark should still have shadows")
    }

    @Test
    fun `shadow red is distinct from body red at every colour tier`() = runBlocking {
        val trueColor = IntroTerminal()
        showIntro(trueColor, capabilities(), state, frameDelayMillis = 0)
        assertContains(trueColor.output, Ansi.BRAND_RED_TRUE_COLOR)
        assertContains(trueColor.output, Ansi.SHADOW_RED_TRUE_COLOR)

        val basic = IntroTerminal()
        showIntro(basic, capabilities(trueColor = false), state, frameDelayMillis = 0)
        assertContains(basic.output, Ansi.BRAND_RED_BASIC)
        assertContains(basic.output, Ansi.SHADOW_RED_BASIC)
        assertFalse(basic.output.contains("38;2"), "no truecolor escapes at the basic tier")
    }

    @Test
    fun `the mark is drawn with half blocks and not braille`() {
        val glyphs = IntroRenderer().mark(1.0).joinToString("").toSet()

        assertTrue(glyphs.isNotEmpty())
        assertTrue(
            glyphs.all { it == ' ' || it in setOf('\u2588', '\u2580', '\u2584') },
            "unexpected glyphs in the mark: $glyphs",
        )
        assertFalse(glyphs.any { it.code in 0x2800..0x28ff }, "braille should be gone")
    }

    @Test
    fun `the wordmark waits for the hooks to land`() {
        val renderer = IntroRenderer()
        val travelling = renderer.lines(state, markReveal = 0.5, visibleRows = 0, panelReveal = 0.0)
            .joinToString("\n") { it.plain() }

        assertFalse(travelling.contains("L A T C H"), "the wordmark appeared mid-travel")
        assertFalse(travelling.contains("v1.3.9"), "the version appeared mid-travel")

        assertContains(renderer.lines(state).joinToString("\n") { it.plain() }, "L A T C H")
    }

    // Caught by watching it run: the panel used to draw a bare corner and a
    // fully formed hint line while the mark was still assembling, which reads
    // as a broken box rather than one being drawn.
    @Test
    fun `the panel is absent until it starts drawing`() {
        val frame = IntroRenderer()
            .lines(state, markReveal = 0.5, visibleRows = 0, panelReveal = 0.0)
            .joinToString("\n") { it.plain() }

        listOf("\u256d", "\u256e", "\u2570", "\u256f", "\u2502").forEach {
            assertFalse(frame.contains(it), "panel character $it appeared before the panel was drawn")
        }
        assertFalse(frame.contains("--help"), "the hint appeared before the panel was drawn")
    }

    @Test
    fun `rows and hint wait for the border to close`() {
        val renderer = IntroRenderer()
        val halfDrawn = renderer.lines(state, visibleRows = 3, panelReveal = 0.5).joinToString("\n") { it.plain() }

        assertFalse(halfDrawn.contains("VIT-WIFI"), "rows should wait for the border")
        assertFalse(halfDrawn.contains("--help"), "the hint should wait for the border")

        val closed = renderer.lines(state).joinToString("\n") { it.plain() }
        assertContains(closed, "VIT-WIFI")
        assertContains(closed, "--help")
    }

    @Test
    fun `settled banner carries the wordmark version and rows`() {
        val text = IntroRenderer().lines(state).joinToString("\n") { it.plain() }

        assertContains(text, "L A T C H")
        assertContains(text, "v1.3.9")
        assertContains(text, "network")
        assertContains(text, "VIT-WIFI")
        assertContains(text, "latch-cli --help for commands")
    }

    @Test
    fun `a row without a value shows a spinner instead`() {
        val pending = state.copy(rows = listOf(IntroRow("status", null)))

        val first = IntroRenderer().lines(pending, spinnerTick = 0).joinToString("") { it.plain() }
        val second = IntroRenderer().lines(pending, spinnerTick = 1).joinToString("") { it.plain() }

        assertFalse(first == second, "the spinner should advance between ticks")
    }

    @Test
    fun `noninteractive output prints nothing`() = runBlocking {
        val terminal = IntroTerminal(interactive = false)

        showIntro(terminal, capabilities(interactive = false), state, frameDelayMillis = 0)

        assertEquals("", terminal.output)
    }

    @Test
    fun `a plain terminal gets one static banner and no escapes`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(ansi = false, noColor = true), state, frameDelayMillis = 0)

        assertFalse(terminal.output.contains(ESCAPE))
        // Printed once, so it is the trimmed banner: no padding rows for a
        // motion that never happens.
        val expected = IntroRenderer().lines(state, trimMark = true)
        assertEquals(expected.size, terminal.output.trimEnd('\n').lines().size)
        assertTrue(expected.size < IntroRenderer().lines(state).size, "the static banner should be the shorter one")
    }

    @Test
    fun `NO_COLOR is honoured even when ansi is available`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(noColor = true), state, frameDelayMillis = 0)

        assertFalse(terminal.output.contains(ESCAPE))
    }

    @Test
    fun `true color uses the brand red`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(), state, frameDelayMillis = 0)

        assertContains(terminal.output, Ansi.BRAND_RED_TRUE_COLOR)
    }

    @Test
    fun `basic ansi falls back to the standard red`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(trueColor = false), state, frameDelayMillis = 0)

        assertContains(terminal.output, Ansi.BRAND_RED_BASIC)
        assertFalse(terminal.output.contains("38;2"))
    }

    @Test
    fun `the cursor is hidden for the animation and restored after it`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(), state, frameDelayMillis = 0)

        assertTrue(terminal.output.startsWith(Ansi.HIDE_CURSOR))
        assertTrue(terminal.output.endsWith(Ansi.RESET + Ansi.SHOW_CURSOR))
    }

    // A banner wider than the terminal wraps, which breaks the cursor-up redraw
    // and smears every frame down the screen.
    @Test
    fun `a narrow terminal gets the one-line form instead`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(), state, width = INTRO_MIN_WIDTH - 1, frameDelayMillis = 0)

        assertFalse(terminal.output.contains(Ansi.HIDE_CURSOR), "no redraw should be attempted")
        assertFalse(terminal.output.contains("\u256d"), "the box should not be drawn")
        assertContains(terminal.output, "Latch")
        assertContains(terminal.output, "VIT-WIFI")
    }

    // Under a non-UTF-8 console the JVM substitutes every block and box glyph
    // on write, so the banner goes out as a wall of question marks.
    @Test
    fun `a console that cannot encode the glyphs gets the one-line form`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(unicode = false), state, frameDelayMillis = 0)

        assertFalse(terminal.output.contains("\u2588"), "no block glyphs should be emitted")
        assertFalse(terminal.output.contains("\u256d"), "no box drawing should be emitted")
        assertContains(terminal.output, "Latch")
        assertContains(terminal.output, "VIT-WIFI")
    }

    @Test
    fun `the one-line form is pure ascii`() {
        val text = compactIntro(state).joinToString("\n") { it.plain() }

        assertTrue(text.all { it.code < 128 }, "the fallback must survive a US-ASCII console: $text")
    }

    // Colour is the only thing separating body from shadow, so without it the
    // depth has to come from the glyph or the mark reads flat.
    @Test
    fun `shadows are shaded rather than coloured when there is no colour`() {
        val mark = IntroRenderer().markSpans(1.0)

        val mono = mark.joinToString("\n") { it.render(capabilities(ansi = false, noColor = true)) }
        assertTrue(mono.contains('\u2592'), "shadows should be shaded in monochrome:\n$mono")
        assertTrue(mono.contains('\u2588'), "the body should stay solid")

        val coloured = mark.joinToString("\n") { it.render(capabilities()) }
        assertFalse(coloured.contains('\u2592'), "shading should not leak into the colour tiers")
        assertContains(coloured, Ansi.SHADOW_RED_TRUE_COLOR)
    }

    @Test
    fun `the banner fits the width it claims to need`() {
        val widest = IntroRenderer().lines(state).maxOf { it.plain().length }

        assertTrue(widest <= INTRO_MIN_WIDTH, "banner is $widest columns but claims $INTRO_MIN_WIDTH")
    }

    // Most shells keep COLUMNS as a shell variable and never export it, so a
    // child JVM does not see it. Trusting it alone meant always assuming 80,
    // and the banner wrapped and smeared on anything narrower.
    @Test
    fun `terminal width asks the terminal when COLUMNS is missing`() {
        assertEquals(64, terminalWidth(emptyMap(), probe = { 64 }))
        assertEquals(80, terminalWidth(emptyMap(), probe = { null }), "no answer means the conventional 80")
        assertEquals(80, terminalWidth(emptyMap(), probe = { 0 }), "a nonsense answer is ignored")
    }

    @Test
    fun `COLUMNS wins when it is actually set`() {
        assertEquals(100, terminalWidth(mapOf("COLUMNS" to "100"), probe = { 64 }))
        assertEquals(64, terminalWidth(mapOf("COLUMNS" to "not-a-number"), probe = { 64 }))
        assertEquals(64, terminalWidth(mapOf("COLUMNS" to "0"), probe = { 64 }))
    }

    @Test
    fun `the panel follows the terminal instead of a fixed width`() {
        assertEquals(44, panelWidthFor(80), "a roomy terminal gets the full panel")
        assertEquals(44, panelWidthFor(46))
        assertEquals(42, panelWidthFor(44), "a snug terminal gets a narrower panel, not a wrapped one")
        assertEquals(32, panelWidthFor(20), "never narrower than it is worth drawing")
    }

    // The mark is padded so the hooks have room to travel, which leaves a blank
    // row above and below once they land.
    @Test
    fun `the settled banner has no padding rows left around the mark`() = runBlocking {
        val terminal = IntroTerminal()

        showIntro(terminal, capabilities(), state, frameDelayMillis = 0)

        val visible = terminal.output.substringAfterLast(Ansi.cursorUp(IntroRenderer().lines(state).size))
        val rows = visible.replace(Regex("\u001b\\[[0-9;?]*[a-zA-Z]"), "").trimEnd('\n').lines()
        assertTrue(rows.first().isNotBlank(), "the banner should start at the mark, not a blank row")
    }
}

private class IntroTerminal(override val interactive: Boolean = true) : TerminalIO {
    private val buffer = StringBuilder()
    val output: String get() = buffer.toString()

    override fun print(text: String) {
        buffer.append(text)
    }

    override fun println(text: String) {
        buffer.append(text).append('\n')
    }

    override fun readLine(prompt: String): String? = null
    override fun readSecret(prompt: String): CharArray? = null
}
