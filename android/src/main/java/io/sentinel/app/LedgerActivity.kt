package io.sentinel.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import io.csslabs.sdk.Chain
import io.csslabs.sdk.FileLogStore
import java.io.File

// A plain screen: start, stop, calibrate, and check the chain. Starting is always the owner's choice.
class LedgerActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var cleanTick: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        status = TextView(this)
        box.addView(status)
        cleanTick = CheckBox(this).apply {
            text = "I have checked this phone by other means and believe it is clean. (Needed to calibrate.)"
        }
        box.addView(cleanTick)
        box.addView(Button(this).apply { text = "Start recording"; setOnClickListener {
            startForegroundService(Intent(this@LedgerActivity, LedgerService::class.java)); refresh() } })
        box.addView(Button(this).apply { text = "Start and calibrate (30 minutes)"; setOnClickListener {
            if (!cleanTick.isChecked) { status.text = "Tick the clean-phone statement first. Calibration needs it."; return@setOnClickListener }
            startForegroundService(Intent(this@LedgerActivity, LedgerService::class.java)
                .putExtra(LedgerService.EXTRA_CALIBRATE, true)); refresh() } })
        box.addView(Button(this).apply { text = "Stop recording"; setOnClickListener {
            stopService(Intent(this@LedgerActivity, LedgerService::class.java)); refresh() } })
        box.addView(Button(this).apply { text = "Check the chain"; setOnClickListener { refresh() } })
        setContentView(box)
        refresh()
    }

    private fun refresh() {
        val store = FileLogStore(File(filesDir, "sentinel/ledger.jsonl"))
        val all = store.all()
        val v = Chain.verify(all)
        val baseline = all.lastOrNull { it.type == "CALIBRATION" }
        val baselineLine = if (baseline == null) "Baseline: none yet." else "Baseline: set (${baseline.summary})."
        status.text = "Records: ${all.size}\nChain: ${if (v.ok) "verifies" else "BROKEN at ${v.brokenAt}"}\n" +
            "$baselineLine\nUsage access: grant in Android settings to add the app in the foreground to each sensor event."
    }
}
