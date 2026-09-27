package moe.shizuku.manager.start

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings

/** What the user can do about a failed start, if anything. */
enum class StartFailureKind {
    GENERIC,

    /** Wireless debugging can't be enabled without a Wi-Fi connection. */
    WIFI,

    /** The ADB TLS handshake was rejected: the device needs pairing. */
    PAIRING
}

/** Outcome of the most recent start attempt, surfaced in the manager UI. */
sealed interface StartStatus {
    data object Idle : StartStatus
    data object Starting : StartStatus
    data object Succeeded : StartStatus
    data class Failed(
        val message: String,
        val kind: StartFailureKind = StartFailureKind.GENERIC
    ) : StartStatus
}

/**
 * A start usually runs in the background (AdbStartWorker), so failures used to be
 * invisible in the app. Both the starter and the worker report here and the home
 * screen shows the reason (and a fix action where one exists).
 */
/** Display name of a start method, shared by the home and settings screens. */
@StringRes
fun startMethodLabelRes(@ShizukuSettings.StartMethod method: Int): Int = when (method) {
    ShizukuSettings.StartMethod.USB -> R.string.start_method_usb
    ShizukuSettings.StartMethod.SYSTEM -> R.string.start_method_system
    ShizukuSettings.StartMethod.ROOT -> R.string.start_method_root
    else -> R.string.start_method_wireless
}

object StartStatusReporter {
    private val _status = MutableStateFlow<StartStatus>(StartStatus.Idle)
    val status: StateFlow<StartStatus> = _status.asStateFlow()

    fun starting() {
        _status.value = StartStatus.Starting
    }

    fun succeeded() {
        _status.value = StartStatus.Succeeded
    }

    fun failed(message: String, kind: StartFailureKind = StartFailureKind.GENERIC) {
        _status.value = StartStatus.Failed(message, kind)
    }

    fun clear() {
        _status.value = StartStatus.Idle
    }
}
