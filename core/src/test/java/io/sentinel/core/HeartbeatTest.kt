package io.sentinel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatTest {
    private val interval = 300_000L

    @Test fun regularBeatsReportNothing() {
        val beats = (0..3).map { Beat(it * interval, 1_000_000L + it * interval) }
        val (gaps, jumps) = Heartbeats.analyse(beats, interval)
        assertTrue(gaps.isEmpty()); assertTrue(jumps.isEmpty())
    }

    @Test fun missingBeatBecomesAGap() {
        val beats = listOf(Beat(0, 1_000), Beat(interval, 301_000), Beat(4 * interval, 1_201_000))
        val (gaps, _) = Heartbeats.analyse(beats, interval)
        assertEquals(1, gaps.size)
        assertEquals(interval, gaps[0].fromElapsedMs)
        assertEquals(4 * interval, gaps[0].toElapsedMs)
    }

    @Test fun wallClockMovedIsAClockJump() {
        val beats = listOf(Beat(0, 1_000), Beat(interval, 301_000 + 60_000))
        val (gaps, jumps) = Heartbeats.analyse(beats, interval)
        assertTrue(gaps.isEmpty())
        assertEquals(1, jumps.size)
        assertEquals(60_000L, jumps[0].wallDeltaMs - jumps[0].elapsedDeltaMs)
    }
}
