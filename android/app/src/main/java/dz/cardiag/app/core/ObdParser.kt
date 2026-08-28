package dz.cardiag.app.core

/** Pure OBD-II/ELM327 response parser. Kept side-effect free so it can be exhaustively unit tested. */
object ObdParser {
    private val HEX_TOKEN = Regex("[0-9A-F]{2,}")

    fun normalize(raw: String): String = raw
        .replace('\r', '\n')
        .replace(">", "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.equals("SEARCHING...", true) || it.equals("NO DATA", true) || it.equals("OK", true) }
        .joinToString(" ")
        .uppercase()

    private fun bytes(raw: String): List<Int> = normalize(raw).split(Regex("\\s+"))
        .flatMap { token ->
            if (HEX_TOKEN.matches(token) && token.length % 2 == 0) {
                token.chunked(2).mapNotNull { it.toIntOrNull(16) }
            } else emptyList()
        }

    private fun response(bytes: List<Int>, mode: Int, pid: Int): Int =
        bytes.windowed(2).indexOfFirst { it[0] == mode + 0x40 && it[1] == pid }

    fun parseRpm(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x0C)
        return if (i >= 0 && i + 3 < b.size) (b[i + 2] * 256 + b[i + 3]) / 4.0 else null
    }

    fun parseCoolantCelsius(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x05)
        return if (i >= 0 && i + 2 < b.size) b[i + 2] - 40.0 else null
    }

    fun parseSpeed(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x0D)
        return if (i >= 0 && i + 2 < b.size) b[i + 2].toDouble() else null
    }

    fun parseIntakeTemperature(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x0F)
        return if (i >= 0 && i + 2 < b.size) b[i + 2] - 40.0 else null
    }

    fun parsePercent(raw: String): Double? {
        val b = bytes(raw)
        val i = b.windowed(2).indexOfFirst { it[0] == 0x41 && it[1] in setOf(0x04, 0x11, 0x2F) }
        return if (i >= 0 && i + 2 < b.size) b[i + 2] * 100.0 / 255.0 else null
    }

    fun parseMaf(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x10)
        return if (i >= 0 && i + 3 < b.size) (b[i + 2] * 256 + b[i + 3]) / 100.0 else null
    }

    fun parseMap(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x0B)
        return if (i >= 0 && i + 2 < b.size) b[i + 2].toDouble() else null
    }

    fun parseTimingAdvance(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x0E)
        return if (i >= 0 && i + 2 < b.size) b[i + 2] / 2.0 - 64.0 else null
    }

    fun parseVoltage(raw: String): Double? {
        val b = bytes(raw); val i = response(b, 1, 0x42)
        return if (i >= 0 && i + 3 < b.size) (b[i + 2] * 256 + b[i + 3]) / 1000.0 else null
    }

    fun parseDtc(raw: String, mode: Int? = null): List<String> {
        val b = bytes(raw)
        val modes = mode?.let { listOf(it) } ?: listOf(0x43, 0x47, 0x4A)
        val startMode = modes.firstOrNull { it in b } ?: return emptyList()
        val i = b.indexOf(startMode)
        return b.drop(i + 1).chunked(2)
            .takeWhile { it.size == 2 && !(it[0] == 0 && it[1] == 0) }
            .map { pair ->
                val value = (pair[0] shl 8) or pair[1]
                val letter = when ((value shr 14) and 3) { 0 -> 'P'; 1 -> 'C'; 2 -> 'B'; else -> 'U' }
                "$letter${(value shr 12) and 3}${value.toString(16).uppercase().padStart(4, '0').takeLast(3)}"
            }
            .distinct()
    }

    /** Decode a Mode 01 Supported-PIDs bitmap. base is 0x00, 0x20 or 0x40. */
    fun parseSupportedPids(raw: String, base: Int): Set<Int> {
        require(base in setOf(0x00, 0x20, 0x40)) { "Supported PID base must be 0x00, 0x20 or 0x40" }
        val b = bytes(raw)
        val i = response(b, 1, base)
        if (i < 0 || i + 5 >= b.size) return emptySet()
        val mask = (b[i + 2] shl 24) or (b[i + 3] shl 16) or (b[i + 4] shl 8) or b[i + 5]
        return (1..32).filter { mask and (1 shl (32 - it)) != 0 }.map { base + it }.toSet()
    }

    fun parseMilStatus(raw: String): Boolean? {
        val b = bytes(raw); val i = response(b, 1, 0x01)
        return if (i >= 0 && i + 2 < b.size) (b[i + 2] and 0x80) != 0 else null
    }

    fun parseReadiness(raw: String): ReadinessStatus {
        val b = bytes(raw); val i = response(b, 1, 0x01)
        if (i < 0 || i + 6 >= b.size) return ReadinessStatus(null, null, normalize(raw))
        val mil = (b[i + 2] and 0x80) != 0
        val statusByteB = b[i + 3]
        val statusByteC = b[i + 4]
        return ReadinessStatus(mil, statusByteB == 0 && statusByteC == 0, normalize(raw))
    }
}
