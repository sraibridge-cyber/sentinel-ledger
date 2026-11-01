package io.csslabs.sdk

import java.security.MessageDigest

// The device signals an app may ask about. Every signal is off until the app
// turns it on, and each one maps to the reading fields it produces.
enum class Signal(val id: String, val title: String, val fields: List<String>) {
    ACCESSIBILITY("accessibility", "Accessibility services", listOf("accessibilityServices")),
    NOTIFICATIONS("notifications", "Notification access", listOf("notificationListeners")),
    ADMINS("admins", "Device administrators", listOf("deviceAdmins")),
    CERTIFICATES("certificates", "User-installed certificates", listOf("userCaCertificates")),
    NETWORK("network", "VPN and proxy", listOf("vpnActive", "proxy")),
    DEVELOPER("developer", "Developer options and USB debugging", listOf("developerOptions", "usbDebugging")),
    INDICATORS("indicators", "Audio and camera in use (counts only)", listOf("microphoneRecordingClients", "camerasInUse"));

    companion object {
        fun fromId(id: String): Signal? = values().firstOrNull { it.id == id }
        fun forField(field: String): Signal? = values().firstOrNull { field in it.fields }
    }
}

// A change between two readings. It says that a setting or indicator changed.
// It does not say who changed it.
data class Flag(val code: String, val text: String)

object SignalParsers {
    // Settings.Secure lists enabled components as "pkg/cls:pkg2/cls2".
    fun parseComponentList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun packageOf(component: String): String = component.substringBefore('/')

    // User-installed certificates have aliases starting with "user:".
    fun userCaAliases(aliases: List<String>): List<String> = aliases.filter { it.startsWith("user:") }

    fun sha256HexBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object SignalChanges {

    private fun list(m: Map<String, Any?>?, key: String): List<String>? =
        (m?.get(key) as? List<*>)?.map { it.toString() }

    private fun caDigests(m: Map<String, Any?>?): List<String>? {
        val ca = m?.get("userCaCertificates") as? Map<*, *> ?: return null
        return (ca["sha256"] as? List<*>)?.map { it.toString() }
    }

    private fun count(m: Map<String, Any?>?, key: String): Long? = (m?.get(key) as? Number)?.toLong()

    private fun bool(m: Map<String, Any?>?, key: String): Boolean? = m?.get(key) as? Boolean

    // Compares only the fields present in both readings. A signal that was off
    // in either reading produces no flag, and so does a first reading.
    fun between(prev: Map<String, Any?>?, cur: Map<String, Any?>?): List<Flag> {
        if (prev == null || cur == null) return emptyList()
        val flags = ArrayList<Flag>()

        val a = list(prev, "accessibilityServices")
        val b = list(cur, "accessibilityServices")
        if (a != null && b != null) {
            for (s in b.filter { it !in a }) {
                flags.add(Flag("ACCESSIBILITY_ON", "An accessibility service was turned on since the last reading: $s. Accessibility services can read what is on screen and act for you. You may have turned it on yourself."))
            }
            for (s in a.filter { it !in b }) {
                flags.add(Flag("ACCESSIBILITY_OFF", "An accessibility service was turned off since the last reading: $s."))
            }
        }

        val n1 = list(prev, "notificationListeners")
        val n2 = list(cur, "notificationListeners")
        if (n1 != null && n2 != null) {
            for (s in n2.filter { it !in n1 }) {
                flags.add(Flag("NOTIFICATION_LISTENER_ON", "An app was given access to notifications since the last reading: $s."))
            }
        }

        val d1 = list(prev, "deviceAdmins")
        val d2 = list(cur, "deviceAdmins")
        if (d1 != null && d2 != null) {
            for (s in d2.filter { it !in d1 }) {
                flags.add(Flag("DEVICE_ADMIN_ON", "A device administrator was added since the last reading: $s. Device administrators can lock the device, wipe it, and set policy."))
            }
        }

        val c1 = caDigests(prev)
        val c2 = caDigests(cur)
        if (c1 != null && c2 != null) {
            val added = c2.count { it !in c1 }
            if (added > 0) {
                flags.add(Flag("USER_CA_ADDED", "$added user-installed certificate authorit${if (added == 1) "y" else "ies"} appeared since the last reading. A user certificate authority can let someone read encrypted traffic from this device."))
            }
        }

        val v1 = bool(prev, "vpnActive")
        val v2 = bool(cur, "vpnActive")
        if (v1 != null && v2 != null && v1 != v2) {
            flags.add(if (v2) Flag("VPN_ON", "A VPN turned on since the last reading.") else Flag("VPN_OFF", "A VPN turned off since the last reading."))
        }

        if (prev.containsKey("proxy") && cur.containsKey("proxy") && prev["proxy"] != cur["proxy"]) {
            val p = cur["proxy"]
            flags.add(
                if (p != null) Flag("PROXY_CHANGED", "A network proxy is set: $p. A proxy can route web traffic through another machine.")
                else Flag("PROXY_CHANGED", "The network proxy was removed since the last reading.")
            )
        }

        if (bool(prev, "developerOptions") == false && bool(cur, "developerOptions") == true) {
            flags.add(Flag("DEVELOPER_OPTIONS_ON", "Developer options were turned on since the last reading."))
        }
        if (bool(prev, "usbDebugging") == false && bool(cur, "usbDebugging") == true) {
            flags.add(Flag("USB_DEBUGGING_ON", "USB debugging was turned on since the last reading."))
        }

        val m1 = count(prev, "microphoneRecordingClients")
        val m2 = count(cur, "microphoneRecordingClients")
        if (m1 == 0L && m2 != null && m2 > 0L) {
            flags.add(Flag("MICROPHONE_IN_USE", "$m2 app(s) started recording audio since the last reading. Android does not say which apps from here."))
        }

        val k1 = count(prev, "camerasInUse")
        val k2 = count(cur, "camerasInUse")
        if (k1 == 0L && k2 != null && k2 > 0L) {
            flags.add(Flag("CAMERA_IN_USE", "A camera became unavailable to this app since the last reading, which usually means another app is using it. Android does not say which app from here."))
        }

        return flags
    }
}
