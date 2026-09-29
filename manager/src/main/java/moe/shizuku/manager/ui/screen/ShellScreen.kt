package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.shell.ShellBackend
import moe.shizuku.manager.ui.component.AppIcon
import moe.shizuku.manager.ui.component.appLabel
import moe.shizuku.manager.shell.ShellLine
import moe.shizuku.manager.shell.ShellSession
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

/** How much scrollback to keep. A command like `logcat` would otherwise grow without end. */
private const val MAX_LINES = 2000

private const val MAX_HISTORY = 100

/**
 * One of the commands the shell offers, which are the ones the manager already runs itself.
 *
 * [insertOnly] is the split that matters: something that only reads the device can be run by
 * a tap, and something that changes it cannot — `pm grant` needs a package and a permission,
 * and a chip that fired it half-written would be a trap. Those write their command into the
 * input instead, so what runs is what you can read.
 */
private data class QuickCommand(
    @StringRes val label: Int,
    val command: String,
    val insertOnly: Boolean = false,
    /**
     * Whether a package alone finishes the command. Nothing here is complete on a package
     * except force stop: the rest take an argument after it, and the space left behind is
     * where that argument goes.
     */
    val needsArgument: Boolean = true
)

private val QUICK = listOf(
    QuickCommand(R.string.shell_quick_battery, "dumpsys battery"),
    QuickCommand(R.string.shell_quick_storage, "df -h /data /sdcard"),
    QuickCommand(R.string.shell_quick_device, "getprop ro.product.model; getprop ro.build.version.release"),
    QuickCommand(R.string.shell_quick_apps, "pm list packages -3 | sort"),
    QuickCommand(R.string.shell_quick_grant, "pm grant ", insertOnly = true),
    QuickCommand(R.string.shell_quick_revoke, "pm revoke ", insertOnly = true),
    QuickCommand(R.string.shell_quick_app_ops, "cmd appops set ", insertOnly = true),
    QuickCommand(R.string.shell_quick_force_stop, "am force-stop ", insertOnly = true, needsArgument = false)
)

