package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.shell.ShellBinderRequestHandler
import moe.shizuku.manager.utils.ShizukuStateMachine

class BinderRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "rikka.shizuku.intent.action.REQUEST_BINDER") return

        ShellBinderRequestHandler.handleRequest(context, intent)

        // Auto wake-up: an app asked for the binder while the server is down.
        // Start it in the background, but only when the user enabled start on
        // boot and did not deliberately stop Shizuku.
        if (!ShizukuStateMachine.isRunning() &&
            !ShizukuSettings.getManuallyStopped() &&
            ShizukuSettings.getStartOnBoot(context)
        ) {
            ShizukuReceiverStarter.start(context)
        }
    }
}
