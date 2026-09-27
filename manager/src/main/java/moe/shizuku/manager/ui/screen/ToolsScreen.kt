package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.Detail
import moe.shizuku.manager.ui.component.SegmentedColumn
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.utils.SettingsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onOpenDetail: (Detail) -> Unit) {
    val context = LocalContext.current

    var watchdog by remember { mutableStateOf(ShizukuSettings.getWatchdog()) }
    var startOnBoot by remember { mutableStateOf(ShizukuSettings.getStartOnBoot(context)) }
    var batteryIgnored by remember {
        mutableStateOf(SettingsHelper.isIgnoringBatteryOptimizations(context))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_tools)) })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_watchdog)) },
                            supportingContent = { Text(stringResource(R.string.settings_watchdog_summary)) },
                            trailingContent = {
                                Switch(
                                    checked = watchdog,
                                    onCheckedChange = {
                                        ShizukuSettings.setWatchdog(context, it)
                                        watchdog = it
                                    }
                                )
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_start_on_boot)) },
                            trailingContent = {
                                Switch(
                                    checked = startOnBoot,
                                    onCheckedChange = {
                                        ShizukuSettings.setStartOnBoot(context, it)
                                        startOnBoot = ShizukuSettings.getStartOnBoot(context)
                                    }
                                )
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.tools_battery)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (batteryIgnored) R.string.tools_battery_ignored
                                        else R.string.tools_battery_not_ignored
                                    )
                                )
                            },
                            trailingContent = if (!batteryIgnored) {
                                {
                                    TextButton(onClick = {
                                        SettingsHelper.requestIgnoreBatteryOptimizationsPrivileged(context) {
                                            batteryIgnored = SettingsHelper.isIgnoringBatteryOptimizations(context)
                                        }
                                    }) { Text(stringResource(R.string.snackbar_action_fix)) }
                                }
                            } else null
                        )
                    }
                }
            }

            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.tools_stealth)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { onOpenDetail(Detail.STEALTH) }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.tools_terminal)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { onOpenDetail(Detail.TERMINAL) }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.intents_title)) },
                            supportingContent = { Text(stringResource(R.string.intents_description)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { onOpenDetail(Detail.INTENTS) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.tab_tools),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
