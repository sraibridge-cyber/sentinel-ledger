package io.sentinel.core

// A heartbeat: the time since boot (which keeps counting through sleep, and which a clock change does not move)
// and the wall clock time, written at a fixed interval while the logger runs.
data class Beat(val elapsedMs: Long, val wallMs: Long)

// The logger was not recording for this span. Silence in the log is recorded as a gap, never ignored.
data class LedgerGap(val fromElapsedMs: Long, val toElapsedMs: Long, val note: String)

// The wall clock moved relative to the steady clock between two heartbeats.
data class ClockJump(val atElapsedMs: Long, val wallDeltaMs: Long, val elapsedDeltaMs: Long, val note: String)

object Heartbeats {
    // Allowed drift between the two clocks before a change is reported (normal sync can move a few ms).
    const val TOLERANCE_MS = 2_000L

    fun analyse(beats: List<Beat>, intervalMs: Long): Pair<List<LedgerGap>, List<ClockJump>> {
        val gaps = ArrayList<LedgerGap>()
        val jumps = ArrayList<ClockJump>()
        for (i in 1 until beats.size) {
            val a = beats[i - 1]
            val b = beats[i]
            val elapsed = b.elapsedMs - a.elapsedMs
            val wall = b.wallMs - a.wallMs
            if (elapsed > intervalMs * 3 / 2) {
                gaps.add(LedgerGap(a.elapsedMs, b.elapsedMs,
                    "The logger was not recording for about ${elapsed / 1000} seconds (no heartbeat)."))
            }
            if (Math.abs(wall - elapsed) > TOLERANCE_MS) {
                jumps.add(ClockJump(b.elapsedMs, wall, elapsed,
                    "The wall clock moved by ${wall - elapsed} ms relative to the device's steady clock."))
            }
        }
        return gaps to jumps
    }
}
