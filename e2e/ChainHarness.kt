import io.csslabs.sdk.Chain
import io.csslabs.sdk.FileLogStore
import io.sentinel.core.Calibration
import io.sentinel.core.CalibrationResult
import io.sentinel.core.Reading
import java.io.File
import java.time.Instant

// Writes logs the way the app does, through the SDK's own FileLogStore, then checks what comes back.
// scripts/e2e.sh runs this. A failed check throws, so the script stops.

private const val T0 = 1_800_000_000_000L
private const val MINUTE = 60_000L

private fun micro(v: Double): Long = (v * 1_000_000).toLong()

private fun writeLog(file: File, withGenesis: Boolean) {
    file.delete()
    val store = FileLogStore(file)
    var n = 0
    fun append(type: String, summary: String, payload: Map<String, Any?>) {
        val head = store.head()
        val rec = Chain.build(head, type, summary, payload, Instant.ofEpochMilli(T0 + n * MINUTE).toString())
        n++
        check(store.appendIfHead(head?.seq ?: 0L, head?.hash, rec)) { "append rejected at record $n" }
    }
    if (withGenesis) append("GENESIS", "CSS Labs SDK chain started", linkedMapOf("sdk" to "e2e"))
    append("CALIBRATION_START", "owner confirmed clean", linkedMapOf("ownerConfirmed" to true))
    val samples = List(30) { i -> Reading(0.3 + (i % 5) * 0.1, 1.0 + (i % 3)) }
    val result = Calibration.build(samples)
    check(result is CalibrationResult.Ready) { "calibration did not set a baseline from 30 samples" }
    val b = (result as CalibrationResult.Ready).baseline
    append("CALIBRATION", "baseline set from ${b.samples} readings", linkedMapOf(
        "samples" to b.samples.toLong(), "tauMicro" to micro(b.tauMu), "muMinMicro" to micro(b.muMin),
        "muMedianMicro" to micro(b.muMedian), "mappingVersion" to b.mappingVersion, "note" to b.note))
    listOf(Reading(0.4, 1.0), Reading(100.0, 1.0), Reading(0.5, 2.0)).forEach { r ->
        val (mu, verdict) = Calibration.check(b, r)
        append("BASELINE", verdict.name.lowercase().replace('_', ' '), linkedMapOf(
            "verdict" to verdict.name, "muMicro" to micro(mu), "tauMicro" to micro(b.tauMu)))
    }
}

fun main(args: Array<String>) {
    val dir = File(args[0])
    val good = File(dir, "ledger.jsonl")
    writeLog(good, withGenesis = true)
    val records = FileLogStore(good).all()
    check(Chain.verify(records).ok) { "SDK verify rejected a log the SDK wrote" }
    check(records.first().type == "GENESIS") { "first record is not GENESIS" }
    val cal = records.single { it.type == "CALIBRATION" }
    val tau = (cal.payload["tauMicro"] as Number).toLong()
    val min = (cal.payload["muMinMicro"] as Number).toLong()
    check(tau < min) { "threshold is not below the lowest calibration reading" }
    println("calibration: samples=${cal.payload["samples"]} tauMicro=$tau muMinMicro=$min")
    println("baseline checks: " + records.filter { it.type == "BASELINE" }.map { it.payload["verdict"] })
    check(Calibration.build(List(29) { Reading(0.5, 1.0) }) is CalibrationResult.NotEnough) { "a threshold was set from 29 readings" }
    val noGenesis = File(dir, "no-genesis.jsonl")
    writeLog(noGenesis, withGenesis = false)
    check(!Chain.verify(FileLogStore(noGenesis).all()).ok) { "SDK verify accepted a log with no GENESIS" }
    println("harness checks passed")
}
