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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.shell.ShellBackend
import moe.shizuku.manager.shell.ShellLine
import moe.shizuku.manager.shell.ShellSession
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

/** How much scrollback to keep. A command like `logcat` would otherwise grow without end. */
private const val MAX_LINES = 2000

private const val MAX_HISTORY = 100

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

    var input by rememberSaveable { mutableStateOf("") }
    var backend by rememberSaveable { mutableStateOf(ShellBackend.SHIZUKU) }
    var running by remember { mutableStateOf(false) }
    var cwd by remember { mutableStateOf(session.cwd) }
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

        input = ""
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

    fun recall(direction: Int) {
        if (history.isEmpty()) return
        if (historyIndex == -1) historyIndex = history.size
        historyIndex = (historyIndex + direction).coerceIn(0, history.size)
        input = if (historyIndex == history.size) "" else history[historyIndex]
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.shell_input_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(input) }),
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
                // Only the Shizuku backend can be cut short: a root command runs inside a
                // `su` job and there is no handle on the process it ends up in.
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
                IconButton(onClick = { submit(input) }, enabled = input.isNotBlank()) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = stringResource(R.string.shell_run)
                    )
                }
            }
        }
    }
}
