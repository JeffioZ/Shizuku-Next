package moe.shizuku.manager.start

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.runShellCommand

/** The global setting Android keeps wireless debugging in. */
private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

/** WRITE_SECURE_SETTINGS can only be granted over ADB, so it is checked before use. */
fun Context.hasWriteSecureSettings(): Boolean =
    checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

fun Context.isWirelessDebuggingEnabled(): Boolean =
    Settings.Global.getInt(contentResolver, ADB_WIFI_ENABLED, 0) == 1

/**
 * Whether [startMethod] is going to need a secure setting written before it can work.
 *
 * Only the wireless flow ever does: it may have to switch wireless debugging on, and the
 * TLS port isn't advertised without it. USB and the classic ADB port need nothing of the
 * sort, and neither does a wireless start where the toggle is already on — so those
 * shouldn't be held up for a permission they won't use.
 */
fun Context.needsWriteSecureSettingsFor(@ShizukuSettings.StartMethod startMethod: Int): Boolean =
    startMethod == ShizukuSettings.StartMethod.WIRELESS && !isWirelessDebuggingEnabled()

/**
 * Writes a global setting, and shrugs when the app isn't allowed to.
 *
 * Every one of these writes is a nudge to adbd — switch a debugging toggle on, or bounce
 * it so it re-announces itself — and without WRITE_SECURE_SETTINGS the settings provider
 * *throws* instead of ignoring it. That used to take the whole start down with it, and the
 * SecurityException was then reported as "network not authorized, re-pair the device",
 * which is nowhere near what happened. A denied nudge should fail the nudge, nothing more.
 */
fun Context.writeGlobalSetting(key: String, value: Int): Boolean = runCatching {
    Settings.Global.putInt(contentResolver, key, value)
    true
}.getOrElse {
    Log.w(AppConstants.TAG, "Could not write the $key setting (WRITE_SECURE_SETTINGS missing?)")
    false
}

fun Context.writeGlobalLongSetting(key: String, value: Long): Boolean = runCatching {
    Settings.Global.putLong(contentResolver, key, value)
    true
}.getOrElse {
    Log.w(AppConstants.TAG, "Could not write the $key setting (WRITE_SECURE_SETTINGS missing?)")
    false
}

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Grants us WRITE_SECURE_SETTINGS through the already running server.
 *
 * The permission can only be handed out over ADB, which used to mean every fresh install
 * — and every install after a re-signed update — needed a computer before wireless
 * debugging could be switched on. A running server *is* ADB (or root), so it can pass the
 * permission on the same way `pm grant` does, and the user never has to run that command.
 *
 * Safe to call whenever a server is seen running: it returns immediately once the
 * permission is there, and grants at most once per install.
 */
fun grantWriteSecureSettingsIfNeeded() {
    val context = ShizukuApplication.appContext
    if (context.hasWriteSecureSettings()) return

    scope.launch {
        // `pm grant` prints nothing when it works, so the permission itself is the answer.
        val output = runShellCommand(
            "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        )
        if (context.hasWriteSecureSettings()) {
            Log.i(AppConstants.TAG, "Granted WRITE_SECURE_SETTINGS through the running server")
        } else {
            Log.w(
                AppConstants.TAG,
                "Could not grant WRITE_SECURE_SETTINGS through the server" +
                    (output?.let { ": $it" } ?: "")
            )
        }
    }
}
