package moe.shizuku.manager.home

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.TypefaceSpan
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbPairingAccessibilityService
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage

fun Context.showAccessibilityDialog() {
    val hasWriteSecureSettings = (checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)

    val installer = packageManager.getInstallerPackageName(packageName)
    val isInstalledByPlayOrAdb = (installer == "com.android.vending") || (installer == null)
    val hasAccessRestrictedSettings = isInstalledByPlayOrAdb || Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    if (isAccessibilityEnabled()) {
        showNavigateDialog()
    } else if (hasWriteSecureSettings) {
        // Switching it on silently leaves the user with no next step to follow (on TV
        // the service drives the rest itself; on a phone the user has to open the
        // pairing dialog), so always explain what to do next.
        if (enableAccessibilityService()) {
            showNavigateDialog()
            return
        }
        showPermissionDialog()
    } else if (!hasAccessRestrictedSettings) {
        showPermissionDialog()
    } else {
        showEnableDialog()
    }
}

private fun Context.showPermissionDialog() {
    val permissionName = "ACCESS_RESTRICTED_SETTINGS"
    val permissionCommand = "adb shell cmd appops set $packageName $permissionName allow"
    val styledPermissionCommand =
        SpannableString(permissionCommand).apply {
            setSpan(TypefaceSpan("monospace"), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    MaterialAlertDialogBuilder(this)
        .setTitle(android.R.string.dialog_alert_title)
        .setMessage(
            TextUtils.expandTemplate(
                getString(R.string.dialog_adb_pairing_accessibility_permission),
                permissionName,
                styledPermissionCommand,
            ),
        ).setPositiveButton("Continue") { _, _ -> showEnableDialog() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun Context.showEnableDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_adb_pairing_title)
        .setMessage(R.string.dialog_adb_pairing_accessibility_enable)
        .setPositiveButton(R.string.enable) { _, _ ->
            SettingsPage.Accessibility.launch(this)
        }.setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun Context.showNavigateDialog() {
    val isTelevision = EnvironmentUtils.isTelevision() && EnvironmentUtils.isTlsSupported()

    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_adb_pairing_title)
        .setMessage(
            if (isTelevision) R.string.dialog_adb_pairing_accessibility_navigate
            else R.string.auto_pair_instructions
        )
        .setPositiveButton(R.string.development_settings) { _, _ ->
            SettingsPage.Developer.HighlightWirelessDebugging.launch(this)
        }
        .setNegativeButton(R.string.auto_pair_manual) { _, _ ->
            // Reading the code can still fail (OEM dialog, service killed) — keep the
            // notification flow one tap away as the fallback.
            runCatching {
                startActivity(Intent(this, AdbPairingTutorialActivity::class.java))
            }
        }
        .show()
}

/**
 * True when the pairing accessibility service is switched on, i.e. codes can be read
 * out of the pairing dialog without the user typing them.
 */
fun Context.isAccessibilityEnabled(): Boolean {
    val accessibilityServiceName = "$packageName/${AdbPairingAccessibilityService::class.java.canonicalName}"
    return getEnabledAccessibilityServices()?.any { it.equals(accessibilityServiceName) } ?: false
}

private fun Context.getEnabledAccessibilityServices(): List<String>? {
    val enabledServices =
        Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
    return enabledServices?.split(":")
}

private fun Context.enableAccessibilityService(): Boolean {
    if (isAccessibilityEnabled()) return true

    val accessibilityServiceName = "$packageName/${AdbPairingAccessibilityService::class.java.canonicalName}"
    val enabledServices = getEnabledAccessibilityServices()
    val newServices =
        if (enabledServices.isNullOrEmpty()) {
            accessibilityServiceName
        } else {
            enabledServices.joinToString(":") + ":$accessibilityServiceName"
        }

    Settings.Secure.putString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        newServices,
    )

    return isAccessibilityEnabled()
}
