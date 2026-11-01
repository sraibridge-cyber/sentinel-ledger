package io.csslabs.sdk

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Collections

// Reads one signal from Android. Every read is a read of a setting or a count;
// nothing here opens a network connection, reads audio, video or screen
// contents, or reads another app's data. Values Android does not show to this
// app are reported as "not visible to this app" rather than guessed.
class AndroidSignalSource(private val context: Context, private val indicators: IndicatorWatch) : SignalSource {

    override fun read(signal: Signal): Map<String, Any?> {
        val cr = context.contentResolver
        return when (signal) {
            Signal.ACCESSIBILITY -> mapOf(
                "accessibilityServices" to SignalParsers.parseComponentList(
                    Settings.Secure.getString(cr, "enabled_accessibility_services")
                ),
            )
            Signal.NOTIFICATIONS -> mapOf(
                "notificationListeners" to SignalParsers.parseComponentList(
                    Settings.Secure.getString(cr, "enabled_notification_listeners")
                ),
            )
            Signal.ADMINS -> mapOf("deviceAdmins" to readAdmins())
            Signal.CERTIFICATES -> mapOf("userCaCertificates" to readUserCa())
            Signal.NETWORK -> {
                val (vpn, proxy) = readNetwork()
                mapOf("vpnActive" to vpn, "proxy" to proxy)
            }
            Signal.DEVELOPER -> mapOf(
                "developerOptions" to (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1),
                "usbDebugging" to (Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1),
            )
            Signal.INDICATORS -> indicators.snapshot()
        }
    }

    private fun readAdmins(): Any {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            (dpm.activeAdmins ?: emptyList()).map { it.flattenToShortString() }
        } catch (e: SecurityException) {
            "not visible to this app"
        }
    }

    // Counts and SHA-256 fingerprints of user-installed certificates. The
    // certificate contents are not stored.
    private fun readUserCa(): Map<String, Any?>? {
        return try {
            val store = KeyStore.getInstance("AndroidCAStore")
            store.load(null)
            val user = SignalParsers.userCaAliases(Collections.list(store.aliases()))
            val digests = user.mapNotNull { alias ->
                (store.getCertificate(alias) as? X509Certificate)?.encoded?.let { SignalParsers.sha256HexBytes(it) }
            }
            mapOf("count" to user.size.toLong(), "sha256" to digests)
        } catch (e: Exception) {
            null
        }
    }

    // Returns (vpnActive, proxy as "host:port" or null).
    private fun readNetwork(): Pair<Boolean?, String?> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val vpn: Boolean? = try {
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
        } catch (e: SecurityException) {
            null
        }
        val proxy: String? = try {
            cm.defaultProxy?.takeIf { !it.host.isNullOrEmpty() }?.let { "${it.host}:${it.port}" }
        } catch (e: SecurityException) {
            null
        }
        return Pair(vpn, proxy)
    }
}

// Counts active audio recordings and cameras that are not free to this app,
// using Android's availability callbacks. They report THAT something is
// recording or busy, not which app. This SDK never uses the microphone or camera.
// The callbacks run only while the process is alive.
class IndicatorWatch(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val lock = Any()
    private val unavailable = HashSet<String>()
    @Volatile private var micClients: Int? = null
    private var started = false

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        val handler = Handler(Looper.getMainLooper())
        micClients = try {
            audio.activeRecordingConfigurations.size
        } catch (e: Exception) {
            null
        }
        audio.registerAudioRecordingCallback(object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                micClients = configs.size
            }
        }, handler)
        cameras.registerAvailabilityCallback(object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                synchronized(lock) { unavailable.remove(cameraId) }
            }

            override fun onCameraUnavailable(cameraId: String) {
                synchronized(lock) { unavailable.add(cameraId) }
            }
        }, handler)
    }

    fun snapshot(): Map<String, Any?> = mapOf(
        "microphoneRecordingClients" to micClients?.toLong(),
        "camerasInUse" to synchronized(lock) { unavailable.size.toLong() },
    )
}

// Consent switches stored in private app preferences. Off by default.
class PrefsConsent(context: Context) : ConsentBackend {
    private val prefs = context.getSharedPreferences("csslabs_sdk_consent", Context.MODE_PRIVATE)

    override fun isGranted(signal: Signal): Boolean = prefs.getBoolean(signal.id, false)

    override fun set(signal: Signal, granted: Boolean) {
        prefs.edit().putBoolean(signal.id, granted).commit()
    }
}
