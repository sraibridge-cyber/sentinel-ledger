package io.csslabs.sdk

import android.content.Context
import java.io.File

// The public entry point of the CSS Labs Android SDK.
//
// What it does: records readings of the device signals you switch on into a
// SHA-256 hash-chained local log, lets you check that log, and exports it.
// What it does not do: prove that nobody is monitoring the device. A flag is a
// change consistent with monitoring, not proof of it. The SDK sends nothing
// off the device, reads no audio, video or screen contents, and reads no
// other app's data.
class CssLabsMonitor private constructor(private val core: CssLabsCore) {

    // Turns signals on or off. Everything starts off.
    fun setConsent(signal: Signal, granted: Boolean): Boolean = core.setConsent(signal, granted)

    fun setConsent(signals: List<Signal>, granted: Boolean): Boolean = core.setConsent(signals, granted)

    fun granted(): List<Signal> = core.grantedSignals()

    // Reads every granted signal and records one entry. Call from a background
    // thread. With nothing granted it reads nothing and records nothing.
    fun sample(): SampleResult = core.sample()

    fun verify(): Verification = core.verify()

    fun sanity(): SanityStatus = core.sanity()

    fun exportText(): String = core.exportText()

    // Writes the export to a file in the given folder and returns it.
    fun exportTo(folder: File): File {
        folder.mkdirs()
        val name = "csslabs-export-${System.currentTimeMillis()}.json"
        val file = File(folder, name)
        file.writeText(exportText(), Charsets.UTF_8)
        return file
    }

    companion object {
        const val VERSION = CssLabsCore.SDK_VERSION

        // Opens the monitor for this app. The log lives in the app's private
        // storage. Clearing the app's data erases it.
        @JvmStatic
        fun open(context: Context, scorer: CoherenceScorer = NoScorer): CssLabsMonitor {
            val app = context.applicationContext
            val indicators = IndicatorWatch(app)
            indicators.start()
            val log = FileLogStore(File(app.filesDir, "csslabs/chain.jsonl"))
            val core = CssLabsCore(log, PrefsConsent(app), AndroidSignalSource(app, indicators), scorer)
            return CssLabsMonitor(core)
        }

        // Checks an export text, on any device. Pass the head hash you saved
        // somewhere else to catch a rewritten log.
        @JvmStatic
        fun checkExport(text: String, expectedHeadHash: String? = null): ExportResult =
            CssLabsCore.checkExport(text, expectedHeadHash)
    }
}
