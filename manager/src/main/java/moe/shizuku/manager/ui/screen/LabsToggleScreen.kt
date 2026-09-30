package moe.shizuku.manager.ui.screen

import android.content.pm.PackageInfo
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.manage.PackageTools
import moe.shizuku.manager.ui.component.AppFilterChip
import moe.shizuku.manager.ui.component.AppIcon
import moe.shizuku.manager.ui.component.AppListSkeleton
import moe.shizuku.manager.ui.component.CenteredMessage
import moe.shizuku.manager.ui.component.ExpressiveSwitch
import moe.shizuku.manager.ui.component.SegmentedCard
import moe.shizuku.manager.ui.component.appLabel
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * One switch per app, over one state, with the filters that make such a list usable: everything,
 * what is blocked, and what is not.
 *
 * The two features that use it differ only in where that state lives. Autostart is an app op, and
 * the platform will hand over every app in it in a single command. The firewall's deny bit cannot
 * be asked for in bulk - the command takes one package, and six hundred round trips is not a
 * screen anyone waits for - so that list keeps its own record and says so, which is what a
 * firewall without root does, and why the app's own page reads the truth from the platform
 * instead.
 *
 * A row is the row the Apps tab uses, a card per app with its icon and its switch, because this
 * is the same kind of list asking a different question.
 */
enum class AppToggleFeature(
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    FIREWALL(R.string.tab_firewall, Icons.Outlined.Shield),
    AUTOSTART(R.string.tab_autostart, Icons.Outlined.RestartAlt);

    /**
     * The op behind Autostart. The firewall's bit is not an app op at all, so nothing reads
     * this for it.
     */
    val op: String
        get() = "RUN_ANY_IN_BACKGROUND"
}

/** Which slice of the list to show. */
private enum class ToggleFilter { ALL, ALLOWED, BLOCKED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsToggleScreen(
    feature: AppToggleFeature,
    bottomPadding: Dp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    var apps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var blocked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ToggleFilter.ALL) }
    var version by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(ShizukuStateMachine.isRunning()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(version) {
        loading = true
        Log.d(AppConstants.TAG, "${feature.name}: reading the apps and their state")

        apps = withContext(Dispatchers.IO) {
            runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        }
        blocked = withContext(Dispatchers.IO) { readBlocked(feature, context) }
        running = ShizukuStateMachine.isRunning()
        loading = false
    }

    /**
     * Flips one app, then reads the state back.
     *
     * The switch moves with the tap and the read is what settles it: a change the platform
     * refuses leaves the row where it was rather than showing something that did not happen,
     * which is the same trade the app-ops dialog makes.
     */
    fun toggle(packageName: String, shouldBlock: Boolean) {
        scope.launch {
            val applied = withContext(Dispatchers.IO) {
                when (feature) {
                    AppToggleFeature.AUTOSTART ->
                        PackageTools.setOpBlocked(context, packageName, feature.op, shouldBlock)

                    AppToggleFeature.FIREWALL ->
                        PackageTools.setNetworkBlocked(context, packageName, shouldBlock)
                }
            }
            if (!applied) {
                Log.w(AppConstants.TAG, "${feature.name}: $packageName would not change")
            }
            blocked = withContext(Dispatchers.IO) { readBlocked(feature, context) }
        }
    }

    val shown = remember(apps, blocked, query, filter) {
        val byFilter = when (filter) {
            ToggleFilter.ALL -> apps
            ToggleFilter.BLOCKED -> apps.filter { it.packageName in blocked }
            ToggleFilter.ALLOWED -> apps.filter { it.packageName !in blocked }
        }
        val trimmed = query.trim()
        val searched = if (trimmed.isBlank()) {
            byFilter
        } else {
            byFilter.filter {
                appLabel(pm, it).contains(trimmed, ignoreCase = true) ||
                    it.packageName.contains(trimmed, ignoreCase = true)
            }
        }
        searched.sortedBy { appLabel(pm, it).lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(feature.titleRes))
                    // How many are blocked, next to the name: the list's filters can say it,
                    // but the count is the thing somebody opens this screen to see.
                    Text(
                        text = stringResource(R.string.labs_blocked_count, blocked.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleFilter.entries.forEach { option ->
                val count = when (option) {
                    ToggleFilter.ALL -> apps.size
                    ToggleFilter.BLOCKED -> apps.count { it.packageName in blocked }
                    ToggleFilter.ALLOWED -> apps.count { it.packageName !in blocked }
                }
                AppFilterChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(
                        when (option) {
                            ToggleFilter.ALL -> R.string.apps_filter_all
                            ToggleFilter.ALLOWED -> R.string.labs_filter_allowed
                            ToggleFilter.BLOCKED -> R.string.labs_filter_blocked
                        }
                    ),
                    count = count,
                    selected = filter == option,
                    onClick = { filter = option }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.app_management_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 4.dp,
                    end = 16.dp,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Said here rather than in one setting somewhere: the firewall's list is this
                // app's own record, and the app's page is where the platform is asked.
                if (feature == AppToggleFeature.FIREWALL) {
                    item {
                        Text(
                            text = stringResource(R.string.labs_firewall_note),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .padding(bottom = 4.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(shown, key = { it.packageName }) { pi ->
                    val isBlocked = pi.packageName in blocked
                    SegmentedCard {
                        ListItem(
                            leadingContent = { AppIcon(pi) },
                            headlineContent = { Text(appLabel(pm, pi)) },
                            supportingContent = {
                                Text(
                                    pi.packageName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = isBlocked,
                                    enabled = feature != AppToggleFeature.AUTOSTART || running,
                                    onCheckedChange = { checked ->
                                        blocked = if (checked) blocked + pi.packageName
                                        else blocked - pi.packageName
                                        toggle(pi.packageName, checked)
                                    }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            if (loading || shown.isEmpty()) {
                if (loading) {
                    AppListSkeleton()
                } else {
                    CenteredMessage {
                        when {
                            !running && feature == AppToggleFeature.AUTOSTART -> Text(
                                text = stringResource(R.string.apps_needs_shizuku),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            query.isNotBlank() -> Text(
                                text = stringResource(R.string.apps_no_match),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            filter == ToggleFilter.BLOCKED -> Text(
                                text = stringResource(R.string.labs_blocked_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            else -> Text(
                                text = stringResource(R.string.apps_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The state this feature is about: which apps are blocked, according to its own source. */
private suspend fun readBlocked(
    feature: AppToggleFeature,
    context: android.content.Context
): Set<String> = when (feature) {
    // One command for every app in the mode.
    AppToggleFeature.AUTOSTART -> PackageTools.readOpDenied(feature.op)
    // This app's own record; the platform cannot be asked for the list.
    AppToggleFeature.FIREWALL -> PackageTools.readFirewallBlocked(context)
}
