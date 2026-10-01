package moe.shizuku.manager.ui.screen

import android.content.pm.PackageInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.manage.Activities
import moe.shizuku.manager.ui.component.AppIcon
import moe.shizuku.manager.ui.component.CenteredMessage
import moe.shizuku.manager.ui.component.ChipEmphasis
import moe.shizuku.manager.ui.component.IntentDraft
import moe.shizuku.manager.ui.component.IntentForm
import moe.shizuku.manager.ui.component.SegmentedCard
import moe.shizuku.manager.ui.component.StatusChip
import moe.shizuku.manager.ui.component.appLabel
import rikka.core.util.ClipboardUtils

/**
 * Two lists: the apps, then the activities one of them declares.
 *
 * A level rather than a screen of its own, because the second list means nothing without the app
 * it belongs to, and going back should land on the picker rather than on the grid.
 *
 * The chips are the whole point of the second list. "Not exported" is the interesting case and the
 * reason this screen exists: it is an activity the app never meant anybody else to open, and it is
 * the one that cannot be started the ordinary way.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActivitiesScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var activities by remember { mutableStateOf<List<Activities.Activity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activitiesLoading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    // The activity whose whole name is being shown. A row has one line for a package and a class,
    // which is not enough for either of them, and the name is the thing needed to find the
    // activity anywhere else - in a manifest, in another log, in a bug report.
    var details by remember { mutableStateOf<Activities.Activity?>(null) }
    var version by remember { mutableIntStateOf(0) }

    // Which half of this screen: what apps declare, or an intent written by hand. Saved, because
    // it is a place somebody chose to be rather than a step in a flow.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val draft = remember { IntentDraft() }

    LaunchedEffect(version) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            runCatching { pm.getInstalledPackages(android.content.pm.PackageManager.GET_ACTIVITIES) }
                .getOrDefault(emptyList())
                // An app with no activities has nothing to show here, and there are a few.
                .filter { !it.activities.isNullOrEmpty() }
        }
        loading = false
    }

    LaunchedEffect(chosen) {
        val packageName = chosen ?: return@LaunchedEffect
        activitiesLoading = true
        message = null
        activities = withContext(Dispatchers.IO) { Activities.of(pm, packageName) }
        activitiesLoading = false
    }

    // Back leaves the activities before it leaves the screen, which is the order the two lists
    // were walked in. On the form there is no level to leave, so the screen is what back closes.
    BackHandler(enabled = tab == 0 && chosen != null) {
        chosen = null
        query = ""
        message = null
    }

    fun copy(text: String) {
        if (ClipboardUtils.put(context, text)) {
            Toast.makeText(context, context.getString(R.string.activities_copied), Toast.LENGTH_SHORT)
                .show()
        }
    }

    /** Sends what the form holds, and says what happened when it is worth saying. */
    fun send() {
        if (!draft.sendable()) {
            draft.message = R.string.intent_nothing
            return
        }
        val intent = draft.build()
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { Activities.launch(context, intent) }
            draft.message = when (outcome) {
                Activities.Outcome.STARTED, Activities.Outcome.ELEVATED -> null
                Activities.Outcome.NO_SHELL -> R.string.activities_needs_shizuku
                Activities.Outcome.REFUSED -> R.string.intent_refused
            }
        }
    }

    fun start(activity: Activities.Activity) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { Activities.launch(context, activity) }
            message = when (outcome) {
                Activities.Outcome.STARTED, Activities.Outcome.ELEVATED -> null
                Activities.Outcome.NO_SHELL -> R.string.activities_needs_shizuku
                Activities.Outcome.REFUSED -> R.string.activities_refused
            }
        }
    }

    val shownApps = remember(apps, query) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isBlank()) {
            apps
        } else {
            apps.filter {
                appLabel(pm, it).contains(trimmed, ignoreCase = true) ||
                    it.packageName.contains(trimmed, ignoreCase = true)
            }
        }
        filtered.sortedBy { appLabel(pm, it).lowercase() }
    }

    val shownActivities = remember(activities, query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            activities
        } else {
            activities.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.name.contains(trimmed, ignoreCase = true)
            }
        }
    }

    val packageName = chosen
    val appLabelText = remember(packageName, apps) {
        packageName?.let { name -> apps.firstOrNull { it.packageName == name } }
            ?.let { appLabel(pm, it) } ?: packageName.orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        if (tab == 1) {
                            stringResource(R.string.tab_intent_builder)
                        } else if (packageName == null) {
                            stringResource(R.string.tab_activities)
                        } else {
                            appLabelText
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // The count belongs to the list; the form has nothing to count.
                    if (tab == 0) {
                        Text(
                            text = if (packageName == null) {
                                stringResource(R.string.activities_apps_count, apps.size)
                            } else {
                                stringResource(R.string.activities_count, activities.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (tab == 1 || packageName == null) {
                            onBack()
                        } else {
                            chosen = null
                            query = ""
                            message = null
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                // Only where there is something to send.
                if (tab == 1) {
                    IconButton(onClick = { send() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.intent_start)
                        )
                    }
                }
            }
        )

        // Two halves of one question, so one screen with two tabs rather than two doors on the
        // grid: what this app can open, and what to open.
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.tab_activities)) }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.tab_intent_builder)) }
            )
        }

        if (tab == 1) {
            IntentForm(draft = draft, bottomPadding = bottomPadding)
            return@Column
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
            if (packageName == null) {
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
                    items(shownApps, key = { it.packageName }) { pi ->
                        SegmentedCard {
                            ListItem(
                                modifier = Modifier.clickable {
                                    query = ""
                                    message = null
                                    chosen = pi.packageName
                                },
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
                                        StatusChip(
                                            text = (pi.activities?.size ?: 0).toString()
                                        )
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            } else {
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
                    message?.let { text ->
                        item { Note(stringResource(text), error = true) }
                    }

                    item { Note(stringResource(R.string.activities_long_press)) }

                    items(shownActivities, key = { it.name }) { activity ->
                        SegmentedCard {
                            ListItem(
                                modifier = Modifier.combinedClickable(
                                    onClick = { start(activity) },
                                    onLongClick = { details = activity }
                                ),
                                headlineContent = { Text(activity.title) },
                                supportingContent = {
                                    Text(
                                        activity.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    StatusChip(
                                        text = stringResource(
                                            when {
                                                activity.launcher -> R.string.activities_launcher
                                                activity.exported -> R.string.activities_exported
                                                else -> R.string.activities_not_exported
                                            }
                                        ),
                                        emphasis = if (activity.launcher || !activity.exported) {
                                            ChipEmphasis.SOFT
                                        } else {
                                            ChipEmphasis.NONE
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

            val busy = if (packageName == null) loading else activitiesLoading
            if (busy || (if (packageName == null) shownApps.isEmpty() else shownActivities.isEmpty())) {
                if (busy) {
                    CenteredMessage { LoadingIndicator() }
                } else {
                    CenteredMessage {
                        when {
                            query.isNotBlank() -> Text(
                                text = stringResource(R.string.apps_no_match),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            packageName == null -> Text(
                                text = stringResource(R.string.apps_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            else -> Text(
                                text = stringResource(R.string.activities_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    details?.let { activity ->
        ActivityDetailsDialog(
            activity = activity,
            onOpen = {
                details = null
                start(activity)
            },
            onCopy = { copy(activity.name) },
            onDismiss = { details = null }
        )
    }
}

/**
 * The whole of one activity: the names that do not fit on a row, and the two things worth doing
 * with them.
 *
 * The class name is the point of it. A row can only ever show the front of a package name, and the
 * full one is what identifies the activity - it is what goes in `am start -n`, what a manifest is
 * searched for, and what somebody pasting this into a bug report needs.
 */
@Composable
private fun ActivityDetailsDialog(
    activity: Activities.Activity,
    onOpen: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(activity.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailLine(stringResource(R.string.activities_detail_package), activity.packageName)
                DetailLine(stringResource(R.string.activities_detail_activity), activity.name)
                DetailLine(
                    stringResource(R.string.activities_detail_state),
                    stringResource(
                        when {
                            activity.launcher -> R.string.activities_launcher
                            activity.exported -> R.string.activities_exported
                            else -> R.string.activities_not_exported
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.activities_open)) }
        },
        dismissButton = {
            TextButton(onClick = { onCopy(activity.name) }) {
                Text(stringResource(R.string.activities_copy))
            }
        }
    )
}

/** One line of the dialog: what the value is, then the value, whole and wrapping. */
@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Note(text: String, error: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .padding(bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}
