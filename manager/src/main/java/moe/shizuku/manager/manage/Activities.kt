package moe.shizuku.manager.manage

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.Diag
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.runShellCommand
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Every activity an app declares, and how to start the ones it keeps to itself.
 *
 * Two thirds of what is interesting in an installed app is not on its launcher icon: settings
 * screens, debug screens, internal editors, the activity a deep link lands on. They are declared
 * in the manifest and this app can list them, because the manifest is public - and most of them
 * carry `android:exported="false"`, which is a statement that only the app itself, the system, or
 * the shell may start them.
 *
 * So there are two ways in, and which one applies is the export flag:
 *
 *     exported or launcher    the platform starts it, the same way one app starts another. No
 *                             privilege, no assistant, nothing to put back afterwards.
 *     everything else         the system starts it on our behalf, by way of the assistant.
 *
 * The second is the trick, and it is worth writing down plainly because every line of it looks
 * wrong: the `assistant` secure setting is pointed at the component, `voice_interaction_service`
 * is blanked so nothing else answers, and then a real `KEYCODE_ASSIST` is pressed. The system
 * reads the setting it owns and starts the activity, as the system, with no export check to fail.
 * The original values are written down before the swap and put back the moment the activity has
 * been given its two seconds - and because they are written to disk rather than held in memory,
 * a process that died in the middle is fixed by [restorePending] on the next start. Leaving
 * somebody with no assistant would be a much worse bug than not having the feature.
 *
 * `KEYCODE_ASSIST` has no app-facing API to trigger - firing `ACTION_ASSIST` only opens a chooser
 * - so it is pressed through the shell, which needs nothing beyond the `WRITE_SECURE_SETTINGS`
 * this app already holds for hiding. `SearchManager` has hidden entry points for the same thing,
 * and they are the fallback for a phone with no Shizuku running at all.
 */
object Activities {

    private const val TAG = "Activities"

    /** `KeyEvent.KEYCODE_ASSIST`. Spelled out, because the constant lives in a framework class. */
    private const val ASSIST_KEYCODE = 219

    /** How long the system is given to read the swapped setting and start the activity. */
    private const val ASSIST_WINDOW_MS = 2000L

    private const val KEY_ASSISTANT = "assistant"
    private const val KEY_VOICE_INTERACTION = "voice_interaction_service"

    private const val PREF_BACKUP = "activities_assistant_backup"

    /** Between the two saved values, and standing in for "there was nothing here". */
    private const val FIELD = "\u001F"
    private const val NOTHING = "\u0000"

    /** One activity of an app, as its manifest declares it. */
    data class Activity(
        val packageName: String,
        val name: String,
        /** What the app calls it in its own manifest, or null when it just uses the app's name. */
        val label: String?,
        val exported: Boolean,
        val launcher: Boolean
    ) {
        val component: ComponentName get() = ComponentName(packageName, name)

        /** The last part of the class name, which is what a person recognises. */
        val shortName: String get() = name.substringAfterLast('.')

        val title: String get() = label ?: shortName
    }

    /** How a launch went, so the screen can say the true thing rather than "done". */
    enum class Outcome { STARTED, ELEVATED, NO_SHELL, REFUSED }

    // ---- the list ----------------------------------------------------------------

    /**
     * The activities of a package that has already been read with `GET_ACTIVITIES`.
     *
     * Passed in rather than re-read so that the app list, which needs every package anyway, does
     * not pay for a second round of package manager calls.
     */
    fun of(packageManager: PackageManager, info: PackageInfo): List<Activity> {
        val launcher = runCatching {
            packageManager.getLaunchIntentForPackage(info.packageName)?.component?.className
        }.getOrNull()

        return order(info.activities.orEmpty().map { declared -> activity(packageManager, declared, launcher) })
    }

