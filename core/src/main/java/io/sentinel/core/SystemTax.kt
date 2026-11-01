package io.sentinel.core

// The device's own cost around a sensor start or stop: how late a fixed timer fires before and
// after the event. A higher number is a change in the device's timing, not proof of its cause.
data class SystemTax(
    val beforeMeanMs: Double,
    val afterMeanMs: Double,
    val deltaMs: Double,
    val note: String,
)

object Tax {
    fun around(beforeLatenessMs: List<Double>, afterLatenessMs: List<Double>): SystemTax? {
        if (beforeLatenessMs.isEmpty() || afterLatenessMs.isEmpty()) return null
        val b = beforeLatenessMs.average()
        val a = afterLatenessMs.average()
        return SystemTax(
            beforeMeanMs = b,
            afterMeanMs = a,
            deltaMs = a - b,
            note = "Timer lateness changed by %.1f ms around this event. Sleep, load and power settings can also cause this.".format(a - b),
        )
    }
}
