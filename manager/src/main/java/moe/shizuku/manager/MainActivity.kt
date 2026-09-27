package moe.shizuku.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import moe.shizuku.manager.home.showAccessibilityDialog
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.ui.ShizukuApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            ShizukuApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(AppConstants.EXTRA_START_SERVICE_VIA_WADB, false)) {
            ShizukuReceiverStarter.start(this, forceStart = true, userInitiated = true)
        }

        if (intent.getBooleanExtra(AppConstants.EXTRA_SHOW_PAIRING_DIALOG, false)) {
            showAccessibilityDialog()
        }
    }
}
