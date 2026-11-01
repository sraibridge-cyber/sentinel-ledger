package io.sentinel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperAlertsTest {
    private val fg = listOf(ForegroundInterval("com.example.recorder", 1_000, 5_000))

    @Test fun attributesToForegroundAppAsInferenceOnly() {
        val a = Mapper.attribute(SensorEvent(Sensor.MICROPHONE, Change.START, 2_000, true, null), fg)
        assertEquals(Requester.InferredForeground("com.example.recorder"), a.requester)
        assertTrue(a.note.contains("does not confirm"))
    }

    @Test fun reportsNotKnownWhenNoForegroundAppMatches() {
        val a = Mapper.attribute(SensorEvent(Sensor.CAMERA, Change.START, 9_000, false, "0"), fg)
        assertEquals(Requester.NotKnown, a.requester)
        assertEquals("0", a.androidDetail)
    }

    @Test fun reportsNotKnownWhenUsageAccessIsOff() {
        val a = Mapper.attribute(SensorEvent(Sensor.MICROPHONE, Change.START, 2_000, true, null), emptyList())
        assertEquals(Requester.NotKnown, a.requester)
    }

    @Test fun alertsOnSensorStartWhileScreenOff() {
        val a = Mapper.attribute(SensorEvent(Sensor.MICROPHONE, Change.START, 2_000, false, null), fg)
        val alerts = Alerts.forSensor(a)
        assertEquals(1, alerts.size)
        assertEquals("sensor-in-use-screen-off", alerts[0].rule)
    }

    @Test fun noAlertWhenScreenOnOrWhenStopping() {
        val on = Mapper.attribute(SensorEvent(Sensor.CAMERA, Change.START, 2_000, true, null), fg)
        val stop = Mapper.attribute(SensorEvent(Sensor.CAMERA, Change.STOP, 2_000, false, null), fg)
        assertTrue(Alerts.forSensor(on).isEmpty())
        assertTrue(Alerts.forSensor(stop).isEmpty())
    }

    @Test fun alertsOnWatchedSettingsOnly() {
        assertEquals(1, Alerts.forSetting(SettingsChange("Device administrators", "added: X", 1)).size)
        assertEquals(1, Alerts.forSetting(SettingsChange("VPN", "on", 1)).size)
        assertTrue(Alerts.forSetting(SettingsChange("Screen brightness", "70", 1)).isEmpty())
    }
}
