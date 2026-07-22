package com.ergomouse.ui.trackpad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergomouse.input.InputIntent
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConnectionState {
    CONNECTED,
    DEGRADED,
    DISCONNECTED
}

enum class TransportMode {
    WIFI,
    BLUETOOTH
} // NEW: The Dual-Mode Switch

class ErgomouseViewModel : ViewModel() {

    // --- State ---
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _transportMode = MutableStateFlow(TransportMode.WIFI)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    // --- Wi-Fi Setup ---
    private val _ipAddress = MutableStateFlow("192.168.1.X") // Just a placeholder
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    fun setIpAddress(ip: String) {
        _ipAddress.value = ip
    }
    // --- Bluetooth Setup ---
    // This is the standard UUID for serial data over Bluetooth (SPP)
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null

    private var sequenceNumber = 0

    // Call this from your UI to switch between Wi-Fi and Bluetooth!
    fun setTransportMode(mode: TransportMode) {
        _transportMode.value = mode
    }
    private fun sendData(data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_transportMode.value == TransportMode.WIFI) {
                    // Route 1: Blast it over Wi-Fi UDP
                    val socket = DatagramSocket()
                    val address = InetAddress.getByName(_ipAddress.value)
                    val packet = DatagramPacket(data, data.size, address, 51234)
                    socket.send(packet)
                    socket.close()
                } else {
                    // Route 2: Stream it over Bluetooth
                    bluetoothSocket?.let { socket ->
                        if (socket.isConnected) {
                            socket.outputStream.write(data)
                            socket.outputStream.flush()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun sendMoveDelta(dx: Float, dy: Float) {
        // 1. Convert the raw float pixels into Q8.8 fixed-point integers
        val dxQ8 = (dx * 256).toInt()
        val dyQ8 = (dy * 256).toInt()

        // 2. Create the exact 13-byte array your Rust server expects
        val data = ByteArray(13)

        // --- HEADER (6 bytes) ---
        data[0] = 1 // Protocol Version
        data[1] = 0x10 // Packet Type: MOVE
        data[2] = (sequenceNumber and 0xFF).toByte() // Seq Lo
        data[3] = ((sequenceNumber shr 8) and 0xFF).toByte() // Seq Hi
        data[4] = 7 // Payload Len Lo (7 bytes)
        data[5] = 0 // Payload Len Hi

        // --- PAYLOAD (7 bytes) ---
        data[6] = (dxQ8 and 0xFF).toByte() // DX Lo
        data[7] = ((dxQ8 shr 8) and 0xFF).toByte() // DX Hi

        data[8] = (dyQ8 and 0xFF).toByte() // DY Lo
        data[9] = ((dyQ8 shr 8) and 0xFF).toByte() // DY Hi

        data[10] = 0 // Timestamp Lo
        data[11] = 0 // Timestamp Hi
        data[12] = 0 // Flags

        // 3. Hand the raw bytes to your universal network router
        sendData(data)

        // 4. Tick the sequence number up for the next time you move your finger
        sequenceNumber++
    }
    @SuppressLint("MissingPermission")
    fun connectToBluetoothDevice(context: Context, deviceAddress: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get the Bluetooth hardware adapter
                val bluetoothManager =
                        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter ?: return@launch

                // Find your specific PC by its MAC address
                val device = adapter.getRemoteDevice(deviceAddress)

                // Attempt to open a serial connection to the PC
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()

                // If we get here, connection was successful! Switch the mode!
                setTransportMode(TransportMode.BLUETOOTH)
                _connectionState.value = ConnectionState.CONNECTED
            } catch (e: Exception) {
                e.printStackTrace()
                _connectionState.value = ConnectionState.DISCONNECTED
                // Close the broken socket to prevent memory leaks
                try {
                    bluetoothSocket?.close()
                } catch (closeException: Exception) {}
            }
        }
    }
    fun sendClick(button: InputIntent.Button, down: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(_ipAddress.value)

                // Construct an 8-byte CLICK packet
                val data = ByteArray(8)

                // --- HEADER ---
                data[0] = 1 // Version
                data[1] = 0x11 // Type: CLICK
                data[2] = (sequenceNumber and 0xFF).toByte()
                data[3] = ((sequenceNumber shr 8) and 0xFF).toByte()
                data[4] = 2 // Payload Len Lo (2 bytes)
                data[5] = 0 // Payload Len Hi

                // --- PAYLOAD ---
                data[6] =
                        when (button) {
                            InputIntent.Button.LEFT -> 0
                            InputIntent.Button.RIGHT -> 1
                            InputIntent.Button.MIDDLE -> 2
                        }
                data[7] = if (down) 1 else 0

                val packet = DatagramPacket(data, data.size, address, 51234)
                socket.send(packet)
                socket.close()

                sequenceNumber++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun sendScroll(dx: Float, dy: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(_ipAddress.value)

                // FIX: Increased to 10 to fit indices 0 through 9
                val data = ByteArray(10)

                // --- HEADER ---
                data[0] = 1 // Version
                data[1] = 0x12 // Type: SCROLL (18)
                data[2] = (sequenceNumber and 0xFF).toByte()
                data[3] = ((sequenceNumber shr 8) and 0xFF).toByte()
                data[4] = 4 // Payload Len Lo (4 bytes)
                data[5] = 0 // Payload Len Hi

                // --- PAYLOAD ---
                val dxInt = dx.toInt()
                val dyInt = dy.toInt()

                data[6] = (dxInt and 0xFF).toByte()
                data[7] = ((dxInt shr 8) and 0xFF).toByte()
                // We invert dy because Android scroll coordinates are reversed from Windows
                val invDy = -dyInt
                data[8] = (invDy and 0xFF).toByte()
                data[9] = ((invDy shr 8) and 0xFF).toByte()

                val packet = DatagramPacket(data, data.size, address, 51234)
                socket.send(packet)
                socket.close()

                sequenceNumber++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
