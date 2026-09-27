package moe.shizuku.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.screen.AboutScreen
import moe.shizuku.manager.ui.screen.AppsScreen
import moe.shizuku.manager.ui.screen.HomeScreen
import moe.shizuku.manager.ui.screen.IntentsScreen
import moe.shizuku.manager.ui.screen.SettingsScreen
import moe.shizuku.manager.ui.screen.StealthScreen
import moe.shizuku.manager.ui.screen.TerminalScreen
import moe.shizuku.manager.ui.theme.ShizukuTheme

/** A secondary screen shown on top of the tab pager. */
enum class Detail { STEALTH, TERMINAL, INTENTS, ABOUT }

/**
 * On wide windows (tablets, foldables, desktop mode, mirrored displays) a
 * single-column layout stretched edge to edge looks sparse, so the content is
 * capped at this width and centred. On a phone the window is narrower than the
 * cap, so this has no effect.
 */
private val MaxContentWidth = 600.dp

private data class Tab(
    val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabs = listOf(
    Tab(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Tab(R.string.tab_apps, Icons.Filled.Apps, Icons.Outlined.Apps),
    Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun ShizukuApp() {
    ShizukuTheme {
        var detail by remember { mutableStateOf<Detail?>(null) }
        val current = detail

        if (current != null) {
            BackHandler { detail = null }
            CenteredContent {
                when (current) {
                    Detail.STEALTH -> StealthScreen(onBack = { detail = null })
                    Detail.TERMINAL -> TerminalScreen(onBack = { detail = null })
                    Detail.INTENTS -> IntentsScreen(onBack = { detail = null })
                    Detail.ABOUT -> AboutScreen(onBack = { detail = null })
                }
            }
        } else {
            MainTabs(onOpenDetail = { detail = it })
        }
    }
}

@Composable
private fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().then(modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                // widthIn must come first: fillMaxWidth sets min == max, which
                // would defeat a later widthIn cap.
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            content()
        }
    }
}

@Composable
private fun MainTabs(onOpenDetail: (Detail) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.label)
                            )
                        },
                        label = { Text(stringResource(tab.label)) }
                    )
                }
            }
        }
    ) { padding ->
        CenteredContent(modifier = Modifier.padding(padding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> HomeScreen()
                    1 -> AppsScreen()
                    2 -> SettingsScreen(onOpenDetail = onOpenDetail)
                }
            }
        }
    }
}
