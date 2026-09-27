package moe.shizuku.manager.ui.screen

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import moe.shizuku.manager.BuildConfig
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.Helps
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.home.showAccessibilityDialog
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.start.StartFailureKind
import moe.shizuku.manager.start.StartStatus
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.start.openAdbPortAndStart
import moe.shizuku.manager.start.runningStartMethodLabelRes
import moe.shizuku.manager.start.startMethodLabelRes
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.ui.component.SegmentedColumn
import moe.shizuku.manager.ui.theme.LocalAmoledTheme
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.ui.component.stripHtmlTags
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.runShellCommand
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
    var rebootRequired by remember { mutableStateOf(false) }
    var duplicateApp by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var rooted by remember { mutableStateOf(false) }
    var startMethod by remember { mutableStateOf(ShizukuSettings.getStartMethod()) }
    var selinuxRes by remember { mutableStateOf<Int?>(null) }
    var seccompRes by remember { mutableStateOf<Int?>(null) }
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
        startMethod = ShizukuSettings.getStartMethod()
        // A start that is already running has nothing left to report; without this a
        // "starting" state from a path that finishes elsewhere would stick.
        if (ShizukuStateMachine.isRunning()) StartStatusReporter.clear()
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

        // Shell.getShell() can block and triggers the root request, so probe it
        // once off the main thread instead of on every recomposition.
        rooted = withContext(Dispatchers.IO) {
            runCatching { EnvironmentUtils.isRooted() }.getOrDefault(false)
        }
    }

    // Re-read when the server comes up: the SELinux row is answered by the server, so it
    // only has a value while one is running.
    LaunchedEffect(running) {
        withContext(Dispatchers.IO) {
            val (selinux, seccomp) = readDeviceStatus()
            selinuxRes = selinux
            seccompRes = seccomp
        }

    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        )

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
                        // Message on top, actions stacked full-width underneath: in a row
                        // they sat shoulder-to-shoulder and read as one control.
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "${stringResource(R.string.start_failed)}: ${failed.message}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (failed.kind == StartFailureKind.WIFI) {
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    SettingsPage.InternetPanel.buildIntent(context)
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) { Text(stringResource(R.string.action_connect_wifi)) }
                                } else if (failed.kind == StartFailureKind.PORT) {
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            // Opens the port and starts over it; if that
                                            // needs Wi-Fi, the card comes back offering
                                            // Connect instead.
                                            scope.launch { openAdbPortAndStart(context) }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) { Text(stringResource(R.string.action_open_adb_port)) }
                                } else if (failed.kind == StartFailureKind.PAIRING) {
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                // Pair without typing: the code is read out
                                                // of the system dialog. The manual flow
                                                // stays one tap away in that dialog.
                                                runCatching { context.showAccessibilityDialog() }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) { Text(stringResource(R.string.action_pair)) }
                                }

                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { StartStatusReporter.clear() },
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) { Text(stringResource(R.string.action_dismiss)) }
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.home_battery_warning),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            ) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        SettingsHelper.requestIgnoreBatteryOptimizationsPrivileged(context) {
                                            batteryIgnored = SettingsHelper.isIgnoringBatteryOptimizations(context)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) { Text(stringResource(R.string.snackbar_action_fix)) }
                            }
                        }
                    }
                }
            }

            item {
                StatusCard(
                    running = running,
                    version = version,
                    uid = uid,
                    startMethodLabelRes = startMethodLabelRes(startMethod)
                )
            }

            item {
                // The actions sit outside the status card and stay on screen in the
                // same place, so the buttons don't move around as the state changes.
                ServerActionButtons(
                    running = running,
                    starting = startStatus is StartStatus.Starting,
                    // Uses whichever method is set in Settings; never guesses from the
                    // last one that happened to work.
                    onStart = { ShizukuReceiverStarter.start(context, userInitiated = true) },
                    // One tap, no confirmation: stopping is a normal action and the
                    // dialog only slowed it down.
                    onStop = {
                        ShizukuSettings.setManuallyStopped(true)
                        ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
                        runCatching { Shizuku.exit() }
                    },
                    // A bounce: forceStart replaces the running server instead of
                    // being ignored as "already running".
                    onRestart = {
                        ShizukuReceiverStarter.start(
                            context,
                            forceStart = true,
                            userInitiated = true
                        )
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
                            onClick = {
                                ShizukuReceiverStarter.start(
                                    context,
                                    userInitiated = true,
                                    startMethod = ShizukuSettings.StartMethod.WIRELESS
                                )
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_usb_adb_title)) },
                            supportingContent = {
                                Text(stringResource(R.string.home_usb_adb_summary).stripHtmlTags())
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            onClick = {
                                ShizukuReceiverStarter.start(
                                    context,
                                    userInitiated = true,
                                    startMethod = ShizukuSettings.StartMethod.USB
                                )
                            }
                        )
                    }
                    if (rooted) {
                        val rootDescription = stringResource(
                            R.string.home_root_description,
                            "<b><a href=\"${Helps.SUI.get()}\">Sui</a></b>",
                            "Sui"
                        ).stripHtmlTags()

                        item {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(
                                            if (running && uid == 0) R.string.home_root_button_restart
                                            else R.string.home_root_title
                                        )
                                    )
                                },
                                supportingContent = { Text(rootDescription) },
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
                                ShizukuReceiverStarter.start(
                                    context,
                                    userInitiated = true,
                                    startMethod = ShizukuSettings.StartMethod.SYSTEM
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
                    // Same rows KernelSU's manager shows, so the device is described the
                    // same way in both apps.
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_device_manager_version)) },
                            supportingContent = { Text(BuildConfig.VERSION_NAME) }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_device_kernel_version)) },
                            supportingContent = { Text(kernelVersion()) }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_device_model)) },
                            supportingContent = { Text(deviceModel()) }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_device_fingerprint)) },
                            supportingContent = { Text(Build.FINGERPRINT ?: "-") }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_device_selinux)) },
                            supportingContent = { Text(selinuxRes?.let { stringResource(it) } ?: "-") }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.home_device_seccomp)) },
                            supportingContent = { Text(seccompRes?.let { stringResource(it) } ?: "-") }
                        )
                    }
                }
            }
        }
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
    version: Int,
    uid: Int,
    @StringRes startMethodLabelRes: Int
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

    // On the pure black theme the stopped card is the same colour as the page, so it
    // gets the same hairline outline as the other cards. The running card uses a
    // tinted container that the page can't swallow.
    val outlined = LocalAmoledTheme.current && !running

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (outlined) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.large
                    )
                } else {
                    Modifier
                }
            ),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large
    ) {
        // Status on the left, action on the right, so the card isn't half empty.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (running) Icons.Rounded.CheckCircle else Icons.Rounded.StopCircle,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (running) R.string.status_running_short else R.string.status_stopped_short
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                // Everything about the server at a glance: how it is running now, what
                // the Start button below will do next, and what it is running as.
                StatusFact(
                    R.string.home_status_started_with,
                    if (running) runningMethodLabel(uid) else stringResource(R.string.status_value_none)
                )
                StatusFact(R.string.settings_start_method, stringResource(startMethodLabelRes))
                StatusFact(
                    R.string.home_info_title,
                    if (running) "v$version" else stringResource(R.string.status_value_none)
                )
                StatusFact(R.string.uid_label, uidLabel(uid))
            }

        }
    }
}

