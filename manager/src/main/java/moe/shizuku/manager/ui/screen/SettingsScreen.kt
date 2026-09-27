package moe.shizuku.manager.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
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
import moe.shizuku.manager.ui.Detail
import moe.shizuku.manager.ui.component.SegmentedColumn
import moe.shizuku.manager.ui.theme.ThemeState
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenDetail: (Detail) -> Unit) {
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
    var nightMode by remember { mutableStateOf(ShizukuSettings.getNightMode()) }
    var themeDialog by remember { mutableStateOf(false) }
    var useSystemColor by remember {
        mutableStateOf(ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false))
    }
    var blackNight by remember {
        mutableStateOf(ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false))
    }
    var batteryIgnored by remember {
        mutableStateOf(SettingsHelper.isIgnoringBatteryOptimizations(context))
    }
    var legacyPairing by remember { mutableStateOf(ShizukuSettings.getLegacyPairing()) }

    var closeTcpDialog by remember { mutableStateOf(false) }
    var restartAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var restartWifiNote by remember { mutableStateOf(false) }
    var batteryPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

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
                                Switch(checked = startOnBoot, onCheckedChange = { checked ->
                                    if (checked && needsBatteryPrompt(context)) {
                                        batteryPrompt = {
                                            ShizukuSettings.setStartOnBoot(context, true)
                                            startOnBoot = ShizukuSettings.getStartOnBoot(context)
                                        }
                                        startOnBoot = false
                                    } else {
                                        ShizukuSettings.setStartOnBoot(context, checked)
                                        startOnBoot = ShizukuSettings.getStartOnBoot(context)
                                    }
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_watchdog)) },
                            supportingContent = { Text(stringResource(R.string.settings_watchdog_summary)) },
                            trailingContent = {
                                Switch(checked = watchdog, onCheckedChange = { checked ->
                                    if (checked && needsBatteryPrompt(context)) {
                                        batteryPrompt = {
                                            ShizukuSettings.setWatchdog(context, true)
                                            watchdog = true
                                        }
                                    } else {
                                        ShizukuSettings.setWatchdog(context, checked)
                                        watchdog = checked
                                    }
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
                                Switch(checked = tcpMode, onCheckedChange = { checked ->
                                    when {
                                        !checked && EnvironmentUtils.getAdbTcpPort() > 0 ->
                                            closeTcpDialog = true

                                        ShizukuStateMachine.isRunning() &&
                                            needsRestart(ShizukuSettings.Keys.KEY_TCP_MODE, checked) -> {
                                            restartAction = {
                                                ShizukuSettings.setTcpMode(checked)
                                                tcpMode = checked
                                            }
                                            restartWifiNote = true
                                        }

                                        else -> {
                                            ShizukuSettings.setTcpMode(checked)
                                            tcpMode = checked
                                        }
                                    }
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
                            headlineContent = { Text(stringResource(R.string.settings_legacy_pairing)) },
                            trailingContent = {
                                Switch(checked = legacyPairing, onCheckedChange = {
                                    ShizukuSettings.getPreferences().edit()
                                        .putBoolean(ShizukuSettings.Keys.KEY_LEGACY_PAIRING, it).apply()
                                    legacyPairing = it
                                })
                            }
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
                            headlineContent = { Text(stringResource(R.string.settings_theme)) },
                            supportingContent = {
                                Text(
                                    when (nightMode) {
                                        AppCompatDelegate.MODE_NIGHT_NO -> stringResource(R.string.settings_theme_light)
                                        AppCompatDelegate.MODE_NIGHT_YES -> stringResource(R.string.settings_theme_dark)
                                        else -> stringResource(R.string.settings_theme_system)
                                    }
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { themeDialog = true }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_use_system_color)) },
                            trailingContent = {
                                Switch(checked = useSystemColor, onCheckedChange = {
                                    ShizukuSettings.getPreferences().edit()
                                        .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, it).apply()
                                    useSystemColor = it
                                    ThemeState.refresh()
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_black_night_theme)) },
                            supportingContent = { Text(stringResource(R.string.settings_black_night_theme_summary)) },
                            trailingContent = {
                                Switch(checked = blackNight, onCheckedChange = {
                                    ShizukuSettings.getPreferences().edit()
                                        .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, it).apply()
                                    blackNight = it
                                    ThemeState.refresh()
                                })
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_language)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                                                .setData(Uri.fromParts("package", context.packageName, null))
                                        )
                                    }
                                }
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.about_title)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { onOpenDetail(Detail.ABOUT) }
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

    restartAction?.let { action ->
        AlertDialog(
            onDismissRequest = { restartAction = null },
            title = { Text(stringResource(R.string.settings_restart_dialog_title)) },
            text = {
                Text(
                    buildString {
                        append(stringResource(R.string.settings_restart_dialog_message))
                        if (restartWifiNote) {
                            append(stringResource(R.string.settings_restart_dialog_message_wifi_required))
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    restartAction = null
                    ShizukuReceiverStarter.start(context, forceStart = true, userInitiated = true)
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { restartAction = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (closeTcpDialog) {
        AlertDialog(
            onDismissRequest = { closeTcpDialog = false },
            title = { Text(stringResource(android.R.string.dialog_alert_title)) },
            text = { Text(stringResource(R.string.settings_tcp_mode_dialog_close_port)) },
            confirmButton = {
                TextButton(onClick = {
                    closeTcpDialog = false
                    scope.launch {
                        val port = EnvironmentUtils.getAdbTcpPort()
                        if (port > 0) AdbStarter.stopTcp(context, port)
                        ShizukuSettings.setTcpMode(false)
                        tcpMode = false
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { closeTcpDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    batteryPrompt?.let { action ->
        AlertDialog(
            onDismissRequest = { batteryPrompt = null },
            title = { Text(stringResource(R.string.tools_battery)) },
            text = { Text(stringResource(R.string.snackbar_battery_optimization_settings)) },
            confirmButton = {
                TextButton(onClick = {
                    SettingsHelper.requestIgnoreBatteryOptimizationsPrivileged(context) {
                        batteryIgnored = SettingsHelper.isIgnoringBatteryOptimizations(context)
                    }
                    action()
                    batteryPrompt = null
                }) { Text(stringResource(R.string.snackbar_action_fix)) }
            },
            dismissButton = {
                TextButton(onClick = { batteryPrompt = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString() to stringResource(R.string.settings_theme_system),
                AppCompatDelegate.MODE_NIGHT_NO.toString() to stringResource(R.string.settings_theme_light),
                AppCompatDelegate.MODE_NIGHT_YES.toString() to stringResource(R.string.settings_theme_dark),
            ),
            selected = nightMode.toString(),
            onDismiss = { themeDialog = false },
            onSelect = {
                val value = it.toIntOrNull() ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ShizukuSettings.getPreferences().edit()
                    .putInt(ShizukuSettings.Keys.KEY_NIGHT_MODE, value).apply()
                AppCompatDelegate.setDefaultNightMode(value)
                nightMode = value
                ThemeState.refresh()
                themeDialog = false
            }
        )
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
                    val value = draft.toIntOrNull()?.takeIf { it in 1..65535 }
                    tcpPortDialog = false
                    if (value != null) {
                        if (ShizukuStateMachine.isRunning() &&
                            needsRestart(ShizukuSettings.Keys.KEY_TCP_PORT, value)
                        ) {
                            restartAction = {
                                ShizukuSettings.setTcpPort(value)
                                tcpPort = value.toString()
                            }
                            restartWifiNote = false
                        } else {
                            ShizukuSettings.setTcpPort(value)
                            tcpPort = value.toString()
                        }
                    }
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

private fun needsRestart(setting: String, newValue: Any? = null): Boolean {
    val currentPort = EnvironmentUtils.getAdbTcpPort()
    return when (setting) {
        ShizukuSettings.Keys.KEY_TCP_MODE ->
            (currentPort > 0) != (newValue as? Boolean ?: ShizukuSettings.getTcpMode())

        ShizukuSettings.Keys.KEY_TCP_PORT ->
            currentPort > 0 && currentPort != (newValue as? Int ?: ShizukuSettings.getTcpPort())

        else -> false
    }
}

private fun needsBatteryPrompt(context: android.content.Context): Boolean =
    !EnvironmentUtils.isTelevision() && !SettingsHelper.isIgnoringBatteryOptimizations(context)

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
