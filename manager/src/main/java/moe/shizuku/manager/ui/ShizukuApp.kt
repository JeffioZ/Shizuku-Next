package moe.shizuku.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.screen.AppsScreen
import moe.shizuku.manager.ui.screen.HomeScreen
import moe.shizuku.manager.ui.screen.IntentsScreen
import moe.shizuku.manager.ui.screen.ManageScreen
import moe.shizuku.manager.ui.screen.PermissionsScreen
import moe.shizuku.manager.ui.screen.SettingsScreen
import moe.shizuku.manager.ui.screen.StealthScreen
import moe.shizuku.manager.ui.screen.TerminalScreen
import moe.shizuku.manager.ui.theme.LocalAmoledTheme
import moe.shizuku.manager.ui.theme.ShizukuTheme

/** A secondary screen shown on top of the tab pager. */
enum class Detail { STEALTH, TERMINAL, INTENTS, PERMISSIONS }

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
    // Apps answers "which apps may use Shizuku"; Manage answers "what may they do on the
    // device", so they belong next to each other rather than either side of Settings.
    Tab(R.string.tab_manage, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings),
    Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun ShizukuApp() {
    ShizukuTheme {
        // Detail screens are shown outside the Scaffold, so wrap everything in a
        // Surface — otherwise LocalContentColor falls back to black and plain
        // Text becomes unreadable in dark themes.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Apply the status bar inset exactly once for every screen: the app
            // bars themselves have no insets, and the Scaffold opts out too.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            ) {
                // Both the open detail and the pager live here, above the switch between
                // the two, for two reasons: the pager used to be remembered inside the tab
                // layout, so opening a detail threw it away and closing the detail started
                // again on the first tab (back from a Settings screen landed on Home). And
                // neither was saveable, so a rotation dropped both and did the same thing.
                var detail by rememberSaveable { mutableStateOf<Detail?>(null) }
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                val current = detail

                if (current != null) {
                    BackHandler { detail = null }
                    CenteredContent {
                        when (current) {
                            Detail.STEALTH -> StealthScreen(onBack = { detail = null })
                            Detail.TERMINAL -> TerminalScreen(onBack = { detail = null })
                            Detail.INTENTS -> IntentsScreen(onBack = { detail = null })
                            Detail.PERMISSIONS -> PermissionsScreen(onBack = { detail = null })
                        }
                    }
                } else {
                    MainTabs(pagerState = pagerState, onOpenDetail = { detail = it })
                }
            }
        }
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainTabs(
    pagerState: PagerState,
    onOpenDetail: (Detail) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val bottomPadding = barHeight + FloatingToolbarDefaults.ScreenOffset +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior)
    ) {
        CenteredContent {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> HomeScreen(bottomPadding = bottomPadding)
                    1 -> AppsScreen(bottomPadding = bottomPadding)
                    2 -> ManageScreen(bottomPadding = bottomPadding)
                    3 -> SettingsScreen(bottomPadding = bottomPadding, onOpenDetail = onOpenDetail)
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = FloatingToolbarDefaults.ScreenOffset)
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
            contentPadding = PaddingValues(0.dp),
            scrollBehavior = scrollBehavior
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = FloatingToolbarDefaults.ContainerSize)
                    .then(
                        if (LocalAmoledTheme.current) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = FloatingToolbarDefaults.ContainerShape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(FloatingToolbarDefaults.ContentPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = {
                            if (!selected) scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        shapes = ToggleButtonShapes(
                            shape = CircleShape,
                            pressedShape = CircleShape,
                            checkedShape = CircleShape
                        ),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.label)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                            exit = shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec())
                        ) {
                            Text(
                                stringResource(tab.label),
                                modifier = Modifier
                                    .padding(start = ButtonDefaults.IconSpacing)
                                    .clearAndSetSemantics { }
                            )
                        }
                    }
                }
            }
        }
    }
}