/**
 * KernelSU's fallback for the device name: manufacturer, brand when it differs, model.
 * Their per-manufacturer "marketing name" properties (Samsung, Xiaomi, OPPO…) could be
 * layered on top later; on a Nothing phone this is the name KSU shows too.
 */
private fun deviceModel(): String = buildString {
    // Capitalised because manufacturers report themselves in lowercase ("samsung"),
    // and without their marketing-name layer there is nothing else to show.
    append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
    if (!Build.BRAND.equals(Build.MANUFACTURER, ignoreCase = true)) {
        append(' ').append(Build.BRAND)
    }
    append(' ').append(Build.MODEL)
}

private fun kernelVersion(): String = System.getProperty("os.version").orEmpty().ifEmpty { "-" }

/**
 * The kernel's SELinux state, worked out the way KernelSU's manager works it out:
 * ask `getenforce` as an ordinary app, and treat a refusal as the answer — selinuxfs is
 * world-readable on disk but denied to app domains, and a permissive policy would have
 * allowed the read. That is what makes this work with no root, no server and no KSU.
 */
@StringRes
private fun readSelinuxStatus(): Int? {
    // With a server running, read the switch itself rather than infer it.
    val raw = runShellCommand("cat /sys/fs/selinux/enforce")
        ?: runCatching { File("/sys/fs/selinux/enforce").readText().trim() }.getOrNull()

    when (raw) {
        "1" -> return R.string.selinux_enforcing
        "0" -> return R.string.selinux_permissive
    }

    val (stdout, stderr) = runCatching {
        val process = ProcessBuilder("/system/bin/sh", "-c", "getenforce").start()
        val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val err = process.errorStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        out to err
    }.getOrNull() ?: return null

    return when {
        stdout.equals("Enforcing", true) -> R.string.selinux_enforcing
        stdout.equals("Permissive", true) -> R.string.selinux_permissive
        stdout.equals("Disabled", true) -> R.string.selinux_disabled
        // Refused: only an enforcing policy says no here.
        stderr.contains("Permission denied") -> R.string.selinux_enforcing
        else -> null
    }
}

