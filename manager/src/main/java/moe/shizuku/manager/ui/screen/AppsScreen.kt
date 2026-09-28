package moe.shizuku.manager.ui.screen

import android.content.pm.PackageInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.ui.component.SegmentedCard
import moe.shizuku.manager.utils.ShizukuStateMachine

enum class SortOrder { LAST_ADDED, ALPHABETICAL }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val pm = context.packageManager

    var all by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.LAST_ADDED) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var version by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var pendingBatch by remember { mutableStateOf<Boolean?>(null) }
    // Set to the state every listed app should end up in, once the user confirms.
    var pendingToggleAll by remember { mutableStateOf<Boolean?>(null) }
    var permissionLimited by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(ShizukuStateMachine.isRunning()) }

    // The list comes from the server, so it has to be re-read when that comes or goes
    // (and the answer while it is down is "there is nothing to list", not an empty page).
    DisposableEffect(Unit) {
        val listener: (ShizukuStateMachine.State) -> Unit = {
            running = it == ShizukuStateMachine.State.RUNNING
        }
        ShizukuStateMachine.addListener(listener)
        onDispose { ShizukuStateMachine.removeListener(listener) }
    }

    LaunchedEffect(running) {
        loading = true
        all = withContext(Dispatchers.IO) {
            runCatching {
                AuthorizationManager.getPackages(exclude = listOf(context.packageName))
            }.getOrDefault(emptyList())
        }
        loading = false
    }

    val shown = remember(all, query, sortOrder) {
        val q = query.trim()
        val filtered = if (q.isBlank()) {
            all
        } else {
            all.filter {
                val label = runCatching { it.applicationInfo?.loadLabel(pm)?.toString() ?: "" }.getOrDefault("")
                label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
        if (sortOrder == SortOrder.ALPHABETICAL) {
            filtered.sortedBy {
                runCatching { it.applicationInfo?.loadLabel(pm)?.toString()?.lowercase() }
                    .getOrDefault(it.packageName)
            }
        } else {
            filtered
        }
    }

    val selectionMode = selected.isNotEmpty()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_selected_count, selected.size)) },
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                navigationIcon = {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { selected = shown.map { it.packageName }.toSet() }) {
                        Text(stringResource(R.string.app_management_action_select_all))
                    }
                    TextButton(onClick = { pendingBatch = true }) {
                        Text(stringResource(R.string.app_management_action_grant))
                    }
                    TextButton(onClick = { pendingBatch = false }) {
                        Text(stringResource(R.string.app_management_action_revoke))
                    }
                }
            )
        } else {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_apps)) },
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                actions = {
                    // Long-press and Select all exist for picking individual apps; this is
                    // the one-tap version for when every listed app should be flipped.
                    if (shown.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                val allGranted = withContext(Dispatchers.IO) {
                                    shown.all {
                                        runCatching {
                                            AuthorizationManager.granted(
                                                it.packageName,
                                                it.applicationInfo!!.uid
                                            )
                                        }.getOrDefault(false)
                                    }
                                }
                                pendingToggleAll = !allGranted
                            }
                        }) { Text(stringResource(R.string.app_management_toggle_all)) }
                    }
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = null)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_management_sort_last_added)) },
                            onClick = { sortOrder = SortOrder.LAST_ADDED; sortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_management_sort_alphabetical)) },
                            onClick = { sortOrder = SortOrder.ALPHABETICAL; sortMenu = false }
                        )
                    }
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
            singleLine = true
        )

        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Every app is its own card, so they need room between them; the padding
            // keeps the cards off the edges like the cards on the other tabs.
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shown, key = { it.packageName }) { pi ->
                val uid = pi.applicationInfo!!.uid
                val granted = remember(pi.packageName, version) {
                    runCatching { AuthorizationManager.granted(pi.packageName, uid) }.getOrDefault(false)
                }
                val isSelected = pi.packageName in selected

                SegmentedCard(
                    // A selected row tints its card, so a multi-select pass reads at a
                    // glance instead of needing the checkbox to be spotted each time.
                    color = if (selectionMode && isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                ListItem(
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                selected = if (isSelected) selected - pi.packageName else selected + pi.packageName
                            } else {
                                val result = runCatching {
                                    if (granted) AuthorizationManager.revoke(pi.packageName, uid)
                                    else AuthorizationManager.grant(pi.packageName, uid)
                                }
                                if (result.exceptionOrNull() is SecurityException) {
                                    permissionLimited = true
                                }
                                version++
                            }
                        },
                        onLongClick = {
                            selected = if (isSelected) selected - pi.packageName else selected + pi.packageName
                        }
                    ),
                    leadingContent = { AppIcon(pi) },
                    headlineContent = {
                        Text(
                            runCatching { pi.applicationInfo!!.loadLabel(pm).toString() }
                                .getOrDefault(pi.packageName)
                        )
                    },
                    supportingContent = { Text(pi.packageName) },
                    trailingContent = {
                        if (selectionMode) {
                            Checkbox(checked = isSelected, onCheckedChange = null)
                        } else {
                            Switch(
                                checked = granted,
                                onCheckedChange = { checked ->
                                    val result = runCatching {
                                        if (checked) AuthorizationManager.grant(pi.packageName, uid)
                                        else AuthorizationManager.revoke(pi.packageName, uid)
                                    }
                                    if (result.exceptionOrNull() is SecurityException) {
                                        permissionLimited = true
                                    }
                                    version++
                                }
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                }
            }
        }

        // Say why the page is empty: still loading, no server to ask, nothing matching
        // the search, or genuinely no apps — a blank page explains nothing.
        if (loading || shown.isEmpty()) {
            CenteredMessage {
                when {
                    loading -> CircularProgressIndicator()

                    !running -> {
                        Text(
                            text = stringResource(R.string.apps_needs_shizuku),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            modifier = Modifier.padding(top = 12.dp),
                            onClick = {
                                ShizukuReceiverStarter.start(context, userInitiated = true)
                            }
                        ) { Text(stringResource(R.string.action_start)) }
                    }

                    query.isNotBlank() -> Text(
                        text = stringResource(R.string.apps_no_match),
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

    if (permissionLimited) {
        val adbUrl = runCatching { Helps.ADB.get() }.getOrDefault("")
        AlertDialog(
            onDismissRequest = { permissionLimited = false },
            title = { Text(stringResource(R.string.app_management_dialog_adb_is_limited_title)) },
            text = {
                Text(stringResource(R.string.app_management_dialog_adb_is_limited_message, adbUrl))
            },
            confirmButton = {
                TextButton(onClick = { permissionLimited = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    pendingToggleAll?.let { grant ->
        AlertDialog(
            onDismissRequest = { pendingToggleAll = null },
            title = {
                Text(
                    stringResource(
                        if (grant) R.string.app_management_batch_grant_title
                        else R.string.app_management_batch_revoke_title
                    )
                )
            },
            // Only what is listed: searching first is how you narrow this down.
            text = { Text(stringResource(R.string.app_management_toggle_all_message, shown.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = shown.toList()
                    pendingToggleAll = null
                    scope.launch {
                        val limited = withContext(Dispatchers.IO) {
                            var limited = false
                            for (pi in target) {
                                val result = runCatching {
                                    if (grant) AuthorizationManager.grant(pi.packageName, pi.applicationInfo!!.uid)
                                    else AuthorizationManager.revoke(pi.packageName, pi.applicationInfo!!.uid)
                                }
                                if (result.exceptionOrNull() is SecurityException) limited = true
                            }
                            limited
                        }
                        if (limited) permissionLimited = true
                        version++
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingToggleAll = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    pendingBatch?.let { grant ->
        AlertDialog(
            onDismissRequest = { pendingBatch = null },
            title = {
                Text(
                    stringResource(
                        if (grant) R.string.app_management_batch_grant_title
                        else R.string.app_management_batch_revoke_title
                    )
                )
            },
            text = { Text(stringResource(R.string.app_management_batch_message, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    for (pi in all.filter { it.packageName in selected }) {
                        runCatching {
                            if (grant) AuthorizationManager.grant(pi.packageName, pi.applicationInfo!!.uid)
                            else AuthorizationManager.revoke(pi.packageName, pi.applicationInfo!!.uid)
                        }
                    }
                    pendingBatch = null
                    selected = emptySet()
                    version++
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatch = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

/** Centred content for the states that aren't a list. */
@Composable
private fun CenteredMessage(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun AppIcon(pi: PackageInfo) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, pi.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                pi.applicationInfo!!.loadIcon(context.packageManager).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Spacer(modifier = Modifier.size(40.dp))
    }
}
