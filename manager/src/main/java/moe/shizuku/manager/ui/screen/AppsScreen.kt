package moe.shizuku.manager.ui.screen

import android.content.pm.PackageInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.authorization.AuthorizationManager

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
    var permissionLimited by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) {
            runCatching {
                AuthorizationManager.getPackages(exclude = listOf(context.packageName))
            }.getOrDefault(emptyList())
        }
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_selected_count, selected.size)) },
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
                actions = {
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

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.packageName }) { pi ->
                val uid = pi.applicationInfo!!.uid
                val granted = remember(pi.packageName, version) {
                    runCatching { AuthorizationManager.granted(pi.packageName, uid) }.getOrDefault(false)
                }
                val isSelected = pi.packageName in selected

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
                    }
                )
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
