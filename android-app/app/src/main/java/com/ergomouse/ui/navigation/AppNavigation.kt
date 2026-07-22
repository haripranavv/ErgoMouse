package com.ergomouse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ergomouse.ui.connection.ConnectionScreen
import com.ergomouse.ui.trackpad.ErgomouseViewModel
import com.ergomouse.ui.trackpad.TrackpadScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Create one shared ViewModel instance for both screens
    val viewModel: ErgomouseViewModel = viewModel()

    NavHost(navController = navController, startDestination = "connection") {
        composable("connection") {
            ConnectionScreen(navController = navController, viewModel = viewModel)
        }
        composable("trackpad") {
            TrackpadScreen(navController = navController, viewModel = viewModel)
        }
    }
}
