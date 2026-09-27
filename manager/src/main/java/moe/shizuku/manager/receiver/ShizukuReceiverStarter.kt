package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.R
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.UserHandleCompat
import moe.shizuku.manager.worker.AdbStartWorker

object ShizukuReceiverStarter {

    const val NOTIFICATION_ID = 1447
    private const val CHANNEL_ID = "AdbStartWorker"

    enum class WorkerState {
        AWAITING_WIFI,
        AWAITING_RETRY,
        CONNECTING,
        WAITING_FOR_UNLOCK,
        RUNNING,
        STOPPED
    }

    fun start(context: Context, forceStart: Boolean = false, userInitiated: Boolean = false) {
        // A start request from any entry point clears manual-stop suppression.
        ShizukuSettings.setManuallyStopped(false)

        if ((UserHandleCompat.myUserId() > 0 || ShizukuStateMachine.isRunning()) && !forceStart) return

        StartStatusReporter.starting()

        if (ShizukuSettings.getLastLaunchMode() == LaunchMethod.ROOT) {
            rootStart(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            || EnvironmentUtils.isTelevision()
            || EnvironmentUtils.getAdbTcpPort() > 0
        ) {
            // UNKNOWN (e.g. after a fresh install) is treated as ADB so a start
            // request still works instead of silently doing nothing.
                if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                    // Falling back to USB debugging is controlled by settings.
                    val allowUsbFallback = ShizukuSettings.getAllowUsbFallback()
                    // User-initiated starts never wait for Wi-Fi; unattended
                    // background restarts honour the "wait for Wi-Fi" setting.
                    val immediate = userInitiated || !ShizukuSettings.getWaitForWifi()
                    AdbStartWorker.enqueue(
                        context,
                        enableWirelessDebugging = !allowUsbFallback,
                        immediate = immediate
                    )
                    updateNotification(context, WorkerState.AWAITING_WIFI)
                } else {
                    StartStatusReporter.failed(
                        context.getString(R.string.start_failed_write_secure_settings)
                    )
                    showPermissionErrorNotification(context)
                }
        } else {
            StartStatusReporter.failed(context.getString(R.string.start_failed_unsupported))
            Log.w(AppConstants.TAG, "Background start not supported")
        }
    }

    fun buildNotification(context: Context, msg: String? = null): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val cancelIntent = Intent(context, NotifCancelReceiver::class.java)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val attemptNowIntent = Intent(context, NotifAttemptReceiver::class.java)
        val attemptNowPendingIntent = PendingIntent.getBroadcast(
            context, 0, attemptNowIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restoreIntent = Intent(context, NotifRestoreReceiver::class.java)
        val restorePendingIntent = PendingIntent.getBroadcast(
            context, 0, restoreIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wifiIntent = SettingsPage.InternetPanel.buildIntent(context)
        val wifiPendingIntent = PendingIntent.getActivity(
            context, 0, wifiIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nb = NotificationCompat.Builder(context, CHANNEL_ID)
        
        if (msg != null) nb.setContentText(msg)

        return nb
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_notification_title))
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_server_restart, context.getString(R.string.wadb_notification_attempt_now), attemptNowPendingIntent)
            .addAction(R.drawable.ic_close_24, context.getString(android.R.string.cancel), cancelPendingIntent)
            .setDeleteIntent(restorePendingIntent)
            .setContentIntent(wifiPendingIntent)
            .build()
    }

    fun updateNotification(context: Context, state: WorkerState) {
        if (state == WorkerState.STOPPED) return
        val msgId = when (state) {
            WorkerState.AWAITING_WIFI -> R.string.wadb_notification_wifi_required
            WorkerState.AWAITING_RETRY -> R.string.wadb_notification_retry
            WorkerState.CONNECTING -> R.string.wadb_notification_connecting
            WorkerState.WAITING_FOR_UNLOCK -> R.string.wadb_notification_waiting_for_unlock
            else -> null
        }
        val msg = if (msgId != null) context.getString(msgId) else null
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(context, msg))
    }

    private fun rootStart(context: Context) {
        if (!Shell.getShell().isRoot) {
            //NotificationHelper.notify(context, AppConstants.NOTIFICATION_ID_STATUS, AppConstants.NOTIFICATION_CHANNEL_STATUS, R.string.notification_service_start_no_root)
            Shell.getCachedShell()?.close()
            return
        }

        try {
            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            Shell.cmd(Starter.internalCommand).exec()
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Failed to start Shizuku with root", e)
            ShizukuStateMachine.update()
        }
    }

    private fun showPermissionErrorNotification(context: Context) {

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val webpageIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thedjchi/Shizuku/wiki#shizuku-isnt-starting-on-boot-for-me"))
        val pendingWebpageIntent = PendingIntent.getActivity(
            context, 0, webpageIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val msg = context.getString(R.string.wadb_permission_error_notification_content)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_permission_error_notification_title))
            .setContentText(msg)
            .setSilent(true)
            .setContentIntent(pendingWebpageIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}