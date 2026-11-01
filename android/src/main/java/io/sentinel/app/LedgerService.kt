package io.sentinel.app

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import io.csslabs.sdk.Chain
import io.csslabs.sdk.ChainRecord
import io.csslabs.sdk.CsJson
import io.csslabs.sdk.FileLogStore
import io.sentinel.core.Alerts
import io.sentinel.core.Change
import io.sentinel.core.ForegroundInterval
import io.sentinel.core.Mapper
import io.sentinel.core.Requester
import io.sentinel.core.Sensor
import io.sentinel.core.SensorEvent
import java.io.File
import java.time.Instant

// Writes sensor start and stop events, and alerts, into the sealed log. It only alerts and logs;
// it never changes a setting or a file other than its own log. Runs only while the owner has started it.
class LedgerService : Service() {
    private lateinit var log: FileLogStore
    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true
    private val micActive = HashSet<String>()
    private val cameraBusy = HashSet<String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> screenOn = false
            }
        }
    }

    private val audioCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val now = configs.size
            if (now > 0 && micActive.isEmpty()) emit(Sensor.MICROPHONE, Change.START, "recording clients: $now")
            if (now == 0 && micActive.isNotEmpty()) emit(Sensor.MICROPHONE, Change.STOP, "no recording clients")
            micActive.clear()
            if (now > 0) micActive.add("active")
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            if (cameraBusy.add(cameraId)) emit(Sensor.CAMERA, Change.START, cameraId)
        }
        override fun onCameraAvailable(cameraId: String) {
            if (cameraBusy.remove(cameraId)) emit(Sensor.CAMERA, Change.STOP, cameraId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        log = FileLogStore(File(filesDir, "sentinel/ledger.jsonl"))
        if (log.head() == null) append("GENESIS", "Sentinel Ledger started", linkedMapOf("app" to "sentinel"))
        val pm = getSystemService(PowerManager::class.java)
        screenOn = pm.isInteractive
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        getSystemService(AudioManager::class.java).registerAudioRecordingCallback(audioCallback, handler)
        getSystemService(CameraManager::class.java).registerAvailabilityCallback(cameraCallback, handler)
        startAsForeground()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(audioCallback)
        getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(cameraCallback)
        append("STOPPED", "Monitoring stopped; events after this point are not recorded", linkedMapOf())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun emit(sensor: Sensor, change: Change, detail: String) {
        val now = System.currentTimeMillis()
        val event = SensorEvent(sensor, change, now, screenOn, detail)
        val attribution = Mapper.attribute(event, foregroundTimeline(now - 60_000, now))
        val who = when (val r = attribution.requester) {
            is Requester.InferredForeground -> "foreground: ${r.packageName}"
            Requester.NotKnown -> "requester not reported by Android"
        }
        append("SENSOR", "${sensor.name.lowercase()} ${change.name.lowercase()}; $who", linkedMapOf(
            "sensor" to sensor.name, "change" to change.name, "screenOn" to screenOn,
            "androidDetail" to detail, "requester" to who, "note" to attribution.note,
        ))
        for (alert in Alerts.forSensor(attribution)) {
            append("ALERT", alert.rule, linkedMapOf("rule" to alert.rule, "reason" to alert.reason))
            notifyOwner(alert.reason)
        }
    }

    // Reads the foreground timeline only if the owner has granted usage access. Otherwise returns nothing.
    private fun foregroundTimeline(from: Long, to: Long): List<ForegroundInterval> {
        val ops = getSystemService(AppOpsManager::class.java)
        val granted = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) ==
            AppOpsManager.MODE_ALLOWED
        if (!granted) return emptyList()
        val events = getSystemService(UsageStatsManager::class.java).queryEvents(from, to)
        val out = ArrayList<ForegroundInterval>()
        var currentPkg: String? = null
        var currentStart = 0L
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { currentPkg = e.packageName; currentStart = e.timeStamp }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentPkg != null) out.add(ForegroundInterval(currentPkg!!, currentStart, e.timeStamp))
                    currentPkg = null
                }
            }
        }
        currentPkg?.let { out.add(ForegroundInterval(it, currentStart, to)) }
        return out
    }

    private fun append(type: String, summary: String, payload: Map<String, Any?>) {
        val ts = Instant.ofEpochMilli(System.currentTimeMillis()).toString()
        for (attempt in 1..5) {
            val head: ChainRecord? = log.head()
            val rec = Chain.build(head, type, summary, payload, ts)
            if (log.appendIfHead(head?.seq ?: 0L, head?.hash, rec)) return
        }
        throw IllegalStateException("could not write to the ledger")
    }

    private fun startAsForeground() {
        val channelId = "sentinel-monitoring"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channelId, "Monitoring", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Sentinel Ledger is recording sensor events")
            .setContentText("Stop it from the app at any time.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        startForeground(1, notification)
    }

    private fun notifyOwner(text: String) {
        val channelId = "sentinel-alerts"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channelId, "Alerts", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, channelId)
            .setContentTitle("Sentinel Ledger alert")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .build()
        nm.notify(text.hashCode(), n)
    }
}
