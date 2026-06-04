package com.jiny.catchtherule.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jiny.catchtherule.ui.challenge.ChallengeScreen
import com.jiny.catchtherule.ui.challenge.TimeAttackScreen
import com.jiny.catchtherule.ui.home.HomeScreen
import com.jiny.catchtherule.ui.play.CampaignSessionScreen
import com.jiny.catchtherule.ui.settings.SettingsScreen
import com.jiny.catchtherule.ui.theme.AppColors

private sealed interface FullScreen {
    data object Campaign : FullScreen
    data object TimeAttack : FullScreen
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun RootScreen() {
    var fullScreen by remember { mutableStateOf<FullScreen?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    when (fullScreen) {
        FullScreen.Campaign -> {
            CampaignSessionScreen(onClose = { fullScreen = null })
            return
        }
        FullScreen.TimeAttack -> {
            TimeAttackScreen(onClose = { fullScreen = null })
            return
        }
        null -> Unit
    }

    val tabs = listOf(
        Tab("홈", Icons.Filled.Home),
        Tab("도전", Icons.Filled.EmojiEvents),
        Tab("설정", Icons.Filled.Settings),
    )

    Scaffold(
        containerColor = AppColors.Bg,
        bottomBar = {
            NavigationBar(containerColor = AppColors.BgElevated) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Accent,
                            selectedTextColor = AppColors.Accent,
                            unselectedIconColor = AppColors.TextTertiary,
                            unselectedTextColor = AppColors.TextTertiary,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onContinue = { fullScreen = FullScreen.Campaign },
            )
            1 -> ChallengeScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onStart = { fullScreen = FullScreen.TimeAttack },
            )
            else -> SettingsScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
