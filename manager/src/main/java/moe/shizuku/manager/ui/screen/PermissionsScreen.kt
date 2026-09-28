package moe.shizuku.manager.ui.screen

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.home.isAccessibilityEnabled
import moe.shizuku.manager.start.hasPermission
import moe.shizuku.manager.ui.component.SegmentedCard
import moe.shizuku.manager.start.localNetworkPermission
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage
import rikka.core.util.ClipboardUtils

/**
 * Everything this app is not allowed to do until the user (or adb) says so, and whether each
 * one is currently allowed.
 *
 * Kept in one place because the states are spread across three different mechanisms — runtime
 * permissions, an adb-only permission, and the battery whitelist — and the app used to ask
 * for them at the moment they were needed, from whatever screen happened to trigger it. The
 * battery row moved here from settings for the same reason: it is the same question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var notifications by remember { mutableStateOf(context.hasPermission(POST_NOTIFICATIONS)) }
    var writeSecureSettings by remember { mutableStateOf(context.hasPermission(WRITE_SECURE_SETTINGS)) }
    var batteryIgnored by remember { mutableStateOf(SettingsHelper.isIgnoringBatteryOptimizations(context)) }
    var accessibility by remember { mutableStateOf(context.isAccessibilityEnabled()) }
    var localNetwork by remember {
        mutableStateOf(localNetworkPermission()?.let { context.hasPermission(it) } ?: true)
    }

    fun refresh() {
        notifications = context.hasPermission(POST_NOTIFICATIONS)
        writeSecureSettings = context.hasPermission(WRITE_SECURE_SETTINGS)
        batteryIgnored = SettingsHelper.isIgnoringBatteryOptimizations(context)
        accessibility = context.isAccessibilityEnabled()
        localNetwork = localNetworkPermission()?.let { context.hasPermission(it) } ?: true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    LaunchedEffect(Unit) { refresh() }
    // Coming back from the system's own screens (accessibility, battery) changes these.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val writeSecureSettingsCommand =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_permissions)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                PermissionRow(
                    headline = stringResource(R.string.permissions_notifications),
                    reason = stringResource(R.string.permissions_notifications_summary),
                    granted = notifications,
                    actionLabel = stringResource(R.string.permissions_action_allow),
                    onAction = { permissionLauncher.launch(POST_NOTIFICATIONS) }
                )
            }

            item {
                PermissionRow(
                    headline = stringResource(R.string.permissions_nearby),
                    reason = stringResource(R.string.permissions_nearby_summary),
                    granted = localNetwork,
                    actionLabel = stringResource(R.string.permissions_action_allow),
                    onAction = { localNetworkPermission()?.let { permissionLauncher.launch(it) } }
                )
            }

            item {
                PermissionRow(
                    headline = stringResource(R.string.permissions_write_secure_settings),
                    reason = stringResource(R.string.permissions_write_secure_settings_summary),
                    granted = writeSecureSettings,
                    // There is no dialog for this one: only adb can grant it, so the action
                    // copies the command to run.
                    actionLabel = stringResource(R.string.intents_copy),
                    onAction = {
                        if (ClipboardUtils.put(context, writeSecureSettingsCommand)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_copied_to_clipboard),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            item {
                PermissionRow(
                    headline = stringResource(R.string.permissions_accessibility),
                    reason = stringResource(R.string.permissions_accessibility_summary),
                    granted = accessibility,
                    actionLabel = stringResource(R.string.enable),
                    onAction = { SettingsPage.Accessibility.launch(context) }
                )
            }

            item {
                PermissionRow(
                    headline = stringResource(R.string.tools_battery),
                    reason = stringResource(R.string.permissions_battery_summary),
                    granted = batteryIgnored,
                    actionLabel = stringResource(R.string.snackbar_action_fix),
                    onAction = {
                        SettingsHelper.requestIgnoreBatteryOptimizationsPrivileged(context) {
                            refresh()
                        }
                    }
                )
            }
        }
    }
}

/**
 * One required permission: what it is for, whether it is allowed, and how to change that.
 *
 * Laid out by hand rather than with a list item, because these reasons run to several
 * lines and a list item puts its trailing content at the top of a tall row — the state
 * ended up level with the headline while the text carried on below it, which read as a
 * label for the paragraph rather than the answer for the row.
 */
@Composable
private fun PermissionRow(
    headline: String,
    reason: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    SegmentedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.bodyLarge)
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.permissions_allowed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
