package moe.shizuku.manager.adb

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.start.StartFailureKind
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.utils.EnvironmentUtils
import java.net.ConnectException

/**
 * Pairs wireless debugging without any typing, by reading the code straight out of
 * the system's "Pair with device" dialog.
 *
 * On phones the user still opens the dialog (Developer options → Wireless debugging
 * → "Pair device with pairing code"); this service picks up the code and port from
 * it, pairs, and starts Shizuku. That removes both the typing and the race against
 * the pairing dialog's short lifetime, which is what makes the notification flow
 * fail when the code is entered too late.
 *
 * On TV the dialog cannot be driven by hand, so the service brings the app up and
 * drives the whole flow itself, then switches off again.
 */
class AdbPairingAccessibilityService : AccessibilityService() {

    private data class PairingDialog(val host: String, val port: Int, val code: String)

    private val handler = Handler(Looper.getMainLooper())

    private var pairing = false

    /** Last code we attempted, so dialog refreshes don't retry the code we failed on. */
    private var lastAttempt: String? = null

    /** Only TV needs driving; elsewhere the user drives the dialog themselves. */
    private val isTelevision
        get() = EnvironmentUtils.isTelevision() && EnvironmentUtils.isTlsSupported()

    override fun onServiceConnected() {
        super.onServiceConnected()

        if (!isTelevision) return

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(AppConstants.EXTRA_SHOW_PAIRING_DIALOG, true)
        }
        runCatching { startActivity(intent) }

        handler.postDelayed({
            toast(getString(R.string.toast_pairing_timeout))
            disableSelf()
        }, 60_000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (pairing || event == null) return

        // The pairing dialog is always hosted by the settings app. Ignore every other
        // window rather than reading content we have no reason to look at.
        val packageName = event.packageName?.toString().orEmpty()
        if (!packageName.contains("settings", ignoreCase = true)) return

        val dialog = readPairingDialog() ?: return

        val signature = "${dialog.host}:${dialog.port}:${dialog.code}"
        if (signature == lastAttempt) return
        lastAttempt = signature

        pair(dialog)
    }

    /**
     * Looks for the pairing dialog in the visible windows and pulls the code and the
     * `host:port` out of it. Reading the whole window (rather than the changed node's
     * own text) is what makes this work on phones, where the code and the port sit in
     * sibling text views.
     */
    private fun readPairingDialog(): PairingDialog? {
        val windows = runCatching { windows }.getOrNull().orEmpty()

        for (window in windows) {
            val root = window?.root ?: continue

            val texts = mutableListOf<String>()
            collectText(root, texts)

            // Match the dialog itself, not the wireless-debugging page behind it,
            // which also mentions pairing but shows a different (connect) port.
            if (texts.none {
                    it.contains("pairing code", ignoreCase = true) ||
                        it.contains("pair with device", ignoreCase = true)
                }
            ) continue

            val code = texts.firstOrNull { CODE_REGEX.matches(it) } ?: continue
            val hostPort = texts.asSequence()
                .mapNotNull { HOST_PORT_REGEX.find(it) }
                .firstOrNull() ?: continue

            val host = hostPort.groupValues[1]
            val port = hostPort.groupValues[2].toIntOrNull() ?: continue

            return PairingDialog(host, port, code)
        }

        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collectText(it, out) }
        }
    }

    private fun pair(dialog: PairingDialog) {
        pairing = true

        GlobalScope.launch(Dispatchers.IO) {
            val key = try {
                AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to load adb key", e)
                finish(getString(R.string.adb_error_key_store))
                return@launch
            }

            AdbPairingClient(dialog.host, dialog.port, dialog.code, key)
                .runCatching { start() }
                .onFailure { e ->
                    Log.w(TAG, "Pair failed", e)
                    finish(
                        when (e) {
                            is ConnectException -> getString(R.string.cannot_connect_port)
                            is AdbInvalidPairingCodeException -> getString(R.string.paring_code_is_wrong)
                            is AdbKeyException -> getString(R.string.adb_error_key_store)
                            else -> e.localizedMessage ?: e.javaClass.simpleName
                        }
                    )
                }
                .onSuccess { success ->
                    if (!success) {
                        finish(getString(R.string.notification_adb_pairing_failed_title))
                        return@onSuccess
                    }

                    Log.i(TAG, "Paired from the pairing dialog, starting Shizuku")
                    onPaired()
                }
        }
    }

    private fun onPaired() {
        // The notification-driven search has nothing left to find.
        runCatching { startService(AdbPairingService.stopIntent(this)) }

        // Pairing was the only thing standing between the user and a running Shizuku,
        // so start it instead of sending them back to tap Start again.
        StartStatusReporter.clear()
        toast(getString(R.string.notification_adb_pairing_succeed_text))
        ShizukuReceiverStarter.start(this, userInitiated = true)

        pairing = false
        if (isTelevision) disableSelf()
    }

    private fun finish(message: String) {
        pairing = false
        StartStatusReporter.failed(message, StartFailureKind.PAIRING)
        toast(message)
    }

    private fun toast(message: String) {
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(this@AdbPairingAccessibilityService, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "AdbPairingAccessibility"

        private val CODE_REGEX = Regex("""\d{6}""")
        private val HOST_PORT_REGEX = Regex("""(\d{1,3}(?:\.\d{1,3}){3}):(\d{2,5})""")
    }
}
