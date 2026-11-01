package io.sentinel.core

// An alert states the reading and the rule it broke. It never names a cause and never changes anything.
data class Alert(val atMillis: Long, val rule: String, val reason: String)

object Alerts {
    // Settings that should be reviewed the moment they change.
    private val watchedSettings = setOf("administrator", "certificate", "listener", "accessibility", "vpn", "proxy")

    fun forSensor(a: Attribution): List<Alert> {
        if (a.change != Change.START || a.screenOn) return emptyList()
        return listOf(
            Alert(
                a.atMillis,
                "sensor-in-use-screen-off",
                "${a.sensor.name.lowercase()} was started while the screen was off. ${a.note}",
            )
        )
    }

    fun forSetting(c: SettingsChange): List<Alert> {
        val key = c.setting.lowercase()
        if (watchedSettings.none { key.contains(it) }) return emptyList()
        return listOf(Alert(c.atMillis, "settings-changed", "${c.setting} changed: ${c.detail}"))
    }
}
