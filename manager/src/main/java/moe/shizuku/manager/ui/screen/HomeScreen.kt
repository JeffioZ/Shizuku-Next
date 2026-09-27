package moe.shizuku.manager.ui.screen

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.ui.component.SegmentedColumn
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
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

    LaunchedEffect(Unit) { ShizukuStateMachine.update() }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
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
                    version = version,
                    uid = uid,
                    onStart = { ShizukuReceiverStarter.start(context, userInitiated = true) },
                    onStop = {
                        ShizukuSettings.setManuallyStopped(true)
                        ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
                        runCatching { Shizuku.exit() }
                    }
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
    version: Int,
    uid: Int,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val containerColor = if (running) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (running) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
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
                    if (running) {
                        Text(stringResource(R.string.home_status_service_version, uidLabel(uid), version.toString()))
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
                    OutlinedButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
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
