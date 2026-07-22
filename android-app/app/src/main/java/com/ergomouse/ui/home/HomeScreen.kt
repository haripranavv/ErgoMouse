package com.ergomouse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@Composable
fun HomeScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    
    // ⚠️ REPLACE THIS WITH YOUR PC'S IPV4 ADDRESS from ipconfig
    val pcIpAddress = "10.174.93.214" 

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Ergomouse: Ready to Pair", 
                color = Color(0xFF4CAF50)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(onClick = {
                // Network calls must happen on a background thread in Android
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val socket = DatagramSocket()
                        val address = InetAddress.getByName(pcIpAddress)
                        
                        // Creating a raw Heartbeat packet based on your packet.rs logic
                        // [Version (1), Type (0x02 for Heartbeat), Seq_Lo (0), Seq_Hi (0), Len_Lo (0), Len_Hi (0)]
                        val data = byteArrayOf(1, 2, 0, 0, 0, 0)
                        
                        val packet = DatagramPacket(data, data.size, address, 51234)
                        socket.send(packet)
                        socket.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }) {
                Text("Send Test Ping")
            }
        }
    }
}