package dz.cardiag.app.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Bluetooth Classic ELM327 transport with parsed standard OBD-II values. */
class ObdService {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.sortedBy { it.name ?: it.address }?.toList().orEmpty()

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): String = withContext(Dispatchers.IO) {
        disconnect()
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket = s
        try {
            s.connect()
            command("ATZ", 2500); command("ATE0"); command("ATL0"); command("ATS0"); command("ATH0"); command("ATSP0")
            "Connected to ${device.name ?: device.address}"
        } catch (e: Exception) {
            disconnect()
            throw IOException("Unable to connect to ELM327 adapter", e)
        }
    }

    suspend fun readTroubleCodes(): List<String> = ObdParser.parseDtc(command("03", 4000))
    suspend fun readRpm(): Double? = ObdParser.parseRpm(command("010C"))
    suspend fun readCoolantTemperature(): Double? = ObdParser.parseCoolantCelsius(command("0105"))
    suspend fun readVehicleSpeedKmh(): Double? = ObdParser.parseSpeed(command("010D"))

    suspend fun command(command: String, timeoutMs: Long = 1800): String = withContext(Dispatchers.IO) {
        val normalized = command.trim().uppercase()
        require(normalized.matches(Regex("[0-9A-Z]+"))) { "Invalid ELM327 command" }
        val s = socket ?: error("OBD adapter is not connected")
        s.outputStream.write((normalized + "\r").toByteArray(Charsets.US_ASCII))
        s.outputStream.flush()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = StringBuilder()
        val bytes = ByteArray(512)
        while (System.currentTimeMillis() < deadline) {
            if (s.inputStream.available() > 0) {
                val n = s.inputStream.read(bytes)
                if (n > 0) {
                    buffer.append(String(bytes, 0, n, Charsets.US_ASCII))
                    if (buffer.contains(">")) break
                }
            } else Thread.sleep(25)
        }
        ObdParser.normalize(buffer.toString())
    }

    fun disconnect() { try { socket?.close() } catch (_: IOException) { }; socket = null }
}
