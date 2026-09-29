package moe.shizuku.manager.ui.screen

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.component.AppFilterChip
import moe.shizuku.manager.ui.component.AppIcon
import moe.shizuku.manager.ui.component.CenteredMessage
import moe.shizuku.manager.ui.component.ChipEmphasis
import moe.shizuku.manager.ui.component.SegmentedCard
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.ui.component.StatusChip

/** Which slice of the installed apps to list. */
enum class ManageFilter {
    ALL,

    /** Apps the user brought, which are the safe ones to change. */
    USER,

    /** Apps that came with the system image, where changing anything is riskier. */
    SYSTEM,

    /**
     * Apps that are installed but not running: disabled with the package manager, or
     * suspended. Both mean the same thing to whoever is looking for them here.
     */
    DISABLED,

    /**
     * Apps with no launcher entry, so they never appear in the app drawer — services,
     * system pieces and background components. The same meaning the Apps tab gives it.
     */
    HIDDEN
}

/**
 * The Manage tab: every installed app, and behind each one the things that are normally
 * only reachable from a computer — system permissions, app ops, run state and battery
 * policy.
 *
 * Unlike the Apps tab this list is read straight from the local package manager, so it
 * works with Shizuku stopped; only the rows that change something need the server, and
 * they say so instead of failing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val pm = context.packageManager

    var apps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.ALPHABETICAL) }
    var sortMenu by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ManageFilter.ALL) }
    // The open app is remembered across rotation, like the tab itself: coming back should
    // not drop you out of the app you were reading.
    var openPackage by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped whenever something changed, to re-read the list (a removed app belongs under
    // a different filter afterwards).
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(version) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES) }
                .getOrDefault(emptyList())
        }
        loading = false
    }

    // An app that is no longer installed keeps its row but has no application record, so it
    // belongs to neither half — it stays reachable through All, where its row says so.
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
    val removedPackages = remember(apps) {
        apps.filter { it.applicationInfo == null }.map { it.packageName }.toSet()
    }

    // Worked out off the main thread, and once per loaded list: it is a package manager
    // question per app, and the chip needs its size before anyone picks it.
    var launcherless by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(apps) {
        if (apps.isEmpty()) {
            launcherless = emptySet()
            return@LaunchedEffect
        }
        launcherless = withContext(Dispatchers.IO) {
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

    val shown = remember(
        apps, query, sortOrder, filter,
        systemPackages, userPackages, disabledPackages, launcherless
    ) {
        val byFilter = when (filter) {
            ManageFilter.ALL -> apps
            ManageFilter.USER -> apps.filter { it.packageName in userPackages }
            ManageFilter.SYSTEM -> apps.filter { it.packageName in systemPackages }
            ManageFilter.DISABLED -> apps.filter { it.packageName in disabledPackages }
            ManageFilter.HIDDEN -> apps.filter { it.packageName in launcherless }
        }
        val trimmed = query.trim()
        val filtered = if (trimmed.isBlank()) {
            byFilter
        } else {
            byFilter.filter {
                labelOf(pm, it).contains(trimmed, ignoreCase = true) ||
                    it.packageName.contains(trimmed, ignoreCase = true)
            }
        }
        when (sortOrder) {
            SortOrder.RECENTLY_INSTALLED -> filtered.sortedByDescending { it.firstInstallTime }
            SortOrder.RECENTLY_UPDATED -> filtered.sortedByDescending { it.lastUpdateTime }
            SortOrder.ALPHABETICAL -> filtered.sortedBy { labelOf(pm, it).lowercase() }
        }
    }

    val open = openPackage
    if (open != null) {
        BackHandler { openPackage = null }
        AppDetailScreen(
            packageName = open,
            bottomPadding = bottomPadding,
            onBack = { openPackage = null },
            onChanged = { version++ }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_manage)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            actions = {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = null)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.app_management_sort_alphabetical)) },
                        onClick = { sortOrder = SortOrder.ALPHABETICAL; sortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.app_management_sort_last_added)) },
                        onClick = { sortOrder = SortOrder.RECENTLY_INSTALLED; sortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.app_management_sort_updated)) },
                        onClick = { sortOrder = SortOrder.RECENTLY_UPDATED; sortMenu = false }
                    )
                }
            }
        )

        // Five filters no longer fit as one row of equal-width chips on a phone, and
        // shrinking the labels to make them fit is how a label ends up cut in half — so
        // this row scrolls instead, and each chip takes the width its own text needs.
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
                    selected = filter == option,
                    fill = false,
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
            placeholder = { Text(stringResource(R.string.manage_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(android.R.string.cancel))
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
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.packageName }) { pi ->
                    val removed = pi.packageName in removedPackages
                    val flags = pi.applicationInfo?.flags ?: 0
                    val suspended = flags and ApplicationInfo.FLAG_SUSPENDED != 0
                    val disabled = pi.applicationInfo?.enabled == false
                    val status = when {
                        removed -> stringResource(R.string.manage_status_removed)
                        suspended -> stringResource(R.string.manage_status_suspended)
                        disabled -> stringResource(R.string.manage_status_disabled)
                        flags and ApplicationInfo.FLAG_SYSTEM != 0 -> stringResource(R.string.manage_status_system)
                        else -> null
                    }

                    SegmentedCard {
                        SegmentedListItem(
                            modifier = Modifier.clickable { openPackage = pi.packageName },
                            leadingContent = {
                                // A removed app has no icon to load — the row keeps the
                                // space so the labels stay aligned with the rest.
                                if (removed) Box(modifier = Modifier.padding(20.dp)) else AppIcon(pi)
                            },
                            headlineContent = { Text(labelOf(pm, pi)) },
                            // The package name is the whole line, and it ellipsises rather than
                            // being cut, because nothing follows it any more.
                            supportingContent = {
                                Text(pi.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (status != null) {
                                        StatusChip(
                                            status,
                                            emphasis = when {
                                                removed -> ChipEmphasis.WARN
                                                disabled || suspended -> ChipEmphasis.SOFT
                                                else -> ChipEmphasis.NONE
                                            }
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            // Centred, so the chip and the arrow sit against the row rather than
                            // level with its first line, and the row's own padding keeps them off
                            // the card's edge.
                            centerTrailing = true
                        )
                    }
                }
            }

            if (loading || shown.isEmpty()) {
                CenteredMessage {
                    when {
                        loading -> CircularProgressIndicator()

                        query.isNotBlank() -> Text(
                            text = stringResource(R.string.apps_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        filter != ManageFilter.ALL -> Text(
                            text = stringResource(R.string.manage_filter_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        else -> Text(
                            text = stringResource(R.string.manage_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** The label, or the package name when there is no application to ask. */
internal fun labelOf(pm: PackageManager, pi: PackageInfo): String =
    runCatching { pi.applicationInfo?.loadLabel(pm)?.toString() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: pi.packageName