    /**
     * Most reachable first: the launcher entry, then anything another app may start, then the ones
     * only this trick can reach - which are the reason somebody opened the list at all.
     */
    internal fun order(activities: List<Activity>): List<Activity> = activities.sortedWith(
        compareByDescending<Activity> { it.launcher }
            .thenByDescending { it.exported }
            .thenBy { it.name.lowercase() }
    )

    fun of(packageManager: PackageManager, packageName: String): List<Activity> =
        runCatching { packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES) }
            .onFailure { Diag.warn(TAG, "could not read the activities of $packageName", it) }
            .getOrNull()
            ?.let { of(packageManager, it) }
            ?: emptyList()

    private fun activity(
        packageManager: PackageManager,
        declared: ActivityInfo,
        launcher: String?
    ): Activity = Activity(
        packageName = declared.packageName ?: "",
        name = declared.name ?: "",
        // The label falls back to the app's own name, which is not more informative than the
        // class name beside it - so a label that only repeats the app is dropped.
        label = runCatching { declared.loadLabel(packageManager)?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != declared.name },
        exported = declared.exported,
        launcher = declared.name == launcher
    )

    // ---- starting one ------------------------------------------------------------

    fun launch(context: Context, activity: Activity): Outcome = launch(context, activity, activity.exported, activity.launcher)

    private fun launch(context: Context, activity: Activity, exported: Boolean, launcher: Boolean): Outcome {
        if (exported || launcher) {
            val intent = Intent()
                .setComponent(activity.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                Diag.info(TAG, "started ${activity.component}")
                return Outcome.STARTED
            }
            // An exported activity can still refuse - a signature permission in the manifest does
            // not show in the export flag - so this falls through to the elevated route rather
            // than reporting a failure the user has no way to act on.
        }

        if (!holdsWriteSecureSettings()) return Outcome.REFUSED
        if (elevate(activity.component)) return Outcome.ELEVATED

        // Neither route worked. Say which half was missing rather than a flat refusal, because
        // "start Shizuku" is something the user can act on.
        return if (ShizukuStateMachine.isRunning()) Outcome.REFUSED else Outcome.NO_SHELL
    }

    /**
     * Whether this app can do the swap at all.
     *
     * Only the grant is required, not Shizuku: asking for the assist in-process needs nothing else
     * on any device where that route answers, and the shell is the fallback rather than the price
     * of entry.
     */
    fun canElevate(): Boolean = holdsWriteSecureSettings()

    private fun holdsWriteSecureSettings(): Boolean = runCatching {
        ShizukuApplication.application
            .checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Starts [component] through the assistant, and puts the assistant back before returning.
     *
     * Write down, then swap, then press, then wait, then restore - in that order, and the restore
     * is in a `finally`. The only way this leaves the phone as it found it is if every step is
     * unconditional.
     */
    private fun elevate(component: ComponentName): Boolean {
        val resolver = ShizukuApplication.application.contentResolver
        val assistant = Settings.Secure.getString(resolver, KEY_ASSISTANT)
        val voice = Settings.Secure.getString(resolver, KEY_VOICE_INTERACTION)

        // Committed rather than applied: this has to be on disk before the setting it describes
        // is changed, because the process dying between the two is exactly the case it is for.
        val written = ShizukuSettings.getPreferences().edit()
            .putString(PREF_BACKUP, backupLine(assistant, voice))
            .commit()
        if (!written) {
            Diag.warn(TAG, "the assistant could not be written down, so it will not be swapped")
            return false
        }

        return try {
            Settings.Secure.putString(resolver, KEY_ASSISTANT, component.flattenToString())
            Settings.Secure.putString(resolver, KEY_VOICE_INTERACTION, "")

            // The shell first, and deliberately: a real injected key event either fires or the
            // command does not run, while the in-process call can return having done nothing at
            // all - a hidden method that answers is not the same as an assist that happened. So
            // the route whose outcome is legible is the one used whenever it is available, and
            // the in-process call is what covers a phone with no Shizuku running.
            val asked = pressAssistKey() || askInProcess()
            if (asked) Thread.sleep(ASSIST_WINDOW_MS)

            Diag.info(
                TAG,
                if (asked) "asked the system to start $component"
                else "the assist request could not be made"
            )
            asked
        } catch (e: Throwable) {
            Diag.warn(TAG, "the elevated launch failed", e)
            false
        } finally {
            restoreAssistant()
        }
    }

    /**
     * Asks the system for an assist without leaving the process.
     *
     * `SearchManager` has the two hidden entry points for it - `launchAssist` and `startAssist` -
     * which is how the Shizuku plugin for Activity Launcher asks for the same assist. Reached only
     * when the shell is not there to press the key, so a phone without Shizuku running can still
     * open a hidden screen.
     */
    private fun askInProcess(): Boolean {
        val search = runCatching {
            ShizukuApplication.application.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
        }.getOrNull() ?: return false

        val launched = runCatching {
            HiddenApiBypass.invoke(SearchManager::class.java, search, "launchAssist", Bundle())
        }.isSuccess
        if (launched) {
            Diag.info(TAG, "asked the system for an assist in-process")
            return true
        }

        val started = runCatching {
            HiddenApiBypass.invoke(SearchManager::class.java, search, "startAssist", Bundle())
        }.isSuccess
        if (started) Diag.info(TAG, "started an assist in-process")
        return started
    }

    /** A real assist key press, which only the shell may make. */
    private fun pressAssistKey(): Boolean {
        if (!ShizukuStateMachine.isRunning()) return false
        // A marker after the key event, because the command answers with nothing and an empty
        // answer is how this app spells "the shell did not run".
        val pressed = runShellCommand("input keyevent $ASSIST_KEYCODE; echo pressed") != null
        if (pressed) Diag.info(TAG, "asked the system for an assist through the shell")
        return pressed
    }

    internal fun backupLine(assistant: String?, voice: String?): String =
        (assistant ?: NOTHING) + FIELD + (voice ?: NOTHING)

    /**
     * The saved pair, or null when the line is not one.
     *
     * A value that was not set has to come back as null rather than as an empty string: writing
     * "" back over an assistant that was never set is a different setting than the one that was
     * there, and this line is the only record of which it was.
     */
    internal fun parseBackup(line: String?): Pair<String?, String?>? {
        if (line == null) return null
        val parts = line.split(FIELD)
        if (parts.size != 2) return null
        fun value(part: String) = part.takeIf { it != NOTHING }
        return value(parts[0]) to value(parts[1])
    }

    /** The saved pair, or null when nothing was ever swapped. */
    private fun backup(): Pair<String?, String?>? =
        parseBackup(ShizukuSettings.getPreferences().getString(PREF_BACKUP, null))

    /** Puts the assistant back, and forgets the backup only once it is really back. */
    private fun restoreAssistant() {
        val backup = backup() ?: return
        if (!holdsWriteSecureSettings()) return

        val resolver = ShizukuApplication.application.contentResolver
        val restored = runCatching {
            Settings.Secure.putString(resolver, KEY_ASSISTANT, backup.first)
            Settings.Secure.putString(resolver, KEY_VOICE_INTERACTION, backup.second)
        }.onFailure { Diag.warn(TAG, "putting the assistant back failed", it) }.isSuccess

        if (restored) {
            ShizukuSettings.getPreferences().edit().remove(PREF_BACKUP).apply()
            Diag.info(TAG, "the assistant is back where it was")
        }
    }

    /**
     * Puts the assistant back after a launch that did not finish.
     *
     * Called when the app starts, and the reason the backup is a preference: the one failure this
     * feature must not have is a phone left with no assistant because a process died holding the
     * name of the one it replaced.
     */
    fun restorePending() {
        if (backup() == null) return
        Diag.warn(TAG, "an activity launch did not finish: putting the assistant back")
        restoreAssistant()
    }
}
