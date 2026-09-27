package moe.shizuku.manager.start

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
