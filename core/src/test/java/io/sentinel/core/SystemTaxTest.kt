package io.sentinel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemTaxTest {
    @Test fun reportsTheChangeInTimerLateness() {
        val t = Tax.around(listOf(0.5, 0.7, 0.6), listOf(2.5, 2.7, 2.6))
        assertEquals(0.6, t!!.beforeMeanMs, 1e-9)
        assertEquals(2.6, t.afterMeanMs, 1e-9)
        assertEquals(2.0, t.deltaMs, 1e-9)
    }

    @Test fun returnsNothingWithoutBothWindows() {
        assertNull(Tax.around(emptyList(), listOf(1.0)))
        assertNull(Tax.around(listOf(1.0), emptyList()))
    }
}
