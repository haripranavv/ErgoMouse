package com.ergomouse.ui.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ergomouse.ui.trackpad.ErgomouseViewModel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

@SuppressLint("MissingPermission")
@Composable
fun ConnectionScreen(navController: NavController, viewModel: ErgomouseViewModel) {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter

    val pairedDevices = remember { adapter?.bondedDevices?.toList() ?: emptyList() }

    // NEW: State to hold the text you type into the box
    val currentIp by viewModel.ipAddress.collectAsState()
    var ipInput by remember { mutableStateOf(currentIp) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).padding(16.dp)) {
        Text("Ergomouse", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(
                "Select your PC to connect",
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 24.dp)
        )

        // NEW: The IP Address Input Box
        OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("PC IP Address", color = Color.Gray) },
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Cyan,
                                unfocusedBorderColor = Color.DarkGray
                        ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        // Wi-Fi Quick Start Button
        Button(
                onClick = {
                    viewModel.setIpAddress(ipInput.trim()) // Save the typed IP
                    viewModel.setTransportMode(com.ergomouse.ui.trackpad.TransportMode.WIFI)
                    navController.navigate("trackpad")
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33333D))
        ) { Text("Continue with Wi-Fi (UDP)") }

        Text(
                "Paired Bluetooth Devices",
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
        )
        Divider(color = Color.DarkGray)

        if (pairedDevices.isEmpty()) {
            Text(
                    "No paired devices found. Pair your PC in Android Settings first!",
                    color = Color.Red,
                    modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn {
                items(pairedDevices) { device ->
                    Card(
                            modifier =
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                        viewModel.connectToBluetoothDevice(context, device.address)
                                        navController.navigate("trackpad")
                                    },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(device.name ?: "Unknown Device", color = Color.White)
                            Text(
                                    device.address,
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
