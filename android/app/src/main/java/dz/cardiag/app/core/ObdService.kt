package dz.cardiag.app.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Production-oriented Bluetooth Classic ELM327 transport. Hardware support remains adapter/vehicle dependent. */
class ObdService {
    companion object { private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }
    @Suppress("DEPRECATION") private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var lastDevice: BluetoothDevice? = null
    @Volatile private var detectedProtocol: String = "unknown"
    @Volatile private var connected = false

    fun isAvailable(): Boolean = adapter != null
    fun isConnected(): Boolean = connected && socket?.isConnected == true
    fun protocol(): String = detectedProtocol
    @SuppressLint("MissingPermission") fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.sortedBy { it.name ?: it.address }?.toList().orEmpty()

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): String = withContext(Dispatchers.IO) {
        disconnect(); require(adapter?.isEnabled == true) { "Bluetooth is disabled" }
        lastDevice = device
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID); socket = s
        try {
            s.connect()
            command("ATZ", 3000); command("ATE0"); command("ATL0"); command("ATS0"); command("ATH0"); command("ATAT1"); command("ATSP0", 2500)
            detectedProtocol = parseProtocol(command("ATDPN", 2500))
            connected = true
            "Connected • ${device.name ?: device.address} • $detectedProtocol"
        } catch (e: Exception) { disconnect(); throw IOException("Unable to connect to ELM327 adapter", e) }
    }

    suspend fun reconnect(): String { val d = lastDevice ?: error("No previous OBD adapter"); repeat(2) { try { return connect(d) } catch (_: Exception) { delay(400) } }; error("Unable to reconnect to ELM327 adapter") }
    suspend fun readTroubleCodes(): List<String> = ObdParser.parseDtc(command("03", 4000))
    suspend fun readPendingTroubleCodes(): List<String> = ObdParser.parseDtc(command("07", 4000))
    suspend fun readPermanentTroubleCodes(): List<String> = ObdParser.parseDtc(command("0A", 4000))
    suspend fun clearTroubleCodes(): String = command("04", 5000)
    suspend fun readRpm(): Double? = ObdParser.parseRpm(command("010C"))
    suspend fun readCoolantTemperature(): Double? = ObdParser.parseCoolantCelsius(command("0105"))
    suspend fun readVehicleSpeedKmh(): Double? = ObdParser.parseSpeed(command("010D"))
    suspend fun readIntakeTemperature(): Double? = ObdParser.parseIntakeTemperature(command("010F"))
    suspend fun readThrottlePosition(): Double? = ObdParser.parsePercent(command("0111"))
    suspend fun readMaf(): Double? = ObdParser.parseMaf(command("0110"))
    suspend fun readMap(): Double? = ObdParser.parseMap(command("010B"))
    suspend fun readFuelLevel(): Double? = ObdParser.parsePercent(command("012F"))
    suspend fun readEngineLoad(): Double? = ObdParser.parsePercent(command("0104"))
    suspend fun readTimingAdvance(): Double? = ObdParser.parseTimingAdvance(command("010E"))
    suspend fun readBatteryVoltage(): Double? = ObdParser.parseVoltage(command("0142"))
    suspend fun readSupportedPids01_20(): String = command("0100", 3000)
    suspend fun readSupportedPids21_40(): String = command("0120", 3000)
    suspend fun readSupportedPids41_60(): String = command("0140", 3000)

    suspend fun readSupportedPids(): Set<Int> {
        val supported = mutableSetOf<Int>()
        supported += ObdParser.parseSupportedPids(readSupportedPids01_20(), 0x00)
        runCatching { supported += ObdParser.parseSupportedPids(readSupportedPids21_40(), 0x20) }
        runCatching { supported += ObdParser.parseSupportedPids(readSupportedPids41_60(), 0x40) }
        return supported
    }

    suspend fun readVehicleInfoVin(): String = command("0902", 5000)
    suspend fun readEcuName(): String = command("090A", 5000)
    suspend fun readEcuInfo(): String = readEcuName()
    suspend fun readFreezeFrameRaw(): String = command("02", 5000)
    suspend fun readFreezeFrameCodes(): List<String> = ObdParser.parseDtc(readFreezeFrameRaw(), 0x42)
    suspend fun readReadinessRaw(): String = command("0101", 3000)
    suspend fun readReadiness(): ReadinessStatus = ObdParser.parseReadiness(readReadinessRaw())
    suspend fun readMilStatus(): Boolean? = ObdParser.parseMilStatus(readReadinessRaw())
    suspend fun adapterInfo(): String = command("ATI", 2500)
    suspend fun adapterProtocol(): String = command("ATDPN", 2500).let(::parseProtocol)
    suspend fun readMode01Pid(pid: String): String = command("01${pid.trim().uppercase().padStart(2, '0')}", 2500)
    suspend fun scanSupportedPids(): Map<Int, Boolean> = readSupportedPids().let { supported -> (1..0x60).associateWith { it in supported } }

    suspend fun command(command: String, timeoutMs: Long = 1800): String = withContext(Dispatchers.IO) {
        val normalized = command.trim().uppercase()
        require(normalized.matches(Regex("[0-9A-Z]+"))) { "Invalid ELM327 command" }
        val s = socket ?: error("OBD adapter is not connected")
        try {
            s.outputStream.write((normalized + "\r").toByteArray(Charsets.US_ASCII)); s.outputStream.flush()
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = StringBuilder(); val bytes = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                if (s.inputStream.available() > 0) {
                    val n = s.inputStream.read(bytes)
                    if (n > 0) { buffer.append(String(bytes, 0, n, Charsets.US_ASCII)); if (buffer.contains(">")) break }
                } else Thread.sleep(20)
            }
            ObdParser.normalize(buffer.toString()).ifBlank { error("ELM327 timeout: $normalized") }
        } catch (e: Exception) {
            connected = false; try { s.close() } catch (_: Exception) {}; socket = null
            throw IOException("ELM327 command failed: $normalized", e)
        }
    }

    fun disconnect() { connected = false; try { socket?.close() } catch (_: IOException) {}; socket = null }
    private fun parseProtocol(raw: String): String = when (raw.trim().uppercase().removePrefix("A")) {
        "0" -> "AUTO"; "1" -> "SAE J1850 PWM"; "2" -> "SAE J1850 VPW"; "3" -> "ISO 9141-2"
        "4" -> "ISO 14230-4 KWP (5-baud)"; "5" -> "ISO 14230-4 KWP (fast)"; "6" -> "ISO 15765-4 CAN 11/500"
        "7" -> "ISO 15765-4 CAN 29/500"; "8" -> "ISO 15765-4 CAN 11/250"; "9" -> "ISO 15765-4 CAN 29/250"
        else -> raw.ifBlank { "unknown" }
    }
}
