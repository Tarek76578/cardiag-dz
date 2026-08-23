package dz.cardiag.app.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Basic ELM327 Bluetooth Classic transport for real OBD-II adapters. */
class ObdService {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices?.sortedBy { it.name ?: it.address }?.toList().orEmpty()

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): String = withContext(Dispatchers.IO) {
        disconnect()
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket = s
        s.connect()
        command("ATZ", 2500)
        command("ATE0")
        command("ATL0")
        command("ATS0")
        command("ATSP0")
        "Connected to ${device.name ?: device.address}"
    }

    suspend fun readTroubleCodes(): String = command("03", 4000)
    suspend fun readRpm(): String = command("010C")
    suspend fun readCoolantTemperature(): String = command("0105")

    suspend fun command(command: String, timeoutMs: Long = 1800): String = withContext(Dispatchers.IO) {
        val s = socket ?: error("OBD adapter is not connected")
        val output = s.outputStream
        val input = s.inputStream
        output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = StringBuilder()
        val bytes = ByteArray(256)
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val n = input.read(bytes)
                if (n > 0) {
                    buffer.append(String(bytes, 0, n, Charsets.US_ASCII))
                    if (buffer.contains(">")) break
                }
            } else Thread.sleep(30)
        }
        buffer.toString().replace("\r", " ").replace("\n", " ").trim(' ', '>')
    }

    fun disconnect() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
    }
}
