package moe.shizuku.manager.adb

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.pm.PackageManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.EOFException
import java.net.Socket
import java.net.SocketException
import javax.net.ssl.SSLException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine

private const val TAG = "AdbStarter"

/** The ADB TLS handshake was rejected: the device is not paired with us. */
class AdbPairingRequiredException(message: String?, cause: Throwable?) :
    Exception(message, cause)

private fun Throwable.isCertificateUnknown(): Boolean {
    val text = message ?: return false
    return text.contains("CERTIFICATE_UNKNOWN", ignoreCase = true) ||
        text.contains("SSLV3_ALERT", ignoreCase = true)
}

object AdbStarter {
    /**
     * @param openTcpPort open the classic ADB port as part of this start, even when
     *   the "TCP mode" setting is off. A USB start asks for exactly that: the port is
     *   what makes later USB starts work without wireless debugging or a computer.
     */
    suspend fun startAdb(
        context: Context,
        port: Int,
        log: ((String) -> Unit)? = null,
        openTcpPort: Boolean = false
    ) {
        suspend fun AdbClient.runCommand(cmd: String) {
            command(cmd) { log?.invoke(String(it)) }
        }

        // Deliberately NOT resetting adb_wifi_enabled here: keeping wireless
        // debugging on lets Shizuku restart without USB debugging or Wi-Fi.
        ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
        log?.invoke("Starting with wireless adb...\n")

        withContext(Dispatchers.IO) {
            val key = runCatching { AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku") }
                .getOrElse {
                    if (it is CancellationException) throw it
                    else throw AdbKeyException(it)
                }

            var activePort = port
            val tcpPort = ShizukuSettings.getTcpPort()
            // Classic TCP mode rides on the USB debugging toggle. Only switch adbd
            // into TCP mode when USB debugging is already on — switching during a
            // wireless-only start would tie future restarts to a toggle that's off.
            val usbDebugging = Settings.Global.getInt(
                context.contentResolver, Settings.Global.ADB_ENABLED, 0
            ) == 1
            // Only a USB start opens/switches to the classic ADB port. Letting a
            // wireless start do it turned "Wireless debugging" into a TCP connection
            // that then reported itself as USB debugging.
            val keepTcpPort = openTcpPort
            var viaTcp = !EnvironmentUtils.isTlsSupported() ||
                    (activePort > 0 && activePort == EnvironmentUtils.getAdbTcpPort())
            if (keepTcpPort && usbDebugging && activePort != tcpPort) {
                log?.invoke("Connecting on port $activePort...")

                AdbClient("127.0.0.1", activePort, key).use { client ->
                    // Same pairing detection as the main path — a TLS rejection here
                    // must not surface as a raw SSL error either.
                    client.connectForPairing()

                    log?.invoke("Successfully connected on port $activePort...")
                    log?.invoke("\nRestarting in TCP mode port: $tcpPort")

                    activePort = tcpPort
                    viaTcp = true
                    runCatching {
                        client.command("tcpip:$activePort")
                    }.onFailure { if (it !is EOFException && it !is SocketException) throw it } // Expected when ADB restarts in TCP mode

                    // adbd restarts on the new port; wait until it actually listens so
                    // the connect below targets a live socket (instead of the stale one).
                    if (!waitForPortAvailable("127.0.0.1", activePort)) {
                        Log.w(TAG, "Timed out waiting for ADB to listen on TCP port $activePort")
                    }
                }
            }

            log?.invoke("Connecting on port $activePort...")

            AdbClient("127.0.0.1", activePort, key).use { client ->
                // connectWithRetry reports a rejected handshake as pairing-required.
                connectWithRetry(client)
                log?.invoke("Successfully connected on port $activePort...\n")
                client.runCommand("shell:${Starter.internalCommand}")
            }

            // Remember which transport launched the server so the home status
            // card can show whether it runs via wireless or USB debugging.
            ShizukuSettings.setLastAdbTransport(
                if (viaTcp) ShizukuSettings.ADB_TRANSPORT_TCP else ShizukuSettings.ADB_TRANSPORT_TLS
            )
        }
    }

    suspend fun stopTcp(context: Context, port: Int) {
        runCatching {
            val cr = context.contentResolver
            val hadUsbDebugging = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
            val canWriteSettings =
                context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
            if (canWriteSettings) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            }

            val adbEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
            if (adbEnabled == 0) throw IllegalStateException("ADB is not enabled")

            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
            val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            withContext(Dispatchers.IO) {
                AdbClient("127.0.0.1", port, key).use { client ->
                    connectWithRetry(client)
                    client.command("usb:")
                }
            }
            // USB debugging was only borrowed to issue the command — restore it
            if (!hadUsbDebugging && canWriteSettings) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
            }
            // Resolve the STOPPING state we set above: closing the TCP port does
            // not kill an already-running server, and if nothing was running the
            // state must return to STOPPED rather than stick at STOPPING (which
            // would suppress the watchdog's dead-check).
            ShizukuStateMachine.update()
        }.onFailure {
            // Never leave the state stuck at STOPPING
            ShizukuStateMachine.update()
            if (EnvironmentUtils.getAdbTcpPort() > 0) {
                withContext(Dispatchers.Main) {
                    val errorMsg = when (it) {
                        is AdbKeyException -> context.getString(R.string.adb_error_key_store)
                        else -> it.message
                    }
                    Toast.makeText(context, context.getString(R.string.adb_error_stop_tcp) + ". ${errorMsg}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private suspend fun waitForPortAvailable(
        host: String,
        port: Int,
        timeoutMs: Long = 15_000L
    ): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket(host, port).use { return@withContext true }
            } catch (_: Exception) {
                delay(300)
            }
        }
        false
    }

    /**
     * Every connection to adbd goes through here, so a TLS rejection is always
     * reported as [AdbPairingRequiredException] (the device doesn't trust our key
     * yet — never paired, or the pairing was invalidated) instead of a raw SSL
     * error the user can't act on.
     */
    private suspend fun AdbClient.connectForPairing() {
        try {
            connect()
        } catch (e: Exception) {
            if (e is AdbPairingRequiredException) throw e
            if (e is SSLException || e.isCertificateUnknown()) {
                Log.w(TAG, "TLS handshake rejected, pairing required", e)
                throw AdbPairingRequiredException(e.message, e)
            }
            throw e
        }
    }

    private suspend fun connectWithRetry(client: AdbClient) {
        var delayTime = 0L
        val maxAttempts = 5
        for (attempt in 1..maxAttempts) {
            try {
                delay(delayTime)
                client.connectForPairing()
                break
            } catch (e: Exception) {
                if (
                    attempt == maxAttempts ||
                    e is CancellationException ||
                    e is SocketTimeoutException
                ) throw e
                delayTime += 1000
            }
        }
    }
}
