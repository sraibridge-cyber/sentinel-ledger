package io.csslabs.sdk

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

// One entry in the log. Each record's hash covers its sequence number, time,
// type, summary text and a hash of its payload, and it stores the hash of the
// record before it. Changing or deleting any entry breaks the chain at a
// visible point.
data class ChainRecord(
    val seq: Long,
    val ts: String,
    val type: String,
    val summary: String,
    val payload: Map<String, Any?>,
    val payloadHash: String,
    val prevHash: String,
    val hash: String,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "seq" to seq,
        "ts" to ts,
        "type" to type,
        "summary" to summary,
        "payload" to payload,
        "payloadHash" to payloadHash,
        "prevHash" to prevHash,
        "hash" to hash,
    )

    companion object {
        fun fromMap(m: Map<*, *>): ChainRecord {
            fun str(k: String): String = m[k] as? String ?: throw IllegalArgumentException("record field $k is missing")
            val seq = m["seq"] as? Long ?: throw IllegalArgumentException("record field seq is missing")
            @Suppress("UNCHECKED_CAST")
            val payload = (m["payload"] as? Map<String, Any?>) ?: throw IllegalArgumentException("record field payload is missing")
            return ChainRecord(seq, str("ts"), str("type"), str("summary"), payload, str("payloadHash"), str("prevHash"), str("hash"))
        }
    }
}

data class Verification(
    val ok: Boolean,
    val count: Int,
    val headSeq: Long,
    val headHash: String?,
    val brokenAt: Long? = null,
    val reason: String? = null,
)

object Chain {
    const val GENESIS_PREV = "0000000000000000000000000000000000000000000000000000000000000000"

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun hashInput(seq: Long, ts: String, type: String, summary: String, payloadHash: String, prevHash: String): String =
        CsJson.canonical(
            linkedMapOf(
                "seq" to seq,
                "ts" to ts,
                "type" to type,
                "summary" to summary,
                "payloadHash" to payloadHash,
                "prevHash" to prevHash,
            )
        )

    fun build(prev: ChainRecord?, type: String, summary: String, payload: Map<String, Any?>, ts: String): ChainRecord {
        val seq = (prev?.seq ?: 0L) + 1
        val prevHash = prev?.hash ?: GENESIS_PREV
        val payloadHash = sha256Hex(CsJson.canonical(payload))
        val hash = sha256Hex(hashInput(seq, ts, type, summary, payloadHash, prevHash))
        return ChainRecord(seq, ts, type, summary, payload, payloadHash, prevHash, hash)
    }

    // Walks the records in order. The first failure is reported with the
    // record it broke at.
    fun verify(records: List<ChainRecord>): Verification {
        var prevHash = GENESIS_PREV
        var prevSeq = 0L
        val count = records.size
        for ((index, r) in records.withIndex()) {
            fun fail(reason: String): Verification =
                Verification(false, count, prevSeq, if (prevSeq > 0) prevHash else null, r.seq, reason)
            if (index == 0 && r.type != "GENESIS") return fail("first record is not GENESIS")
            if (r.seq != prevSeq + 1) return fail("sequence gap, reorder, or deletion")
            if (r.prevHash != prevHash) return fail("link to the previous record is broken")
            val payloadHash = sha256Hex(CsJson.canonical(r.payload))
            if (payloadHash != r.payloadHash) return fail("payload was changed after it was written")
            val hash = sha256Hex(hashInput(r.seq, r.ts, r.type, r.summary, r.payloadHash, r.prevHash))
            if (hash != r.hash) return fail("record fields were changed after it was written")
            prevHash = r.hash
            prevSeq = r.seq
        }
        return Verification(true, count, prevSeq, if (count > 0) prevHash else null)
    }
}

// Storage for the log. The only write is appendIfHead(): a record is written
// only if the chain head is still the one it was built on. There is no update
// and no delete.
interface LogStore {
    fun head(): ChainRecord?
    fun all(): List<ChainRecord>
    fun appendIfHead(expectedSeq: Long, expectedHash: String?, record: ChainRecord): Boolean
}

class MemoryLogStore : LogStore {
    private val records = ArrayList<ChainRecord>()

    @Synchronized
    override fun head(): ChainRecord? = records.lastOrNull()

    @Synchronized
    override fun all(): List<ChainRecord> = records.toList()

    @Synchronized
    override fun appendIfHead(expectedSeq: Long, expectedHash: String?, record: ChainRecord): Boolean {
        val h = records.lastOrNull()
        val hs = h?.seq ?: 0L
        val hh = h?.hash
        if (hs != expectedSeq || hh != expectedHash) return false
        if (record.seq != hs + 1 || record.prevHash != (hh ?: Chain.GENESIS_PREV)) return false
        records.add(record)
        return true
    }
}

// One JSON object per line, appended to a single file. The file is opened in
// append mode and never rewritten by this class.
class FileLogStore(private val file: File) : LogStore {

    @Synchronized
    override fun all(): List<ChainRecord> {
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { ChainRecord.fromMap(CsJson.parse(it) as Map<*, *>) }
    }

    @Synchronized
    override fun head(): ChainRecord? = all().lastOrNull()

    @Synchronized
    override fun appendIfHead(expectedSeq: Long, expectedHash: String?, record: ChainRecord): Boolean {
        val h = head()
        val hs = h?.seq ?: 0L
        val hh = h?.hash
        if (hs != expectedSeq || hh != expectedHash) return false
        if (record.seq != hs + 1 || record.prevHash != (hh ?: Chain.GENESIS_PREV)) return false
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { out ->
            out.write((CsJson.canonical(record.toMap()) + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        return true
    }
}
