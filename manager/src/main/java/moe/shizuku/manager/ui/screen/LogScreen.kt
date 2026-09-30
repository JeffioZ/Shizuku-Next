package moe.shizuku.manager.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.component.AppFilterChip
import moe.shizuku.manager.ui.component.CenteredMessage
import moe.shizuku.manager.utils.Diag
import moe.shizuku.manager.utils.runShellCommand

/** Which tags the list is showing. */
private enum class LogFilter { ALL, PROBLEMS }

/**
 * What this app has done lately, readable on the phone.
 *
 * The watch, the watchdog and the state machine all report to [Diag] as well as to logcat, and
 * this is the window onto it: the last day of lines, searchable, filterable by tag, and saved
 * anywhere when a report needs to go somewhere.
 *
 * The shell comes along for the parts this app does not write itself: the Shizuku API and the
 * server log under their own tags, from processes this one cannot read, and the refresh action
 * pulls those in through the same shell the rest of the app uses. Lines this app already stored
 * are skipped on the way in, so a refresh cannot double the list.
 *
 * Rendered as fixed-width lines with the timestamps in a column, which is what makes a log
 * readable: the shell's transcript does the same thing for the same reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var entries by remember { mutableStateOf<List<Diag.Entry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var tag by remember { mutableStateOf<String?>(null) }
    var pulling by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(version) {
        entries = withContext(Dispatchers.IO) { Diag.entries() }
    }

    val tags = remember(entries) { Diag.tags().take(6) }
    val shown = remember(entries, query, filter, tag) {
        val byKind = when (filter) {
            LogFilter.ALL -> entries
            LogFilter.PROBLEMS -> entries.filter { it.level == 'W' || it.level == 'E' }
        }
        val byTag = tag?.let { chosen -> byKind.filter { it.tag == chosen } } ?: byKind
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            byTag
        } else {
            byTag.filter {
                it.message.contains(trimmed, ignoreCase = true) ||
                    it.tag.contains(trimmed, ignoreCase = true)
            }
        }.reversed()   // newest first
    }

    fun pull() {
        scope.launch {
            pulling = true
            val added = withContext(Dispatchers.IO) { pullLogcat() }
            pulling = false
            version++
            if (added > 0) {
                Toast.makeText(
                    context,
                    context.getString(R.string.log_pulled, added),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.tab_log))
                    Text(
                        text = stringResource(R.string.log_count, entries.size),
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
            },
            actions = {
                IconButton(onClick = { pull() }, enabled = !pulling) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.log_refresh))
                }
                IconButton(
                    onClick = {
                        val file = Diag.export()
                        Toast.makeText(
                            context,
                            if (file == null) {
                                context.getString(R.string.log_nothing_to_save)
                            } else {
                                context.getString(R.string.log_saved, file.name)
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(R.string.log_save))
                }
                IconButton(
                    onClick = {
                        Diag.clear()
                        version++
                        Toast.makeText(
                            context,
                            context.getString(R.string.log_cleared),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.log_clear))
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppFilterChip(
                label = stringResource(R.string.log_filter_all),
                count = entries.size,
                selected = filter == LogFilter.ALL && tag == null,
                fill = false,
                onClick = { filter = LogFilter.ALL; tag = null }
            )
            AppFilterChip(
                label = stringResource(R.string.log_filter_problems),
                count = entries.count { it.level == 'W' || it.level == 'E' },
                selected = filter == LogFilter.PROBLEMS,
                fill = false,
                onClick = {
                    filter = if (filter == LogFilter.PROBLEMS) LogFilter.ALL else LogFilter.PROBLEMS
                }
            )
            tags.forEach { name ->
                AppFilterChip(
                    label = name,
                    count = entries.count { it.tag == name },
                    selected = tag == name,
                    // Tapping the chosen one again lets go of it, the same as the other lists.
                    fill = false,
                    onClick = { tag = if (tag == name) null else name }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.log_search_hint)) },
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
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 4.dp,
                    end = 16.dp,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(shown, key = { "${it.at}-${it.tag}-${it.message.hashCode()}" }) { entry ->
                    LogLine(entry, query.trim())
                }
            }

            if (shown.isEmpty()) {
                CenteredMessage {
                    Text(
                        text = stringResource(
                            if (query.isNotBlank() || tag != null) R.string.apps_no_match
                            else R.string.log_empty
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * One line, the way the shell's transcript draws them: fixed width, the time in its own column,
 * and the search term marked where it was found.
 */
@Composable
private fun LogLine(entry: Diag.Entry, query: String) {
    val time = Diag.stamp(entry.at)
    val colour = when (entry.level) {
        'E' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.tertiary
        'D' -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface2(colour) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append("$time  ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(entry.tag) }
                append("  ")
                if (query.isBlank()) {
                    append(entry.message)
                } else {
                    appendMarked(entry.message, query)
                }
            },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colour
        )
    }
}

/** The message with every occurrence of [query] picked out. */
private fun AnnotatedString.Builder.appendMarked(message: String, query: String) {
    var index = 0
    while (true) {
        val at = message.indexOf(query, index, ignoreCase = true)
        if (at < 0) {
            append(message.substring(index))
            return
        }
        append(message.substring(index, at))
        withStyle(SpanStyle(background = Color(0x66FFB74D))) {
            append(message.substring(at, at + query.length))
        }
        index = at + query.length
    }
}

/** A tinted, rounded line, so a warning or an error is visible while scrolling past it. */
@Composable
private fun Surface2(colour: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        content()
    }
}

/**
 * Reads logcat through the shell and keeps what this app has not already stored.
 *
 * The API and the server are other processes, so their lines only exist in logcat; the tags here
 * are the ones Shizuku writes under, and the lines this app writes itself are skipped because
 * [Diag] already has them - which is what makes a second refresh add nothing rather than
 * everything twice.
 *
 * Runs shell commands; not for the main thread.
 */
private fun pullLogcat(): Int {
    val tags = listOf(
        "Shizuku", "ShizukuServer", "ShizukuStarter", "ShizukuApi", "ShizukuManager",
        "ShizukuWatchdog", "ShizukuStateMachine", "ShizukuApplication"
    )
    val filter = tags.joinToString(" ") { "$it:V" }
    val out = runShellCommand("logcat -d -v epoch -t 600 $filter '*:S' 2>/dev/null") ?: return 0

    var added = 0
    out.lineSequence().forEach { line ->
        val match = LOGCAT.find(line) ?: return@forEach
        val tag = match.groupValues[3].trim()
        // Ours are already stored, with the tag this app uses for them.
        if (OWN_TAGS.any { tag.startsWith(it) }) return@forEach
        val at = match.groupValues[1].toDoubleOrNull()?.times(1000)?.toLong() ?: return@forEach
        Diag.log(match.groupValues[2].firstOrNull() ?: 'I', tag, match.groupValues[4].trim())
        added++
    }
    return added
}

/**
 * `-v epoch`: `"         1790809925.077 11885 11885 I ShizukuApi: message"`.
 *
 * The timestamp is padded to a fixed width, so the line starts with spaces - the first version
 * of this anchored on a digit and matched nothing at all, which looked exactly like there being
 * nothing to pull.
 */
private val LOGCAT = Regex("""^\s*(\d+\.\d+)\s+\d+\s+\d+\s+([VDIWEF])\s+(.+?):\s?(.*)$""")

private val OWN_TAGS = listOf(AppConstants.TAG, "ShizukuWatchdog", "ShizukuStateMachine")
