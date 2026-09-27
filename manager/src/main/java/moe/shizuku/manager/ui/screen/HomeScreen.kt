package moe.shizuku.manager.ui.screen

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.start.StartStatus
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.ui.component.SegmentedColumn
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.UpdateHelper
import rikka.core.util.ClipboardUtils
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current

    var running by remember { mutableStateOf(ShizukuStateMachine.isRunning()) }
    var uid by remember { mutableStateOf(if (running) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1) }
    var version by remember { mutableStateOf(if (running) runCatching { Shizuku.getVersion() }.getOrDefault(0) else 0) }
    var batteryIgnored by remember {
        mutableStateOf(SettingsHelper.isIgnoringBatteryOptimizations(context))
    }
    var showAdbCommand by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var rebootRequired by remember { mutableStateOf(false) }
    var duplicateApp by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    val startStatus by StartStatusReporter.status.collectAsState()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener: (ShizukuStateMachine.State) -> Unit = {
            running = it == ShizukuStateMachine.State.RUNNING
            if (running) {
                uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
                version = runCatching { Shizuku.getVersion() }.getOrDefault(0)
            }
        }
        ShizukuStateMachine.addListener(listener)
        onDispose { ShizukuStateMachine.removeListener(listener) }
    }

    // Refresh on every resume, like the old home screen did.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ShizukuStateMachine.update()
        batteryIgnored = SettingsHelper.isIgnoringBatteryOptimizations(context)
    }

    LaunchedEffect(Unit) {
        ShizukuStateMachine.update()

        // After reinstalling under a different package name (stealth mode) the
        // system may not recognize the Shizuku permission until a reboot, or a
        // duplicate app may own it.
        try {
            context.packageManager.getPermissionGroupInfo(Manifest.permission_group.API, 0)
            val permission = context.packageManager.getPermissionInfo(Manifest.permission.API_V23, 0)
            if (permission.packageName != context.packageName) {
                duplicateApp = true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            rebootRequired = true
        }

        updateAvailable = runCatching {
            UpdateHelper.isCheckForUpdatesEnabled() && UpdateHelper.isNewUpdateAvailable()
        }.getOrDefault(false)
        if (updateAvailable) {
            runCatching { UpdateHelper.updateLastPromptedVersion() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            if (updateAvailable) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.snackbar_update_available),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { UpdateHelper.update() }
                                    updateAvailable = false
                                }
                            }) { Text(stringResource(R.string.snackbar_action_update)) }
                        }
                    }
                }
            }

            (startStatus as? StartStatus.Failed)?.let { failed ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(R.string.start_failed)}: ${failed.message}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { StartStatusReporter.clear() }) {
                                Text(stringResource(R.string.action_dismiss))
                            }
                        }
                    }
                }
            }

            if (!batteryIgnored) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.home_battery_warning),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = {
                                SettingsHelper.requestIgnoreBatteryOptimizationsPrivileged(context) {
                                    batteryIgnored = SettingsHelper.isIgnoringBatteryOptimizations(context)
                                }
                            }) { Text(stringResource(R.string.snackbar_action_fix)) }
                        }
                    }
                }
            }

            item {
                StatusCard(
                    running = running,
                    starting = startStatus is StartStatus.Starting,
                    version = version,
                    uid = uid,
                    onStart = { ShizukuReceiverStarter.start(context, userInitiated = true) },
                    onStop = { confirmStop = true }
                )
            }

            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_wireless_adb_title)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { ShizukuReceiverStarter.start(context, userInitiated = true) }
                        )
                    }
                    if (EnvironmentUtils.isRooted()) {
                        item {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.home_root_title)) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                onClick = {
                                    context.startActivity(
                                        Intent(context, StarterActivity::class.java)
                                            .putExtra(StarterActivity.EXTRA_IS_ROOT, true)
                                    )
                                }
                            )
                        }
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_system_title)) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = {
                                context.startActivity(
                                    Intent(context, StarterActivity::class.java)
                                        .putExtra(StarterActivity.EXTRA_IS_SYSTEM, true)
                                )
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.intents_adb_command)) },
                            supportingContent = {
                                Text(Starter.adbCommand, fontFamily = FontFamily.Monospace)
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = { showAdbCommand = true }
                        )
                    }
                }
            }

            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_info_title)) },
                            supportingContent = { Text(if (running) "v$version" else "-") }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text("UID") },
                            supportingContent = { Text(uidLabel(uid)) }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text("Transport") },
                            supportingContent = { Text(transportLabel()) }
                        )
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            text = { Text(stringResource(R.string.dialog_stop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    ShizukuSettings.setManuallyStopped(true)
                    ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
                    runCatching { Shizuku.exit() }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (rebootRequired) {
        ExitDialog(
            R.string.home_dialog_reboot_required_title,
            R.string.home_dialog_reboot_required_message
        )
    }

    if (duplicateApp) {
        ExitDialog(
            R.string.home_dialog_duplicate_app_detected_title,
            R.string.home_dialog_duplicate_app_detected_message
        )
    }

    if (showAdbCommand) {
        AlertDialog(
            onDismissRequest = { showAdbCommand = false },
            title = { Text(stringResource(R.string.intents_adb_command)) },
            text = { Text(Starter.adbCommand, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(onClick = {
                    if (ClipboardUtils.put(context, Starter.adbCommand)) {
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }
                    showAdbCommand = false
                }) { Text(stringResource(R.string.intents_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdbCommand = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    running: Boolean,
    starting: Boolean,
    version: Int,
    uid: Int,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    // "Stopped" is a normal state, not an error — a red container made the
    // primary action clash. Use a neutral surface instead.
    val containerColor = if (running) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (running) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large
    ) {
        Column {
            ListItem(
                leadingContent = {
                    Icon(
                        if (running) Icons.Rounded.CheckCircle else Icons.Rounded.StopCircle,
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        stringResource(
                            if (running) R.string.status_running_short else R.string.status_stopped_short
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    when {
                        running -> Text(
                            stringResource(
                                R.string.home_status_service_version,
                                uidLabel(uid),
                                version.toString()
                            )
                        )

                        starting -> Text(stringResource(R.string.start_waiting_for_service))
                        else -> Unit
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (running) {
                    OutlinedButton(
                        onClick = onStop,
                        // Explicit outline so it reads as a button on the tonal card.
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
                    ) { Text(stringResource(R.string.action_stop)) }
                } else {
                    Button(onClick = onStart) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.action_start))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitDialog(titleRes: Int, messageRes: Int) {
    val activity = LocalContext.current as? Activity
    AlertDialog(
        onDismissRequest = { activity?.finishAffinity() },
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(onClick = { activity?.finishAffinity() }) {
                Text(stringResource(R.string.home_dialog_button_exit))
            }
        }
    )
}

private fun uidLabel(uid: Int): String = when (uid) {
    0 -> "root"
    2000 -> "adb"
    -1 -> "-"
    else -> "uid $uid"
}

private fun transportLabel(): String = when (ShizukuSettings.getLastAdbTransport()) {
    ShizukuSettings.ADB_TRANSPORT_TLS -> "Wireless debugging"
    ShizukuSettings.ADB_TRANSPORT_TCP -> "USB debugging"
    else -> "Unknown"
}
