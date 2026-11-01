package io.sentinel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {
    private val steady = Reading(timerLatenessMs = 0.5, clockSkewMs = 1.0)

    @Test fun weightsSumToOne() {
        assertEquals(1.0, DomainScores.WEIGHTS.values.sum(), 1e-12)
    }

    @Test fun perfectReadingGivesMuOne() {
        assertEquals(1.0, Calibration.mu(Reading(0.0, 0.0)), 1e-12)
    }

    @Test fun refusesToSetAThresholdWithTooFewSamples() {
        val r = Calibration.build(List(Calibration.MIN_SAMPLES - 1) { steady })
        assertTrue(r is CalibrationResult.NotEnough)
    }

    @Test fun thresholdComesFromTheCalibrationSamples() {
        val samples = List(40) { i -> Reading(0.4 + (i % 5) * 0.1, 1.0 + (i % 3)) }
        val ready = Calibration.build(samples) as CalibrationResult.Ready
        assertTrue(ready.baseline.tauMu < ready.baseline.muMin)
        assertEquals(40, ready.baseline.samples)
        assertEquals(DomainScores.MAPPING_VERSION, ready.baseline.mappingVersion)
    }

    @Test fun ordinaryReadingIsWithinBaselineAndADeviantOneIsNot() {
        val ready = Calibration.build(List(40) { steady }) as CalibrationResult.Ready
        assertEquals(Verdict.WITHIN_BASELINE, Calibration.check(ready.baseline, steady).second)
        val deviant = Reading(timerLatenessMs = 100.0, clockSkewMs = 1.0)
        assertEquals(Verdict.OUTSIDE_BASELINE, Calibration.check(ready.baseline, deviant).second)
    }
}
