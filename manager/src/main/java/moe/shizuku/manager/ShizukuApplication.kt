package moe.shizuku.manager

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.manage.Hiding
import moe.shizuku.manager.service.HidingWatchService
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.ShizukuStateMachine
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class ShizukuApplication : Application() {

    companion object {

        init {
            logd("ShizukuApplication", "init")

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }

        lateinit var application: ShizukuApplication
            private set

        lateinit var appContext: Context
            private set

    }

    private fun init(context: Context) {
        ShizukuSettings.initialize(context)
        // The starter writes its own log into this app's external directories when a device
        // exploit runs it, and they are only created on first use: create both now, so the
        // paths exist before anything tries to write to them. Without this a start that
        // failed had nowhere to leave its account of itself. The media directory matters
        // most, since that is the one another app's process is allowed to write.
        runCatching { getExternalFilesDir(null)?.mkdirs() }
        runCatching { getExternalMediaDirs()?.firstOrNull()?.mkdirs() }
        // The preference is the source of truth, so re-apply it to the boot receiver here:
        // installs from before this read the component back and can be stuck disabled with
        // start on boot switched on.
        ShizukuSettings.updateBootReceiver(context)
        LocaleDelegate.defaultLocale = ShizukuSettings.getLocale()
        AppCompatDelegate.setDefaultNightMode(ShizukuSettings.getNightMode())

        if(ShizukuSettings.getWatchdog()) WatchdogService.start(context)

        // The watch is the thing that puts the settings back, so a process that died while
        // something was hidden starts it again rather than leaving the device lying with nothing
        // left to notice the app it was hiding for is gone. Off the main thread, because it asks
        // the shell, and the platform may refuse a foreground service started from the
        // background - which is what the start it does is for.
        if (Hiding.hasAnyApp() && !Hiding.isPaused()) {
            thread(name = "hiding-watch-restart") { HidingWatchService.refresh(context) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        appContext = applicationContext
        init(this)
    }

}
