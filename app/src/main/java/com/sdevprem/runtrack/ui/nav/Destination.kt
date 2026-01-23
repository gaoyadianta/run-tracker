package com.sdevprem.runtrack.ui.nav

import androidx.navigation.NavController
import androidx.navigation.navDeepLink

sealed class Destination(val route: String) {

    object OnBoardingDestination : Destination("on_boarding") {
        fun navigateToHome(navController: NavController) {
            navController.navigate(BottomNavDestination.Home.route) {
                popUpTo(route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    object CurrentRun : Destination("current_run") {
        val currentRunUriPattern = "https://runtrack.sdevprem.com/$route"
        val deepLinks = listOf(
            navDeepLink {
                uriPattern = currentRunUriPattern
            }
        )
    }

    data object RunStats : Destination("run_stats")

    data object Settings : Destination("settings")

    data object RunDetail : Destination("run_detail/{runId}") {
        const val ARG_RUN_ID = "runId"
        fun createRoute(runId: Int) = "run_detail/$runId"
    }

    //global navigation
    companion object {
        fun navigateToCurrentRunScreen(navController: NavController) {
            navController.navigate(CurrentRun.route)
        }

        fun navigateToSettingsScreen(navController: NavController) {
            navController.navigate(Settings.route)
        }

        fun navigateToRunDetail(navController: NavController, runId: Int) {
            navController.navigate(RunDetail.createRoute(runId))
        }
    }

}
