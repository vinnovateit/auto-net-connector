package com.vinnovateit.latch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LatchCoreVersionTest {

    // latch-version.properties is a template; if processResources stops
    // substituting it, LatchCore.VERSION becomes the literal "${latchVersion}"
    // and every artifact reports that as its version. That shipped once
    // already -- the v1.3.9 release job caught it only at the packaged CLI
    // smoke test, after the tag was pushed.
    @Test
    fun `version resource is substituted at build time`() {
        val expected = checkNotNull(System.getProperty("latchVersion")) {
            "desktopTest must be given the latchVersion system property"
        }
        assertEquals(expected, LatchCore.VERSION)
    }

    @Test
    fun `version carries no unresolved template token`() {
        assertFalse(LatchCore.VERSION.contains('$'), "unresolved token in ${LatchCore.VERSION}")
    }
}
