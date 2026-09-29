package moe.shizuku.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.screen.AppsScreen
import moe.shizuku.manager.ui.screen.HomeScreen
import moe.shizuku.manager.ui.screen.IntentsScreen
import moe.shizuku.manager.ui.screen.ManageScreen
import moe.shizuku.manager.ui.screen.PermissionsScreen
import moe.shizuku.manager.ui.screen.SettingsScreen
import moe.shizuku.manager.ui.screen.ShellScreen
import moe.shizuku.manager.ui.screen.StealthScreen
import moe.shizuku.manager.ui.screen.TerminalScreen
import moe.shizuku.manager.ui.theme.LocalAmoledTheme
import moe.shizuku.manager.ui.theme.ShizukuTheme

/** A secondary screen shown on top of the tab pager. */
enum class Detail { STEALTH, TERMINAL, INTENTS, PERMISSIONS, SHELL }

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

/** The tab the shell button stands before, counted in tabs: [tabs] is Home, Apps, Manage, Settings. */
private const val SHELL_BEFORE_TAB = 3

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
        // Surface otherwise LocalContentColor falls back to black and plain
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
                    CenteredContent(
                        // A detail screen has no tab bar under it, so it is the one that has
                        // to keep clear of the navigation bar itself. The tab layout adds
                        // that inset to its own bottom padding; a detail had none, which on
                        // a device with navigation buttons put the shell's input row under
                        // them.
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        )
                    ) {
                        when (current) {
                            Detail.STEALTH -> StealthScreen(onBack = { detail = null })
                            Detail.TERMINAL -> TerminalScreen(onBack = { detail = null })
                            Detail.INTENTS -> IntentsScreen(onBack = { detail = null })
                            Detail.PERMISSIONS -> PermissionsScreen(onBack = { detail = null })
                            Detail.SHELL -> ShellScreen(onBack = { detail = null })
                        }
                    }
                } else {
                    MainTabs(pagerState = pagerState, onOpenDetail = { detail = it })
                }
            }
        }
    }
}

/**
 * The bar's one action, drawn exactly like the tabs beside it so the row keeps its rhythm.
 * It never shows as selected: pressing it opens the shell over the tabs rather than switching
 * to a page, so there is no state for it to hold.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellBarButton(onOpen: () -> Unit) {
    ToggleButton(
        checked = false,
        onCheckedChange = { onOpen() },
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
            Icons.Outlined.Terminal,
            contentDescription = stringResource(R.string.shell_title)
        )
    }
}

@Composable
private fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
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
    // The bar's height as a constant, rather than as something it reports back. Measuring it
    // and feeding that measurement into every page as bottom padding made the pages re-lay out
    // whenever the bar re-measured mid-animation — a scroll, a tab change — which is what read
    // as the content bouncing and left a gap the size of the bar's largest frame.
    val bottomPadding = FloatingToolbarDefaults.ContainerSize +
        FloatingToolbarDefaults.ScreenOffset +
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

        // The bar's own band, and the fade the pages run into. It is drawn here rather than
        // by the pages for two reasons: it leaves with the bar, and it is already there
        // before a page has scrolled a long list sitting at its top still has rows under
        // the bar, which a scrim that waited for a scroll would leave with a hard edge.
        // A gradient and not a blur: blurring a scrolling page means drawing it into an
        // offscreen layer and re-blurring it every frame.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Before the insets, so the fade reaches the bottom of the screen instead of
                // stopping at the top of the gesture area: below the bar is page the bar is
                // floating over too. It lands on the colour the pages themselves draw, so it
                // dissolves into them in every theme, black included.
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            contentAlignment = Alignment.TopCenter
        ) {
            // The bar hides by moving down by its own height, and it stands clear of the
            // gesture area — so it came to rest exactly that gap above the bottom of the
            // screen with a strip of itself still showing. Clipping this box to its own edge
            // cuts that strip off, and leaves the library's own timing alone: the box is
            // exactly the bar's height, so at rest nothing is cut, and on the way down all of
            // it is.
            // Centre-aligned, and so is the band: a full-width box with the bar in it would
            // otherwise leave the bar sitting against its left edge.
            Box(
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier,
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
                            // The shell is not a tab: it is a place you go and come back from, so
                            // it opens over the tabs rather than becoming a fifth page, which
                            // would have to keep a session and its output alive behind the
                            // others. It sits before Settings, where it is looked for.
                            if (index == SHELL_BEFORE_TAB) {
                                ShellBarButton { onOpenDetail(Detail.SHELL) }
                            }

                            val selected = pagerState.currentPage == index
                            ToggleButton(
                                checked = selected,
                            onCheckedChange = {
                                // Straight to the page, not through the ones between: the pager
                                // composes what it scrolls past, so going from Settings to Home
                                // started loading the Manage tab's six hundred apps on the way.
                                // Swiping still animates, because that is the gesture.
                                if (!selected) scope.launch { pagerState.scrollToPage(index) }
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
                                // Icons only, with the name kept for anyone reading it aloud: a
                                // label that grows out of the selected tab makes the bar wider
                                // and taller, and the pages under it move. The filled pill says
                                // which tab is current.
                                Icon(
                                    if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.label)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
