package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.Detail
import moe.shizuku.manager.ui.component.ExpressiveCard

/**
 * The things that are gone to rather than lived in.
 *
 * Every one of these was a tab of its own before, which is most of the bar's width spent on
 * screens that are largely empty when you land on them: you open the app-ops list to look one
 * app up, and you open the shell to run something and leave. A tab is for a place the app
 * keeps you in, so this is a list of the others instead, and the next one to earn a place here
 * costs a line rather than a fifth of the bar.
 *
 * Each tile says what it is for, because the name alone cannot: "App ops" is the screen the
 * platform's own hidden switches live behind, and "Shell" is a shell as the uid Shizuku runs
 * as, which is the one thing an app cannot be by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsScreen(bottomPadding: Dp, onOpenDetail: (Detail) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_labs)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                ExpressiveCard(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = stringResource(R.string.tab_manage),
                    body = stringResource(R.string.labs_app_ops_summary),
                    onClick = { onOpenDetail(Detail.APP_OPS) }
                )
            }

            item {
                ExpressiveCard(
                    icon = Icons.Outlined.Terminal,
                    title = stringResource(R.string.tab_shell),
                    body = stringResource(R.string.labs_shell_summary),
                    onClick = { onOpenDetail(Detail.SHELL) }
                )
            }
        }
    }
}
