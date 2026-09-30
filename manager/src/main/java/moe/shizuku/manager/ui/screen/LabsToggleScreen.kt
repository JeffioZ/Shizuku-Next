package moe.shizuku.manager.ui.screen

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
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
import moe.shizuku.manager.ui.component.AppStatusChips
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

/**
 * Whether the app is blocked. Its two named states are the only two there are, so the row holds
 * exactly those two and neither being chosen means both: unlike the kind of app, which has a
 * list to choose from, this has nothing else to offer.
 */
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
    // The other question the list answers, and the one the app-ops list already asked: what
    // kind of app it is, so a system package can be told from something you installed.
    var kind by remember { mutableStateOf(ManageFilter.ALL) }
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

    // What "hidden" means here as well: installed, and never in the app drawer. Asked of the
    // package manager once per app, off the main thread, which is why it is worked out once per
    // read rather than per keystroke.
    val systemPackages = remember(apps) {
        apps.filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { it.packageName }.toSet()
    }
    val userPackages = remember(apps, systemPackages) {
        apps.filter { it.applicationInfo != null && it.packageName !in systemPackages }
            .map { it.packageName }.toSet()
    }
    val disabledPackages = remember(apps) {
        apps.filter {
            val ai = it.applicationInfo ?: return@filter false
            !ai.enabled || (ai.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        }.map { it.packageName }.toSet()
    }
    var launcherless by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(apps) {
        launcherless = if (apps.isEmpty()) {
            emptySet()
        } else {
            withContext(Dispatchers.IO) {
                apps.filterNot { pi ->
                    runCatching {
                        pm.getLaunchIntentForPackage(pi.packageName) != null ||
                            pm.queryIntentActivities(
                                Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                                    .setPackage(pi.packageName),
                                0
                            ).isNotEmpty()
                    }.getOrDefault(true)
                }.map { it.packageName }.toSet()
            }
        }
    }

    val shown = remember(apps, blocked, launcherless, systemPackages, userPackages, disabledPackages, query, filter, kind) {
        val byKind = when (kind) {
            ManageFilter.ALL -> apps
            ManageFilter.USER -> apps.filter { it.packageName in userPackages }
            ManageFilter.SYSTEM -> apps.filter { it.packageName in systemPackages }
            ManageFilter.DISABLED -> apps.filter { it.packageName in disabledPackages }
            ManageFilter.HIDDEN -> apps.filter { it.packageName in launcherless }
        }
        val byState = when (filter) {
            ToggleFilter.ALL -> byKind
            ToggleFilter.BLOCKED -> byKind.filter { it.packageName in blocked }
            ToggleFilter.ALLOWED -> byKind.filter { it.packageName !in blocked }
        }
        val trimmed = query.trim()
        val searched = if (trimmed.isBlank()) {
            byState
        } else {
            byState.filter {
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

        // Two rows, because they answer two questions and one row cannot say which is which:
        // what kind of app this is, and whether it is blocked. Five kind labels do not fit on a
        // phone, so that row scrolls like the app-ops list's does; the state row is two chips
        // and fits, and leaves room for the next kind to be added without moving anything.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ManageFilter.entries.forEach { option ->
                val count = when (option) {
                    ManageFilter.ALL -> apps.size
                    ManageFilter.USER -> userPackages.size
                    ManageFilter.SYSTEM -> systemPackages.size
                    ManageFilter.DISABLED -> disabledPackages.size
                    ManageFilter.HIDDEN -> launcherless.size
                }
                AppFilterChip(
                    label = stringResource(
                        when (option) {
                            ManageFilter.ALL -> R.string.manage_filter_all
                            ManageFilter.USER -> R.string.manage_filter_user
                            ManageFilter.SYSTEM -> R.string.manage_filter_system
                            ManageFilter.DISABLED -> R.string.manage_filter_disabled
                            ManageFilter.HIDDEN -> R.string.manage_filter_hidden
                        }
                    ),
                    count = count,
                    selected = kind == option,
                    // Hugging its label rather than filling a slot: a row that scrolls sizes
                    // each chip to its own text.
                    fill = false,
                    onClick = { kind = option }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppFilterChip(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.labs_filter_allowed),
                count = apps.count { it.packageName !in blocked },
                selected = filter == ToggleFilter.ALLOWED,
                // Tapping the chosen one again lets go of it: with no filter the list shows
                // both, which is what opening the screen should do.
                onClick = {
                    filter = if (filter == ToggleFilter.ALLOWED) ToggleFilter.ALL else ToggleFilter.ALLOWED
                }
            )
            AppFilterChip(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.labs_filter_blocked),
                count = apps.count { it.packageName in blocked },
                selected = filter == ToggleFilter.BLOCKED,
                onClick = {
                    filter = if (filter == ToggleFilter.BLOCKED) ToggleFilter.ALL else ToggleFilter.BLOCKED
                }
            )
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // The chips the other app lists carry, so an app says the
                                    // same things about itself wherever it is listed.
                                    AppStatusChips(pi, hidden = pi.packageName in launcherless)
                                    ExpressiveSwitch(
                                        checked = isBlocked,
                                        enabled = feature != AppToggleFeature.AUTOSTART || running,
                                        onCheckedChange = { checked ->
                                            blocked = if (checked) blocked + pi.packageName
                                            else blocked - pi.packageName
                                            toggle(pi.packageName, checked)
                                        }
                                    )
                                }
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

                            kind != ManageFilter.ALL -> Text(
                                text = stringResource(R.string.apps_filter_empty),
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
