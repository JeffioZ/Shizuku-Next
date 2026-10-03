package moe.shizuku.manager.ui.screen

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.manage.WifiPasswords
import moe.shizuku.manager.ui.component.CenteredMessage
import moe.shizuku.manager.ui.component.SegmentedCard
import moe.shizuku.manager.ui.component.SegmentedListItem

/**
 * The passwords of the networks this phone has saved.
 *
 * The list Settings keeps is the same one, and every row on it is a row of dots: the value is not
 * there to be read. This screen is the way to the real one, which is what Shizuku buys - see
 * [WifiPasswords] for what the call is and why it is the shell's to make.
 *
 * Hidden by default and revealed a row at a time, because a screen full of wifi keys is a screen
 * somebody reads off a photograph of. The copy is marked sensitive for the same reason: the
 * system's own clipboard preview puts what was copied at the top of the screen, which is the last
 * place a password should be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiPasswordsScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    var outcome by remember { mutableStateOf<WifiPasswords.Outcome?>(null) }
    // By SSID rather than by index: a re-read can reorder the list, and a row that reopened with
    // the password of the row above it is worse than one that closed again.
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(version) {
        outcome = null
        outcome = withContext(Dispatchers.IO) { WifiPasswords.read() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_wifi_passwords)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                IconButton(onClick = { version++ }) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.device_refresh)
                    )
                }
            }
        )

        val current = outcome
        if (current == null) {
            CenteredMessage { LoadingIndicator() }
            return@Column
        }

        when (current) {
            is WifiPasswords.Outcome.NoServer -> CenteredMessage {
                Text(
                    text = stringResource(R.string.wifi_passwords_needs_shizuku),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            is WifiPasswords.Outcome.Unsupported -> CenteredMessage {
                Text(
                    text = stringResource(R.string.wifi_passwords_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            is WifiPasswords.Outcome.Failed -> CenteredMessage {
                Text(
                    text = stringResource(R.string.wifi_passwords_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            is WifiPasswords.Outcome.Networks -> {
                if (current.items.isEmpty()) {
                    CenteredMessage {
                        Text(
                            text = stringResource(R.string.wifi_passwords_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
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
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        items(current.items, key = { it.ssid }) { network ->
                            val shown = network.ssid in revealed
                            val label = stringResource(R.string.tab_wifi_passwords)

                            SegmentedCard {
                                SegmentedListItem(
                                    headlineContent = { Text(network.ssid) },
                                    supportingContent = {
                                        PasswordLine(
                                            password = network.password,
                                            security = network.security,
                                            revealed = shown
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Outlined.Wifi, contentDescription = null)
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Nothing to show and nothing to copy on a network with
                                            // no key, so neither control is offered on one.
                                            if (network.password.isNotEmpty()) {
                                                IconButton(
                                                    onClick = {
                                                        revealed = if (shown) {
                                                            revealed - network.ssid
                                                        } else {
                                                            revealed + network.ssid
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        if (shown) Icons.Outlined.VisibilityOff
                                                        else Icons.Outlined.Visibility,
                                                        contentDescription = stringResource(
                                                            if (shown) R.string.wifi_passwords_hide
                                                            else R.string.wifi_passwords_show
                                                        )
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        if (copyPassword(context, label, network.password)) {
                                                            Toast.makeText(
                                                                context,
                                                                R.string.toast_copied_to_clipboard,
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.ContentCopy,
                                                        contentDescription = stringResource(
                                                            R.string.wifi_passwords_copy
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    centerSlots = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The key and what kind of key it is, on one line under the name of the network. */
@Composable
private fun PasswordLine(password: String, security: String, revealed: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                password.isEmpty() -> stringResource(R.string.wifi_passwords_none)
                revealed -> password
                // A length that says nothing: the key's own would be a hint, and this is a mask.
                else -> Mask
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (revealed) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = security,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The dots a hidden key is drawn as, whatever the key's length is. */
private const val Mask = "••••••••"

/**
 * Puts a key on the clipboard, marked sensitive.
 *
 * Written out rather than handed to a helper because of the flag: a plain text clip is previewed
 * by the system at the top of the screen and kept in the clipboard's history, and a wifi password
 * is the last thing that should be. The sensitive flag is the platform's own way of saying that,
 * and the one the Settings app marks its own copies with.
 */
private fun copyPassword(context: Context, label: String, value: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false

    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    clipboard.setPrimaryClip(clip)
    true
}.getOrDefault(false)
