package dev.honeyphone.app.core.honeypot

import dev.honeyphone.app.model.HoneypotStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process status bridge between the foreground listener service and the UI. */
object HoneypotRuntime {
    private val mutableStatus = MutableStateFlow(HoneypotStatus())
    val status = mutableStatus.asStateFlow()

    fun update(status: HoneypotStatus) {
        mutableStatus.value = status
    }
}
