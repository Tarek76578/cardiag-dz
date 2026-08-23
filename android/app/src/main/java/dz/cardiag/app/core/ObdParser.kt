package dz.cardiag.app.core

object ObdParser {
    fun normalize(raw: String): String = raw.replace("\r", "\n").replace(">", "").lines().map { it.trim() }.filter { it.isNotEmpty() }.filterNot { it.equals("SEARCHING...", true) || it.equals("NO DATA", true) }.joinToString(" ").uppercase()
    private fun bytes(raw: String): List<Int> = normalize(raw).split(Regex("\\s+")).flatMap { token -> if (token.matches(Regex("[0-9A-F]{2,}")) && token.length % 2 == 0) token.chunked(2).mapNotNull { it.toIntOrNull(16) } else emptyList() }
    fun parseRpm(raw: String): Double? { val b=bytes(raw); val i=b.windowed(2).indexOfFirst{it[0]==0x41&&it[1]==0x0C}; return if(i>=0&&i+3<b.size)(b[i+2]*256+b[i+3])/4.0 else null }
    fun parseCoolantCelsius(raw: String): Double? { val b=bytes(raw); val i=b.windowed(2).indexOfFirst{it[0]==0x41&&it[1]==0x05}; return if(i>=0&&i+2<b.size)b[i+2]-40.0 else null }
    fun parseSpeed(raw: String): Double? { val b=bytes(raw); val i=b.windowed(2).indexOfFirst{it[0]==0x41&&it[1]==0x0D}; return if(i>=0&&i+2<b.size)b[i+2].toDouble() else null }
    fun parseDtc(raw: String): List<String> { val b=bytes(raw); val i=b.windowed(2).indexOfFirst{it[0]==0x43}; if(i<0)return emptyList(); return b.drop(i+1).chunked(2).takeWhile{it.size==2&&!(it[0]==0&&it[1]==0)}.map{p->val c=(p[0] shl 8)or p[1]; val q=when((c shr 14)and 3){0->'P';1->'C';2->'B';else->'U'}; "$q${(c shr 12)and 3}${c.toString(16).uppercase().padStart(3,'0').takeLast(3)}"}.distinct() }
}
