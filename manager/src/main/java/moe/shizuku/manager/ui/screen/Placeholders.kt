package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import moe.shizuku.manager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Placeholder(titleRes: Int) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(titleRes)) })
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ToolsScreen() = Placeholder(R.string.tab_tools)

@Composable
fun SettingsScreen() = Placeholder(R.string.tab_settings)
