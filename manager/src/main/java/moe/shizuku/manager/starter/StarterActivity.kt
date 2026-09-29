package moe.shizuku.manager.starter

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLProtocolException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbPairingHelper
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.databinding.StarterActivityBinding
import rikka.lifecycle.Resource
import rikka.lifecycle.Status

private class NotRootedException: Exception()

class StarterActivity : AppBarActivity() {

    private val viewModel: ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_24)

        val binding = StarterActivityBinding.inflate(layoutInflater, rootView, true)

        viewModel.output.observe(this) {
            val output = it.data!!.trim()
            if (output.endsWith(Starter.serviceStartedMessage)) {
                window?.decorView?.postDelayed({
                    if (!isFinishing) finish()
                }, 3000)
            } else if (it.status == Status.ERROR) {
                var message = 0
                when (it.error) {
                    is AdbKeyException -> {
                        message = R.string.adb_error_key_store
                    }
                    is NotRootedException -> {
                        message = R.string.start_with_root_failed
                    }
                    is SocketTimeoutException -> {
                        message = R.string.cannot_connect_port
                    }
                    is ConnectException -> {
                        message = R.string.cannot_connect_port
                    }
                    is SSLProtocolException -> {
                        // Not paired yet: run the pairing flow automatically instead of
                        // failing and asking the user to pair manually.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            AdbPairingHelper.handlePairing(this@StarterActivity)
                            finish()
                            return@observe
                        } else {
                            message = R.string.adb_pair_required
                        }
                    }
                }

                if (message != 0) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            binding.text1.text = output
        }
    }

    private var hasStarted = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasStarted) {
            hasStarted = true
            viewModel.start(
                intent.getBooleanExtra(EXTRA_IS_ROOT, false),
                intent.getBooleanExtra(EXTRA_IS_SYSTEM, false),
                intent.getIntExtra(EXTRA_PORT, 0),
                intent.getBooleanExtra(EXTRA_SYSTEM_CUSTOM, false)
            )
        }
    }

    companion object {

        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_IS_SYSTEM = "$EXTRA.IS_SYSTEM"
        const val EXTRA_SYSTEM_CUSTOM = "$EXTRA.SYSTEM_CUSTOM"
        const val EXTRA_PORT = "$EXTRA.PORT"
    }
}

class ViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val sb = StringBuilder()
    private val _output = MutableLiveData<Resource<StringBuilder>>()

    val output = _output as LiveData<Resource<StringBuilder>>

    private val handler = CoroutineExceptionHandler { _, throwable ->
        ShizukuStateMachine.update()
        log(error = throwable)
    }

    private var started = false

    fun start(root: Boolean, isSystem: Boolean, port: Int, systemCustom: Boolean = false) {
        if (started) return
        started = true

        // Recorded here too: this activity is an entry point of its own (the root and
        // system rows start it directly), and the card reports the method that started
        // the running server.
        ShizukuSettings.setRunningStartMethod(
            when {
                root -> ShizukuSettings.StartMethod.ROOT
                isSystem -> ShizukuSettings.StartMethod.SYSTEM
                else -> ShizukuSettings.StartMethod.USB
            }
        )

        viewModelScope.launch(handler) {
            // Whether there is anything to wait for: a system start whose exploit target
            // this device does not ship has already said so, and waiting a minute for a
            // service nobody asked for only hides that.
            val waiting = when {
                root -> { startRoot(); true }
                isSystem && systemCustom -> { startSystemCustom(); true }
                isSystem -> startSys()
                else -> { AdbStarter.startAdb(appContext, port, { log(it) }); true }
            }
            if (waiting) Starter.waitForBinder({ log(it) })
        }
    }

    /**
     * Launches the Shizuku server under the system UID (1000) by abusing a
     * device-specific privilege escalation. This only works on devices that ship
     * the vulnerable component, and is opt-in via the "System start method"
     * setting.
     */
    private suspend fun startSys(): Boolean {
        log("Starting with system...\n")

        return withContext(Dispatchers.IO) {
            try {
                appContext.startActivity(
                    Intent().apply {
                        setClassName("com.sdet.fotaagent", "com.sdet.fotaagent.Main")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )

                val exploit = Intent("com.sdet.fotaagent.intent.CP_FILE").apply {
                    putExtra("CP_FILE", "/data")
                    putExtra(
                        "CP_LOC",
                        "; " + appContext.applicationInfo.nativeLibraryDir + "/libshizuku.so" +
                            "; am force-stop com.sdet.fotaagent"
                    )
                }

                Thread.sleep(1000)
                appContext.sendBroadcast(exploit)
                log("Start system success!\n")
                true
            } catch (e: ActivityNotFoundException) {
                // The exploit only exists where the device ships the component it abuses,
                // and a phone that no longer does has nothing to start: say which component
                // is missing and which method does not need it, instead of reporting a
                // success that never happened and then waiting for it.
                val message = appContext.getString(R.string.start_failed_system_no_exploit)
                log(message)
                withContext(Dispatchers.Main) { StartStatusReporter.failed(message) }
                false
            } catch (e: Throwable) {
                log("Start system failed!", e)
                false
            }
        }
    }

    /**
     * The instruction path for the system start: this app has no privilege of its own, so
     * it says what to run and waits for the service to appear. Whatever can launch a
     * process as a privileged uid - the user's own exploit, an automation, a root shell -
     * runs the executable this app ships, and the wait is the same one the other methods
     * use.
     */
    private suspend fun startSystemCustom() {
        val command = systemStarterCommand()

        log(appContext.getString(R.string.start_system_custom_intro))
        log(command)

        val copied = withContext(Dispatchers.Main) {
            val clipboard =
                appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@withContext false
            clipboard.setPrimaryClip(ClipData.newPlainText("Shizuku", command))
            true
        }

        log(
            appContext.getString(
                if (copied) R.string.start_system_custom_copied
                else R.string.start_system_custom_copy_failed
            )
        )
    }

    /**
     * The command resolves the installed APK's own lib directory at run time, so it keeps
     * working after an update moves it, and it names this package rather than a fixed one,
     * which matters for stealth mode.
     */
    private fun systemStarterCommand(): String {
        val packageName = appContext.packageName
        return "P=\$(pm path $packageName" +
            " | sed -E 's|^package:(.*/)[^/]+\\.apk\$|\\1|')" +
            " && \${P}lib/arm64/libshizuku.so"
    }

    private fun log(line: String? = null, error: Throwable? = null) {
        line?.let { sb.appendLine(it) }
        error?.let { sb.appendLine().appendLine(Log.getStackTraceString(it)) }

        if (error == null) _output.postValue(Resource.success(sb))
        else _output.postValue(Resource.error(error, sb))
    }

    private suspend fun startRoot() {
        log("Starting with root...\n")

        return withContext(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                // Try again just in case
                Shell.getCachedShell()?.close()

                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    throw NotRootedException()
                }
            }

            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            suspendCancellableCoroutine { cont ->
                Shell.cmd(Starter.internalCommand)
                    .to(object : CallbackList<String?>() {
                        override fun onAddElement(s: String?) { s?.let { log(it) } }
                    })
                    .submit {
                        if (it.isSuccess) {
                            cont.resume(Unit)
                        } else {
                            cont.resumeWithException(Exception("Failed to start with root"))
                        }
                    }
            }
        }
    }
    
}
