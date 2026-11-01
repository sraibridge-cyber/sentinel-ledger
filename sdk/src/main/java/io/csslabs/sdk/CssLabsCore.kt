package io.csslabs.sdk

import java.time.Instant

// Reads one signal. Implementations must read only what the signal names.
interface SignalSource {
    fun read(signal: Signal): Map<String, Any?>
}

data class SampleResult(
    val sampled: Boolean,
    val reason: String?,
    val reading: Map<String, Any?>,
    val flags: List<Flag>,
    val coherence: CoherenceReport,
    val notRead: List<String>,
    val record: ChainRecord?,
)

data class ExportResult(
    val ok: Boolean,
    val reason: String?,
    val count: Int = 0,
    val headHash: String? = null,
    val brokenAt: Long? = null,
)

// The platform-free core: consent, sampling, change flags, the sanity status,
// and export. Android storage and readers are passed in, so this class runs
// under plain JVM unit tests.
class CssLabsCore(
    private val log: LogStore,
    private val consent: ConsentBackend,
    private val source: SignalSource,
    private val scorer: CoherenceScorer = NoScorer,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var baseline: Map<String, Any?>? = null
    private var baselineLoaded = false

    init {
        ensureGenesis()
    }

    private fun stamp(): String = Instant.ofEpochMilli(clock()).toString()

    private fun ensureGenesis() {
        if (log.head() != null) return
        val rec = Chain.build(null, "GENESIS", "CSS Labs SDK chain started", linkedMapOf("sdk" to SDK_VERSION), stamp())
        log.appendIfHead(0L, null, rec)
    }

    private fun append(type: String, summary: String, payload: Map<String, Any?>): ChainRecord {
        for (attempt in 1..5) {
            val head = log.head()
            val rec = Chain.build(head, type, summary, payload, stamp())
            if (log.appendIfHead(head?.seq ?: 0L, head?.hash, rec)) return rec
        }
        throw IllegalStateException("could not write to the log: the chain head kept changing")
    }

    // Turns one or more signals on or off, in a single CONSENT record.
    @Synchronized
    fun setConsent(signals: List<Signal>, granted: Boolean): Boolean {
        val changing = signals.filter { consent.isGranted(it) != granted }
        if (changing.isEmpty()) return false
        for (s in changing) consent.set(s, granted)
        if (!granted) baseline = null
        append(
            "CONSENT",
            "${changing.joinToString(", ") { it.id }} ${if (granted) "on" else "off"}",
            linkedMapOf("signals" to changing.map { it.id }, "granted" to granted, "method" to "switch"),
        )
        return true
    }

    fun setConsent(signal: Signal, granted: Boolean): Boolean = setConsent(listOf(signal), granted)

    @Synchronized
    fun grantedSignals(): List<Signal> = Signal.values().filter { consent.isGranted(it) }

    private fun loadBaseline() {
        if (baselineLoaded) return
        baselineLoaded = true
        var base: Map<String, Any?>? = null
        for (r in log.all()) {
            if (r.type == "CONSENT" && r.payload["granted"] == false) base = null
            if (r.type == "OBSERVATION") {
                @Suppress("UNCHECKED_CAST")
                val readings = r.payload["readings"] as? Map<String, Any?>
                if (readings != null) base = readings
            }
        }
        baseline = base
    }

    // Reads every granted signal and writes one OBSERVATION record. With no
    // signal granted it reads nothing and writes nothing.
    @Synchronized
    fun sample(): SampleResult {
        val asked = grantedSignals()
        if (asked.isEmpty()) {
            return SampleResult(false, "no permissions granted", emptyMap(), emptyList(), noCoherence("no signal is granted"), emptyList(), null)
        }
        val reading = LinkedHashMap<String, Any?>()
        val notRead = ArrayList<String>()
        for (s in asked) {
            try {
                reading.putAll(source.read(s))
            } catch (e: Exception) {
                notRead.add("${s.id}: ${e.message ?: "read failed"}")
            }
        }
        loadBaseline()
        val flags = SignalChanges.between(baseline, reading)
        val coherence = coherenceFor(reading)
        val rec = append(
            "OBSERVATION",
            "readings: ${asked.joinToString(", ") { it.id }}; ${if (coherence.value == null) "coherence not computed" else "coherence ${coherence.value} (uncalibrated)"}",
            linkedMapOf(
                "readings" to reading,
                "notRead" to notRead,
                "flags" to flags.map { linkedMapOf("code" to it.code, "text" to it.text) },
                "coherence" to coherence.value,
            ),
        )
        baseline = reading
        return SampleResult(true, null, reading, flags, coherence, notRead, rec)
    }

    private fun noCoherence(note: String): CoherenceReport = CoherenceReport(null, false, null, note)

    private fun coherenceFor(reading: Map<String, Any?>): CoherenceReport {
        if (scorer.dimensions.isEmpty()) {
            return noCoherence("No scoring function was supplied. Device signals have no defined mapping onto the framework's dimensions yet.")
        }
        val scores = scorer.score(reading) ?: return noCoherence("The scoring function could not score this reading.")
        val mu = Coherence.geometricMean(scores, scorer.weights)
            ?: return noCoherence("The scores did not form a valid geometric mean (need two or more dimensions, weights summing to 1, scores in (0, 1]).")
        return CoherenceReport(mu, false, null, "Uncalibrated: no threshold (tau) has been set from reference data.")
    }

    fun verify(): Verification = Chain.verify(log.all())

    // Sanity status: R is the log's integrity; G is whether every reading was
    // taken while the signals in it were granted. Tau is not set.
    @Synchronized
    fun sanity(): SanityStatus {
        val v = verify()
        val granted = HashSet<Signal>()
        var gateHeld = true
        for (r in log.all()) {
            if (r.type == "CONSENT") {
                val ids = (r.payload["signals"] as? List<*>)?.map { it.toString() } ?: emptyList()
                for (id in ids) {
                    val s = Signal.fromId(id) ?: continue
                    if (r.payload["granted"] == true) granted.add(s) else granted.remove(s)
                }
            }
            if (r.type == "OBSERVATION") {
                @Suppress("UNCHECKED_CAST")
                val readings = r.payload["readings"] as? Map<String, Any?> ?: emptyMap()
                for (field in readings.keys) {
                    val s = Signal.forField(field) ?: continue
                    if (s !in granted) gateHeld = false
                }
            }
        }
        return SanityStatus(
            ruleLogIntact = v.ok,
            gateConsentHeld = gateHeld,
            threshold = null,
            verdict = "Not evaluated: no threshold (tau) is set. The framework requires one derived from reference data.",
        )
    }

    // The whole log as one canonical JSON text, with the head hash stated.
    @Synchronized
    fun exportText(): String {
        val recs = log.all()
        val v = Chain.verify(recs)
        return CsJson.canonical(
            linkedMapOf(
                "format" to EXPORT_FORMAT,
                "version" to 1L,
                "exportedAt" to stamp(),
                "sdkVersion" to SDK_VERSION,
                "count" to recs.size.toLong(),
                "headSeq" to v.headSeq,
                "headHash" to v.headHash,
                "chainAtExport" to if (v.ok) "verified" else "broken at record ${v.brokenAt}",
                "records" to recs.map { it.toMap() },
            )
        )
    }

    companion object {
        const val SDK_VERSION = "0.1.0"
        const val EXPORT_FORMAT = "csslabs-sdk.export"

        // Checks an export text on any machine. Pass the head hash you saved
        // somewhere else to catch a whole-log rewrite.
        fun checkExport(text: String, expectedHeadHash: String? = null): ExportResult {
            val parsed = try {
                CsJson.parse(text)
            } catch (e: IllegalArgumentException) {
                return ExportResult(false, "not valid JSON")
            }
            val m = parsed as? Map<*, *> ?: return ExportResult(false, "not an export file")
            if (m["format"] != EXPORT_FORMAT) return ExportResult(false, "not a CSS Labs SDK export")
            val list = m["records"] as? List<*> ?: return ExportResult(false, "export has no records list")
            val recs = ArrayList<ChainRecord>()
            for (item in list) {
                val map = item as? Map<*, *> ?: return ExportResult(false, "a record is malformed")
                try {
                    recs.add(ChainRecord.fromMap(map))
                } catch (e: IllegalArgumentException) {
                    return ExportResult(false, "a record is malformed")
                }
            }
            val v = Chain.verify(recs)
            if (!v.ok) return ExportResult(false, v.reason, v.count, null, v.brokenAt)
            if (m["headHash"] != v.headHash) {
                return ExportResult(false, "the file's stated head hash does not match its records", v.count, v.headHash)
            }
            if (expectedHeadHash != null && expectedHeadHash.trim() != v.headHash) {
                return ExportResult(
                    false,
                    "the chain is internally consistent, but its head hash differs from the one you saved",
                    v.count,
                    v.headHash,
                )
            }
            return ExportResult(true, null, v.count, v.headHash)
        }
    }
}
