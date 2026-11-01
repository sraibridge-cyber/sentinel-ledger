package io.csslabs.sdk

// Canonical JSON for hashing, plus a small reader for log and export files.
// Object keys are sorted, null is kept, no whitespace is written, and numbers
// render the same way on write and on read: integers as digits, doubles as
// Kotlin's shortest round-trip form (for example 0.9946 or 1.0E-5). A change to
// any value therefore changes the hash.
object CsJson {

    fun canonical(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int -> sb.append(v.toLong().toString())
            is Long -> sb.append(v.toString())
            is Double -> sb.append(if (v.isFinite()) v.toString() else "null")
            is String -> writeString(sb, v)
            is Map<*, *> -> {
                sb.append('{')
                val entries = v.entries.map { it.key.toString() to it.value }.sortedBy { it.first }
                var first = true
                for ((k, x) in entries) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k)
                    sb.append(':')
                    write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, x)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // Parses a JSON text. Throws IllegalArgumentException on malformed input.
    fun parse(text: String): Any? {
        val p = Reader(text)
        val v = p.value()
        p.skipWs()
        require(p.atEnd()) { "trailing characters after JSON value" }
        return v
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char {
            require(i < s.length) { "unexpected end of JSON" }
            return s[i]
        }

        fun value(): Any? {
            skipWs()
            return when (peek()) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                i++
                return out
            }
            while (true) {
                skipWs()
                val key = str()
                skipWs()
                require(peek() == ':') { "expected ':' at $i" }
                i++
                out[key] = value()
                skipWs()
                val c = peek()
                i++
                if (c == '}') return out
                require(c == ',') { "expected ',' or '}' at ${i - 1}" }
            }
        }

        private fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') {
                i++
                return out
            }
            while (true) {
                out.add(value())
                skipWs()
                val c = peek()
                i++
                if (c == ']') return out
                require(c == ',') { "expected ',' or ']' at ${i - 1}" }
            }
        }

        private fun str(): String {
            require(peek() == '"') { "expected string at $i" }
            i++
            val sb = StringBuilder()
            while (true) {
                val c = peek()
                i++
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = peek()
                        i++
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(i + 4 <= s.length) { "short unicode escape" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape at ${i - 1}")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            val token = s.substring(start, i)
            require(token.isNotEmpty()) { "bad value at $start" }
            return if (token.any { it == '.' || it == 'e' || it == 'E' }) token.toDouble() else token.toLong()
        }
    }
}
