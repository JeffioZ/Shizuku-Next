package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.worker.AdbStartWorker

class NotifAttemptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // User-initiated ("Retry now") — never wait for Wi-Fi.
        AdbStartWorker.enqueue(
            context,
            enableWirelessDebugging = !ShizukuSettings.getAllowUsbFallback(),
            immediate = true
        )
    }
}
