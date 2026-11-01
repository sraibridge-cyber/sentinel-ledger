package io.sentinel.core

// One reading of the device's own timing and clock.
data class Reading(val timerLatenessMs: Double, val clockSkewMs: Double)

// Maps a raw measure to a domain score in (0, 1]. Fixed and documented; NOT calibrated.
// A larger deviation gives a lower score. The scales and weights are our choice, not measured.
object DomainScores {
    const val MAPPING_VERSION = "v1-uncalibrated"
    const val TIMER_SCALE_MS = 5.0
    const val CLOCK_SCALE_MS = 50.0
    val WEIGHTS: Map<String, Double> = mapOf("timer" to 0.5, "clock" to 0.5)

    fun scores(r: Reading): Map<String, Double> = mapOf(
        "timer" to 1.0 / (1.0 + Math.max(0.0, r.timerLatenessMs) / TIMER_SCALE_MS),
        "clock" to 1.0 / (1.0 + Math.abs(r.clockSkewMs) / CLOCK_SCALE_MS),
    )
}

// The device's own baseline. tauMu is derived from the calibration samples, not chosen by hand.
data class Baseline(
    val samples: Int,
    val tauMu: Double,
    val muMin: Double,
    val muMedian: Double,
    val mappingVersion: String,
    val note: String,
)

sealed class CalibrationResult {
    data class Ready(val baseline: Baseline) : CalibrationResult()
    data class NotEnough(val have: Int, val need: Int) : CalibrationResult()
}

enum class Verdict { WITHIN_BASELINE, OUTSIDE_BASELINE }

object Calibration {
    // Below this many samples there is no threshold at all. A threshold from a handful of readings is a guess.
    const val MIN_SAMPLES = 30
    // tau sits this far below the lowest calibration mu, so ordinary noise does not trip it.
    const val MARGIN = 0.01

    // Weighted geometric mean of the domain scores (the framework's formula, with our weights).
    fun mu(r: Reading): Double {
        val s = DomainScores.scores(r)
        val w = DomainScores.WEIGHTS
        val total = w.values.sum()
        val logSum = w.entries.sumOf { (k, wk) -> wk * Math.log(s.getValue(k)) }
        return Math.exp(logSum / total)
    }

    fun build(samples: List<Reading>): CalibrationResult {
        if (samples.size < MIN_SAMPLES) return CalibrationResult.NotEnough(samples.size, MIN_SAMPLES)
        val mus = samples.map { mu(it) }.sorted()
        val min = mus.first()
        val median = mus[mus.size / 2]
        val tau = Math.max(0.0, min - MARGIN)
        return CalibrationResult.Ready(Baseline(
            samples = samples.size,
            tauMu = tau,
            muMin = min,
            muMedian = median,
            mappingVersion = DomainScores.MAPPING_VERSION,
            note = "Within-baseline means this reading matches this device's own calibration. It is not a judgement about who is watching.",
        ))
    }

    fun check(baseline: Baseline, r: Reading): Pair<Double, Verdict> {
        val m = mu(r)
        return m to (if (m >= baseline.tauMu) Verdict.WITHIN_BASELINE else Verdict.OUTSIDE_BASELINE)
    }
}
