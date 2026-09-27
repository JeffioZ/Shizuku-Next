package moe.shizuku.manager.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.settings.BugReportDialogActivity
import moe.shizuku.manager.ui.component.SegmentedColumn
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.utils.CustomTabsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    var startOnBoot by remember { mutableStateOf(ShizukuSettings.getStartOnBoot(context)) }
    var watchdog by remember { mutableStateOf(ShizukuSettings.getWatchdog()) }
    var autoDisableUsb by remember { mutableStateOf(ShizukuSettings.getAutoDisableUsbDebugging()) }
    var allowUsbFallback by remember { mutableStateOf(ShizukuSettings.getAllowUsbFallback()) }
    var waitForWifi by remember { mutableStateOf(ShizukuSettings.getWaitForWifi()) }
    var tcpMode by remember { mutableStateOf(ShizukuSettings.getTcpMode()) }
    var tcpPort by remember { mutableStateOf(ShizukuSettings.getTcpPort().toString()) }
    var systemStartMethod by remember { mutableStateOf(ShizukuSettings.getSystemStartMethod()) }
    var updateMode by remember { mutableStateOf(ShizukuSettings.getUpdateMode()) }

    var tcpPortDialog by remember { mutableStateOf(false) }
    var systemStartDialog by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_settings)) })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_start_on_boot)) },
                            trailingContent = {
                                Switch(checked = startOnBoot, onCheckedChange = {
                                    ShizukuSettings.setStartOnBoot(context, it)
                                    startOnBoot = ShizukuSettings.getStartOnBoot(context)
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_watchdog)) },
                            supportingContent = { Text(stringResource(R.string.settings_watchdog_summary)) },
                            trailingContent = {
                                Switch(checked = watchdog, onCheckedChange = {
                                    ShizukuSettings.setWatchdog(context, it)
                                    watchdog = it
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_auto_disable_usb_debugging)) },
                            supportingContent = { Text(stringResource(R.string.settings_auto_disable_usb_debugging_summary)) },
                            trailingContent = {
                                Switch(checked = autoDisableUsb, onCheckedChange = {
                                    ShizukuSettings.getPreferences().edit()
                                        .putBoolean(ShizukuSettings.Keys.KEY_AUTO_DISABLE_USB_DEBUGGING, it).apply()
                                    autoDisableUsb = it
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_allow_usb_fallback)) },
                            supportingContent = { Text(stringResource(R.string.settings_allow_usb_fallback_summary)) },
                            trailingContent = {
                                Switch(checked = allowUsbFallback, onCheckedChange = {
                                    ShizukuSettings.setAllowUsbFallback(it)
                                    allowUsbFallback = it
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_wait_for_wifi)) },
                            supportingContent = { Text(stringResource(R.string.settings_wait_for_wifi_summary)) },
                            trailingContent = {
                                Switch(checked = waitForWifi, onCheckedChange = {
                                    ShizukuSettings.setWaitForWifi(it)
                                    waitForWifi = it
                                })
                            }
                        )
                    }
                }
            }

            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_tcp_mode)) },
                            supportingContent = { Text(stringResource(R.string.settings_tcp_mode_summary)) },
                            trailingContent = {
                                Switch(checked = tcpMode, onCheckedChange = {
                                    ShizukuSettings.setTcpMode(it)
                                    tcpMode = it
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_tcp_port)) },
                            supportingContent = { Text(tcpPort) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { tcpPortDialog = true }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_system_start_method)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (systemStartMethod == ShizukuSettings.SYSTEM_START_EXPLOIT)
                                            R.string.settings_system_start_method_exploit
                                        else R.string.settings_system_start_method_custom
                                    )
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { systemStartDialog = true }
                        )
                    }
                }
            }

            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.check_for_updates)) },
                            supportingContent = {
                                Text(
                                    when (updateMode) {
                                        ShizukuSettings.UpdateMode.OFF -> stringResource(R.string.off)
                                        ShizukuSettings.UpdateMode.BETA -> stringResource(R.string.settings_update_beta)
                                        else -> stringResource(R.string.settings_update_stable)
                                    }
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { updateDialog = true }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_help)) },
                            supportingContent = { Text(stringResource(R.string.tab_settings)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = {
                                CustomTabsHelper.launchUrlOrCopy(context, context.getString(R.string.help_url))
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_report_bug)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = {
                                context.startActivity(Intent(context, BugReportDialogActivity::class.java))
                            }
                        )
                    }
                }
            }
        }
    }

    if (tcpPortDialog) {
        var draft by remember { mutableStateOf(tcpPort) }
        AlertDialog(
            onDismissRequest = { tcpPortDialog = false },
            title = { Text(stringResource(R.string.settings_tcp_port)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { s -> draft = s.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    draft.toIntOrNull()?.takeIf { it in 1..65535 }?.let {
                        ShizukuSettings.setTcpPort(it)
                        tcpPort = it.toString()
                    }
                    tcpPortDialog = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { tcpPortDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    if (systemStartDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_system_start_method),
            options = listOf(
                ShizukuSettings.SYSTEM_START_EXPLOIT to stringResource(R.string.settings_system_start_method_exploit),
                ShizukuSettings.SYSTEM_START_CUSTOM to stringResource(R.string.settings_system_start_method_custom),
            ),
            selected = systemStartMethod,
            onDismiss = { systemStartDialog = false },
            onSelect = {
                ShizukuSettings.setSystemStartMethod(it)
                systemStartMethod = it
                systemStartDialog = false
            }
        )
    }

    if (updateDialog) {
        ChoiceDialog(
            title = stringResource(R.string.check_for_updates),
            options = listOf(
                ShizukuSettings.UpdateMode.OFF.toString() to stringResource(R.string.off),
                ShizukuSettings.UpdateMode.STABLE.toString() to stringResource(R.string.settings_update_stable),
                ShizukuSettings.UpdateMode.BETA.toString() to stringResource(R.string.settings_update_beta),
            ),
            selected = updateMode.toString(),
            onDismiss = { updateDialog = false },
            onSelect = {
                val value = it.toIntOrNull() ?: ShizukuSettings.UpdateMode.STABLE
                ShizukuSettings.getPreferences().edit()
                    .putInt(ShizukuSettings.Keys.KEY_UPDATE_MODE, value).apply()
                updateMode = value
                updateDialog = false
            }
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    TextButton(onClick = { onSelect(value) }) {
                        Text(if (value == selected) "✓ $label" else label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