/**
 * The shell, in the app rather than in a terminal app.
 *
 * Every command is its own process — see [ShellSession] for why a real tty is not on offer
 * here — with the working directory and anything exported to `export` carried from one to the
 * next, so it reads like a session even though nothing outlives a command. Two backends, and
 * the same screen for both: through Shizuku (whatever uid the server runs as: 2000 over adb,
 * 0 with root, 1000 with the exploit) or through `su`, which works with Shizuku stopped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val session = remember { ShellSession() }
    val listState = rememberLazyListState()

    val lines = remember { mutableStateListOf<ShellLine>() }
    // The command runs off the main thread and writes here; the UI drains it on the main
    // one, which is why streaming output does not need a recomposition per line.
    val incoming = remember { Channel<ShellLine>(Channel.UNLIMITED) }

    // A TextFieldValue rather than a plain String, for one reason: a chip that fills the
    // input has to leave the caret at the end of what it wrote. With a String the caret
    // stayed where it was — at the start of an empty field — so the rest of the command was
    // typed in front of the template ("com.foo pm grant").
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var backend by rememberSaveable { mutableStateOf(ShellBackend.SHIZUKU) }
    var running by remember { mutableStateOf(false) }
    var cwd by remember { mutableStateOf(session.cwd) }
    // A chip that fills the input leaves the cursor in it, so the command can be finished
    // without reaching for the field again.
    val focus = remember { FocusRequester() }
    // A chip that needs an app to act on asks for one, rather than handing over a template
    // with a hole where the package goes.
    var pickFor by remember { mutableStateOf<QuickCommand?>(null) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var uid by remember { mutableIntStateOf(-1) }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    val shizukuRunning = ShizukuStateMachine.isRunning()

    fun feed(line: ShellLine) {
        incoming.trySend(line)
    }

    LaunchedEffect(Unit) {
        // Probing root spawns a shell, so it happens once, off the main thread.
        rootAvailable = withContext(Dispatchers.IO) {
            runCatching { EnvironmentUtils.isRooted() }.getOrDefault(false)
        }
        uid = withContext(Dispatchers.IO) {
            runCatching { if (Shizuku.pingBinder()) Shizuku.getUid() else -1 }.getOrDefault(-1)
        }
        feed(ShellLine(context.getString(R.string.shell_intro), ShellLine.Kind.INFO))
        // Where it opens is the shell's call: shared storage if the device has it, the tmp
        // directory otherwise. Either way it is only a starting point.
        val opened = withContext(Dispatchers.IO) {
            runCatching { session.openInPreferredDirectory(::feed) }.getOrDefault(session.cwd)
        }
        cwd = opened
    }

    LaunchedEffect(Unit) {
        for (line in incoming) {
            val atBottom = !listState.canScrollForward
            lines.add(line)
            if (lines.size > MAX_LINES) lines.removeRange(0, lines.size - MAX_LINES)
            // Follow the output unless the reader has scrolled back to read something.
            if (atBottom) listState.scrollToItem(lines.lastIndex)
        }
    }

    /**
     * Runs one line of input, or handles the two commands that are this side's business:
     * `cd`, which the shell resolves for us, and `export`, which has nothing to live in.
     */
    fun submit(raw: String) {
        val command = raw.trim()
        if (command.isEmpty() || running) return

        field = TextFieldValue("")
        feed(ShellLine("$cwd $ $command", ShellLine.Kind.COMMAND))
        if (history.isEmpty() || history.last() != command) {
            history.add(command)
            if (history.size > MAX_HISTORY) history.removeAt(0)
        }
        historyIndex = -1

        if (session.isPlainCd(command)) {
            val target = session.cdTarget(command)
            running = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { session.cd(target, ::feed) }.getOrDefault(false)
                }
                if (!ok) feed(ShellLine("cd: no such directory: $target", ShellLine.Kind.ERROR))
                cwd = session.cwd
                running = false
            }
            return
        }

        if (session.export(command)) {
            feed(ShellLine("exported ${command.trim().removePrefix("export").trim()}", ShellLine.Kind.INFO))
            return
        }

        // Running it through Shizuku after the user asked for root would answer with the
        // wrong uid and no hint that it did, so say it instead.
        if (backend == ShellBackend.ROOT && rootAvailable != true) {
            feed(ShellLine(context.getString(R.string.shell_root_refused), ShellLine.Kind.ERROR))
            return
        }
        val targetBackend = backend

        running = true
        scope.launch {
            val code = withContext(Dispatchers.IO) {
                runCatching { session.run(targetBackend, command, ::feed) }.getOrDefault(-1)
            }
            if (code != 0) {
                feed(ShellLine("exit $code", ShellLine.Kind.EXIT))
            }
            running = false
        }
    }

    /**
     * Fills the input from a chip and a picked package: the template, the package, and the
     * space the next argument goes in, with the caret after it.
     */
    fun fillFromChip(quick: QuickCommand, packageName: String) {
        val filled = buildString {
            append(quick.command)
            append(packageName)
            if (quick.needsArgument) append(' ')
        }
        field = TextFieldValue(filled, TextRange(filled.length))
        focus.requestFocus()
    }

    fun recall(direction: Int) {
        if (history.isEmpty()) return
        if (historyIndex == -1) historyIndex = history.size
        historyIndex = (historyIndex + direction).coerceIn(0, history.size)
        val recalled = if (historyIndex == history.size) "" else history[historyIndex]
        field = TextFieldValue(recalled, TextRange(recalled.length))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The keyboard covers the input otherwise, and a shell without its input line is
            // just a log.
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.shell_title))
                    Text(
                        text = when {
                            backend == ShellBackend.ROOT ->
                                stringResource(R.string.shell_backend_root_status)
                            uid >= 0 -> stringResource(R.string.shell_backend_shizuku_status, uid)
                            else -> stringResource(R.string.shell_backend_offline)
                        },
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
                if (lines.isNotEmpty()) {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(lines.joinToString("\n") { it.text }))
                    }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.shell_copy)
                        )
                    }
                    IconButton(onClick = { lines.clear() }) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.shell_clear)
                        )
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = backend == ShellBackend.SHIZUKU,
                onClick = { backend = ShellBackend.SHIZUKU },
                enabled = shizukuRunning,
                label = { Text(stringResource(R.string.shell_backend_shizuku)) }
            )
            // Never disabled: a chip that cannot be pressed is also a chip that cannot ask
            // for root, and asking is what makes the root manager offer the grant.
            FilterChip(
                selected = backend == ShellBackend.ROOT,
                onClick = {
                    backend = ShellBackend.ROOT
                    if (rootAvailable != true) {
                        scope.launch {
                            val granted = withContext(Dispatchers.IO) {
                                runCatching { EnvironmentUtils.isRooted() }.getOrDefault(false)
                            }
                            rootAvailable = granted
                            if (granted) {
                                feed(ShellLine(context.getString(R.string.shell_root_granted), ShellLine.Kind.INFO))
                            } else {
                                feed(ShellLine(context.getString(R.string.shell_root_refused), ShellLine.Kind.ERROR))
                                backend = ShellBackend.SHIZUKU
                            }
                        }
                    }
                },
                label = { Text(stringResource(R.string.shell_backend_root)) }
            )
            Text(
                // The tail is the part that says where you are, so a long path keeps its
                // end and loses its beginning rather than the other way round.
                text = if (cwd.length > 24) "…" + cwd.takeLast(23) else cwd,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }

        if (!shizukuRunning && rootAvailable != true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(R.string.shell_needs_a_backend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (line.kind) {
                        ShellLine.Kind.COMMAND -> MaterialTheme.colorScheme.primary
                        ShellLine.Kind.ERROR -> MaterialTheme.colorScheme.error
                        ShellLine.Kind.EXIT -> MaterialTheme.colorScheme.error
                        ShellLine.Kind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        ShellLine.Kind.OUTPUT -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(QUICK) { quick ->
                AssistChip(
                    onClick = {
                        if (quick.insertOnly) {
                            pickFor = quick
                        } else {
                            submit(quick.command)
                        }
                    },
                    enabled = !running,
                    label = { Text(stringResource(quick.label)) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.shell_input_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(field.text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            IconButton(onClick = { recall(-1) }, enabled = history.isNotEmpty()) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.shell_history_previous)
                )
            }
            IconButton(onClick = { recall(1) }, enabled = history.isNotEmpty()) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.shell_history_next)
                )
            }
            if (running) {
                // Only the Shizuku backend can be cut short: a root command runs inside the
                // root shell's job and there is no handle on the process it ends up in.
                IconButton(
                    onClick = { session.stop() },
                    enabled = backend == ShellBackend.SHIZUKU
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.shell_stop)
                    )
                }
            } else {
                IconButton(onClick = { submit(field.text) }, enabled = field.text.isNotBlank()) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = stringResource(R.string.shell_run)
                    )
                }
            }
        }
    }

    pickFor?.let { quick ->
        PackagePickerDialog(
            title = stringResource(quick.label),
            onDismiss = { pickFor = null }
        ) { packageName ->
            pickFor = null
            fillFromChip(quick, packageName)
        }
    }
}

/**
 * The app a chip is about to act on.
 *
 * Read straight from the local package manager, so it needs no server and lists what is
 * installed rather than what has asked Shizuku for anything. Search is by label or package
 * name, which is why it sits above a list of every app on the device.
 */
@Composable
private fun PackagePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }.getOrDefault(emptyList()).sortedBy { appLabel(pm, it).lowercase() }
        }
        loading = false
    }

    val shown = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                appLabel(pm, it).contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.manage_search_hint)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    shown.isEmpty() -> Text(
                        stringResource(R.string.apps_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(shown, key = { it.packageName }) { pi ->
                            ListItem(
                                modifier = Modifier.clickable { onPick(pi.packageName) },
                                leadingContent = { AppIcon(pi) },
                                headlineContent = { Text(appLabel(pm, pi)) },
                                supportingContent = {
                                    Text(
                                        pi.packageName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        },
        // Picking an app is the whole answer, so there is nothing left to confirm.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
