package io.sentinel.core

// One start or stop of a sensor, as Android reports it. Android gives the time, the sensor,
// and whatever detail the platform exposes (for example a camera id). It does not give the
// requesting app to an ordinary app, so that field is absent here by design.
enum class Sensor { MICROPHONE, CAMERA }
enum class Change { START, STOP }

data class SensorEvent(
    val sensor: Sensor,
    val change: Change,
    val atMillis: Long,
    val screenOn: Boolean,
    val androidDetail: String?,
)

// A settings change the app can see directly (administrators, certificates, listeners, VPN, proxy).
data class SettingsChange(val setting: String, val detail: String, val atMillis: Long)

// The foreground timeline. Only available when the owner grants usage access in Android settings.
data class ForegroundInterval(val packageName: String, val startMillis: Long, val endMillis: Long)
