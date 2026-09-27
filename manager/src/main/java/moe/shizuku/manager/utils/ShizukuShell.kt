package moe.shizuku.manager.utils

import android.util.Log
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuShell"

/**
 * Runs [cmd] as shell through Shizuku and returns its output, or null when it couldn't
 * run at all (server down, permission denied, no output).
 *
 * For the few facts an app is not allowed to read itself — the SELinux status lives in
 * selinuxfs, which is world-readable on disk but denied to app domains by policy.
 */
fun runShellCommand(cmd: String): String? {
    if (!Shizuku.pingBinder()) return null

    return try {
        // Shizuku#newProcess is private, reach it through reflection.
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }

        val process = newProcess.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
            ?: return null

        process.inputStream.bufferedReader().use { it.readText() }.trim().ifEmpty { null }
    } catch (e: Throwable) {
        Log.w(TAG, "Shell command via Shizuku failed: $cmd", e)
        null
    }
}
