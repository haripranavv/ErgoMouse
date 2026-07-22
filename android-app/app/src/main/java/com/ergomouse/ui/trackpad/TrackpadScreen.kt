package com.ergomouse.ui.trackpad

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ergomouse.input.*

@Composable
fun TrackpadScreen(
    navController: NavController,
    viewModel: ErgomouseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        // Top status bar
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("● Wi-Fi Connected", color = Color(0xFF4CAF50))
            Text("🔋100%", color = Color.White)
            Text("⏱ Phase 2 Active", color = Color.White)
        }

        // Main Trackpad Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF121214))
                // 1. Handle Clicks (1-finger tap = Left, Long press = Right)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            viewModel.sendClick(InputIntent.Button.LEFT, true)
                            viewModel.sendClick(InputIntent.Button.LEFT, false)
                        },
                        onLongPress = {
                            viewModel.sendClick(InputIntent.Button.RIGHT, true)
                            viewModel.sendClick(InputIntent.Button.RIGHT, false)
                        }
                    )
                }
                // 2. Handle Movement and Scrolling
                .pointerInput(Unit) {
                    var scrollAccumulator = 0f

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes

                            val moved = pointers.any {
                                it.positionChange() != androidx.compose.ui.geometry.Offset.Zero
                            }

                            if (moved) {
                                if (pointers.size == 1) {
                                    // Move
                                    val delta = pointers[0].positionChange()
                                    viewModel.sendMoveDelta(delta.x, delta.y)
                                    pointers[0].consume()
                                } else if (pointers.size == 2) {
                                    // Scroll
                                    val pan = event.calculatePan()
                                    scrollAccumulator += pan.y

                                    if (scrollAccumulator > 10f) {
                                        Log.d("Ergomouse", "🔥 SENDING SCROLL UP")
                                        viewModel.sendScroll(0f, -1f)
                                        scrollAccumulator = 0f
                                    } else if (scrollAccumulator < -10f) {
                                        Log.d("Ergomouse", "🔥 SENDING SCROLL DOWN")
                                        viewModel.sendScroll(0f, 1f)
                                        scrollAccumulator = 0f
                                    }

                                    pointers.forEach { it.consume() }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Trackpad Active", color = Color.DarkGray)
        }
    }
}