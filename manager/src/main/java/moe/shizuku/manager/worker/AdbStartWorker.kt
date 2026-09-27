package moe.shizuku.manager.worker

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.asFlow
import androidx.work.*
import java.io.EOFException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbPairingRequiredException
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter.WorkerState
import moe.shizuku.manager.settings.BugReportDialogActivity
import moe.shizuku.manager.start.StartFailureKind
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine

class AdbStartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** The method this start was asked for, so notifications retry the same way. */
    private var requestedMethod = ShizukuSettings.StartMethod.WIRELESS

    private fun notify(state: ShizukuReceiverStarter.WorkerState) =
        ShizukuReceiverStarter.updateNotification(applicationContext, state, requestedMethod)

    override suspend fun doWork(): Result {
        try {
            requestedMethod = inputData.getInt(KEY_START_METHOD, ShizukuSettings.StartMethod.WIRELESS)

            notify(WorkerState.RUNNING)

            val cr = applicationContext.contentResolver
            val startMethod = requestedMethod
            val usbMethod = startMethod == ShizukuSettings.StartMethod.USB

            // Which debugging toggle we use is the entire difference between the two
            // ADB start methods, so each one only ever touches its own:
            //   USB      -> USB debugging, never wireless debugging
            //   WIRELESS -> wireless debugging, never USB debugging. Forcing USB
            //               debugging on is what the old USB "fallback" did, and it
            //               is what killed Shizuku on some Chinese devices when the
            //               USB mode was File Transfer and the screen went off.
            val wirelessAlreadyEnabled = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1
            val usbAlreadyEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1

            if (usbMethod) {
                if (!usbAlreadyEnabled) {
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                }
                // Don't let the authorized connection expire while we connect.
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            } else if (wirelessAlreadyEnabled) {
                // Wireless is already active. Writing adb_wifi_enabled=1 again is a
                // no-op (SettingsProvider does not notify on the same value), so adbd
                // never reinitialises wireless and mDNS discovery finds nothing. Write
                // 0 first so the re-enable below is a real 0->1 change, forcing adbd to
                // restart wireless and emit a fresh mDNS announcement.
                Settings.Global.putInt(cr, "adb_wifi_enabled", 0)
                try {
                    delay(200)
                } finally {
                    // Restore unconditionally: normally a harmless no-op, and on
                    // cancellation it avoids leaving wireless disabled.
                    Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                }
            }
            // else: wireless is off and we are starting over it — the callbackFlow
            // below writes adb_wifi_enabled=1 so the start proceeds over wireless.

            val tcpPort = EnvironmentUtils.getAdbTcpPort()
            // "TCP mode off" means don't keep a port open for wireless restarts — but
            // a USB start exists to use that port, so it opens and keeps it instead of
            // closing the very thing it needs.
            if (tcpPort > 0 && !ShizukuSettings.getTcpMode() && !usbMethod) {
                AdbStarter.stopTcp(applicationContext, tcpPort)
            }


            // A USB start is USB only: the classic ADB port, where the connection
            // authenticates itself and Android asks to allow USB debugging. It never
            // falls back to wireless discovery — going through the wireless port is
            // what used to make a "USB" start depend on Wi-Fi and pairing.
            if (usbMethod && tcpPort <= 0) {
                StartStatusReporter.failed(
                    applicationContext.getString(R.string.start_failed_usb_no_port)
                )
                notify(WorkerState.AWAITING_RETRY)
                return Result.failure()
            }

            // A wireless start always goes over the wireless (TLS) port. Taking the
            // classic ADB port here — which TCP mode keeps open — is what made a
            // "Wireless debugging" start run over USB debugging's transport and report
            // itself as USB. Only the USB method, and platforms without wireless
            // debugging at all, use the classic port.
            val useClassicPort = usbMethod || !EnvironmentUtils.isTlsSupported()
            val port = tcpPort.takeIf { useClassicPort }
                ?: callbackFlow {
                val adbMdns = AdbMdns(applicationContext, AdbMdns.TLS_CONNECT) { p ->
                    if (p.second > 0) trySend(p.second)
                }

                var awaitingAuth = false
                var timeoutJob: Job? = null
                var unlockReceiver: BroadcastReceiver? = null

                fun startDiscoveryWithTimeout() {
                    adbMdns.start()
                    timeoutJob?.cancel()
                    timeoutJob = launch {
                        delay(15_000)
                        close(TimeoutException("Timed out during mDNS port discovery"))
                    }
                }

                fun handleAuth() {
                    val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (km.isKeyguardLocked) {
                        val notification = ShizukuReceiverStarter.buildNotification(
                            applicationContext,
                            null,
                            requestedMethod
                        )
                        val foregroundInfo = ForegroundInfo(
                            ShizukuReceiverStarter.NOTIFICATION_ID,
                            notification
                        )
                        setForegroundAsync(foregroundInfo)
                        notify(WorkerState.WAITING_FOR_UNLOCK)

                        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                        unlockReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (intent.action == Intent.ACTION_USER_PRESENT) {
                                    context.unregisterReceiver(this)
                                    unlockReceiver = null
                                    // Wireless debugging must be on for the TLS port to be
                                    // advertised, otherwise mDNS discovery can never find it.
                                    Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                                }
                            }
                        }
                        applicationContext.registerReceiver(unlockReceiver, filter)
                    } else awaitingAuth = true
                    timeoutJob?.cancel()
                    adbMdns.stop()
                }

                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        when (Settings.Global.getInt(cr, "adb_wifi_enabled", 0)) {
                            0 -> if (awaitingAuth) {
                                close(SecurityException("Network is not authorized for wireless debugging"))
                            } else handleAuth()
                            1 -> startDiscoveryWithTimeout()
                        }
                    }
                }

                // Always required for discovery — without it the device never
                // advertises _adb-tls-connect and the worker just times out.
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                cr.registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"), false, observer)
                startDiscoveryWithTimeout()

                awaitClose {
                    adbMdns.stop()
                    timeoutJob?.cancel()
                    cr.unregisterContentObserver(observer)
                    unlockReceiver?.let { applicationContext.unregisterReceiver(it) }
                }
            }.first()
            
            notify(WorkerState.CONNECTING)
            AdbStarter.startAdb(applicationContext, port, openTcpPort = usbMethod)
            Starter.waitForBinder()

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)

            StartStatusReporter.succeeded()
            return Result.success()
        } catch (e: CancellationException) {
            val state = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                WorkerState.AWAITING_RETRY
            } else {
                when (stopReason) {
                    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> WorkerState.AWAITING_WIFI
                    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> WorkerState.STOPPED
                    else -> WorkerState.AWAITING_RETRY
                }
            }
            notify(state)

            throw e
        } catch (e: Exception) {
            // Surface the reason in the app; the worker may still retry.
            val (message, kind) = when (e) {
                is AdbPairingRequiredException ->
                    applicationContext.getString(R.string.start_failed_pairing_required) to
                        StartFailureKind.PAIRING

                is TimeoutException ->
                    applicationContext.getString(R.string.start_failed_no_port) to
                        StartFailureKind.GENERIC

                is SecurityException ->
                    applicationContext.getString(R.string.start_failed_no_auth) to
                        StartFailureKind.GENERIC

                else -> {
                    // Safety net: an adbd path we haven't mapped can still surface the
                    // TLS rejection as a plain SSL string. Never show that raw.
                    val text = e.localizedMessage.orEmpty()
                    if (text.contains("CERTIFICATE_UNKNOWN", true) ||
                        text.contains("CERTIFICATE_VERIFY_FAILED", true) ||
                        text.contains("SSLV3_ALERT", true)
                    ) {
                        applicationContext.getString(R.string.start_failed_pairing_required) to
                            StartFailureKind.PAIRING
                    } else {
                        (e.localizedMessage ?: e.javaClass.simpleName) to
                            StartFailureKind.GENERIC
                    }
                }
            }
            StartStatusReporter.failed(message, kind)

            val ignored = listOf(
                EOFException::class,
                SecurityException::class,
                TimeoutException::class,
                // The app already shows this with a Pair action; a bug-report
                // notification would just be noise.
                AdbPairingRequiredException::class
            )
            if (ignored.none { it.isInstance(e) }) showErrorNotification(applicationContext, e)

            if (ShizukuStateMachine.update() == ShizukuStateMachine.State.RUNNING) {
                return Result.success()
            } else {
                notify(WorkerState.AWAITING_RETRY)
                return Result.retry()
            }
        }
    }

    private fun showErrorNotification(context: Context, e: Exception) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val nb = NotificationCompat.Builder(context, CHANNEL_ID)

        val msgNotif = "$e. ${context.getString(R.string.wadb_error_notify_dev)}"

        val intent = Intent(context, BugReportDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = nb
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_error_title))
            .setContentText(msgNotif)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msgNotif))
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_START_METHOD = "start_method"

        fun enqueue(
            context: Context,
            startMethod: Int = ShizukuSettings.StartMethod.WIRELESS,
            immediate: Boolean = false
        ) {
            val usbMethod = startMethod == ShizukuSettings.StartMethod.USB
            val cb = Constraints.Builder()
            // `immediate` is for user-initiated starts (manual broadcast, GUI button):
            // they shouldn't wait on unmetered Wi-Fi like unattended auto-restarts do,
            // since the discovery flow works without any network connection. A USB
            // start never wants a network constraint at all.
            // A wireless start always needs a network to keep wireless debugging
            // alive; the USB method uses the classic port and needs none.
            if (!usbMethod && !immediate)
                cb.setRequiredNetworkType(NetworkType.UNMETERED)
            val constraints = cb.build()

            val inputData = workDataOf(KEY_START_METHOD to startMethod)

            val request = OneTimeWorkRequestBuilder<AdbStartWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "adb_start_worker",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
        const val CHANNEL_ID = "AdbStartWorker"
        const val NOTIFICATION_ID = 1448
    }
}