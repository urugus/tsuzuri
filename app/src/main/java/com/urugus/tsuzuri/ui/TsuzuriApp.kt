package com.urugus.tsuzuri.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.urugus.tsuzuri.feature.chat.ChatScreen
import com.urugus.tsuzuri.feature.diary.DayDetailScreen
import com.urugus.tsuzuri.feature.diary.DayDetailViewModel
import com.urugus.tsuzuri.feature.settings.SettingsScreen
import com.urugus.tsuzuri.feature.timeline.TimelineScreen

private enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Chat("chat", "会話", Icons.AutoMirrored.Filled.Chat),
    Timeline("timeline", "ふり返り", Icons.Filled.Timeline),
    Settings("settings", "設定", Icons.Filled.Settings),
}

@Composable
fun TsuzuriApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // トップレベル(タブ)以外の画面（日詳細など）では下部ナビを隠す。
    val isTopLevel = TopDestination.entries.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Chat.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(TopDestination.Chat.route) {
                ChatScreen()
            }
            composable(TopDestination.Timeline.route) {
                TimelineScreen(
                    onOpenDay = { date -> navController.navigate("day/$date") },
                )
            }
            composable(TopDestination.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = "day/{${DayDetailViewModel.ARG_DATE}}",
                arguments = listOf(navArgument(DayDetailViewModel.ARG_DATE) { type = NavType.StringType }),
            ) {
                DayDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
