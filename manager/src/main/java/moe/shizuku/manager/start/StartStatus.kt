package moe.shizuku.manager.start

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Outcome of the most recent start attempt, surfaced in the manager UI. */
sealed interface StartStatus {
    data object Idle : StartStatus
    data object Starting : StartStatus
    data object Succeeded : StartStatus
    data class Failed(val message: String) : StartStatus
}

/**
 * A start usually runs in the background (AdbStartWorker), so failures used to be
 * invisible in the app. Both the starter and the worker report here and the home
 * screen shows the reason.
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

    fun failed(message: String) {
        _status.value = StartStatus.Failed(message)
    }

    fun clear() {
        _status.value = StartStatus.Idle
    }
}
