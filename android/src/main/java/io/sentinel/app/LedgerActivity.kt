package io.sentinel.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.csslabs.sdk.Chain
import io.csslabs.sdk.FileLogStore
import java.io.File

// A plain screen: start, stop, and check the chain. Starting is always the owner's choice.
class LedgerActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        status = TextView(this)
        box.addView(status)
        box.addView(Button(this).apply { text = "Start recording"; setOnClickListener {
            startForegroundService(Intent(this@LedgerActivity, LedgerService::class.java)); refresh() } })
        box.addView(Button(this).apply { text = "Stop recording"; setOnClickListener {
            stopService(Intent(this@LedgerActivity, LedgerService::class.java)); refresh() } })
        box.addView(Button(this).apply { text = "Check the chain"; setOnClickListener { refresh() } })
        setContentView(box)
        refresh()
    }

    private fun refresh() {
        val store = FileLogStore(File(filesDir, "sentinel/ledger.jsonl"))
        val v = Chain.verify(store.all())
        status.text = "Records: ${store.all().size}\nChain: ${if (v.ok) "verifies" else "BROKEN at ${v.brokenAt}"}\n" +
            "Usage access: grant in Android settings to add the app in the foreground to each sensor event."
    }
}