/** Seccomp mode for this process, from our own /proc entry. */
@StringRes
private fun readSeccompStatus(): Int? {
    val mode = runCatching {
        File("/proc/self/status").readLines()
            .firstOrNull { it.startsWith("Seccomp:") }
            ?.substringAfter(':')
            ?.trim()
    }.getOrNull()

    return when (mode) {
        "0" -> R.string.seccomp_disabled
        "1" -> R.string.seccomp_strict
        "2" -> R.string.seccomp_filter
        else -> null
    }
}

/** Reads what the device card shows, off the main thread. */
private fun readDeviceStatus(): Pair<Int?, Int?> = readSelinuxStatus() to readSeccompStatus()

/** A label/value pair inside the status card. */
@Composable
private fun StatusFact(@StringRes labelRes: Int, value: String) {
    Row(modifier = Modifier.padding(top = 2.dp)) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** Restart's blue — see the note where it is used. */
private val RestartBlue = Color(0xFF2563EB)

/**
 * Start, Stop and Restart below the status card, all always visible. An action that
 * doesn't apply right now is disabled rather than hidden, so the row never shifts.
 * A start in flight shows as progress inside Start, where the eye already is.
 */
@Composable
private fun ServerActionButtons(
    running: Boolean,
    starting: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = !running && !starting,
            onClick = onStart
        ) {
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    // The button is disabled while starting, so pick a colour that
                    // still reads against the disabled container.
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(stringResource(R.string.action_start))
            }
        }

        // Red: stopping is the destructive one, and the theme's error role stays red
        // whatever the seed colour is.
        Button(
            modifier = Modifier.weight(1f),
            enabled = running,
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(stringResource(R.string.action_stop))
        }

        // Blue on purpose: Material 3 has no blue role and the palette is seeded from
        // the wallpaper or the brand colour, so a role here would come out indigo or
        // teal depending on the theme.
        Button(
            modifier = Modifier.weight(1f),
            enabled = running,
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(
                containerColor = RestartBlue,
                contentColor = Color.White
            )
        ) {
            Text(stringResource(R.string.action_restart))
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

/**
 * The method the running server was started with. Falls back to the ADB transport when
 * no launch of ours was recorded — e.g. the server was started by another tool.
 */
@Composable
private fun runningMethodLabel(uid: Int): String =
    // Shared with the notifications, so both name the method the same way.
    runningStartMethodLabelRes()?.let { stringResource(it) } ?: transportLabel(uid)

/**
 * The wire the last launch used. Only reached when no method was recorded, so it keeps
 * the "adb (...)" wording that can't be confused with a start method.
 */
@Composable
private fun transportLabel(uid: Int): String = when {
    uid == 0 -> stringResource(R.string.start_method_root)
    // "adb (...)" rather than the method names, so the transport can't be confused
    // with the start method shown next to it.
    ShizukuSettings.getLastAdbTransport() == ShizukuSettings.ADB_TRANSPORT_TCP ->
        stringResource(R.string.home_status_adb_usb)

    ShizukuSettings.getLastAdbTransport() == ShizukuSettings.ADB_TRANSPORT_TLS ->
        stringResource(R.string.home_status_adb_wireless)

    else -> stringResource(R.string.transport_unknown)
}
