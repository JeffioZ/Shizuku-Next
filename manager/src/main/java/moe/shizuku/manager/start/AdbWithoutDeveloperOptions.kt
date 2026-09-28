package moe.shizuku.manager.start

import android.content.Context
import android.provider.Settings
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings

/** Developer options' own master switch. */
private const val DEVELOPMENT_SETTINGS_ENABLED = "development_settings_enabled"

/** Wireless debugging's toggle, which Developer options drags down with it. */
private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

/**
 * Turns Developer options off and puts back the two ADB settings it takes with it.
 *
 * Apps that refuse to run on a device with Developer options on check that one flag, but
 * the ADB settings are not part of the flag: adbd keeps running if [Settings.Global.ADB_ENABLED]
 * and wireless debugging are still on, so Shizuku can start and restart itself on a device
 * that looks clean to them. It is the same thing System UI Tuner's "Enable ADB" does.
 *
 * Returns false when the writes were refused, which in practice means this app has no
 * WRITE_SECURE_SETTINGS yet (the permissions page says how to give it one).
 */
fun applyAdbWithoutDeveloperOptions(context: Context): Boolean {
    // The flag first: turning it off from here does not clear the ADB settings, but if some
    // system build does clear them, the writes below still land after it.
    val developerOptionsOff = context.writeGlobalSetting(DEVELOPMENT_SETTINGS_ENABLED, 0)
    val adb = context.writeGlobalSetting(Settings.Global.ADB_ENABLED, 1)
    val wireless = context.writeGlobalSetting(ADB_WIFI_ENABLED, 1)

    if (!developerOptionsOff || !adb || !wireless) {
        Log.w(
            AppConstants.TAG,
            "Could not keep ADB on with Developer options off " +
                "(developer options off: $developerOptionsOff, adb: $adb, wireless: $wireless)"
        )
        return false
    }
    return true
}

/** Puts Developer options back the way it was before the setting was switched on. */
fun restoreDeveloperOptions(context: Context) {
    context.writeGlobalSetting(DEVELOPMENT_SETTINGS_ENABLED, 1)
}

/**
 * Re-applies the setting after a reboot, when the system has cleared the ADB toggles but
 * left Developer options off. Called from the boot receiver.
 */
fun reapplyAdbWithoutDeveloperOptionsIfEnabled(context: Context) {
    if (!ShizukuSettings.getAdbWithoutDeveloperOptions()) return
    applyAdbWithoutDeveloperOptions(context)
}
