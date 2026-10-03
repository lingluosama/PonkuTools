package com.ponkutools.gameassistant.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AutomationRuntimeState {
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()
    private val mutableTaskActive = MutableStateFlow(false)
    val taskActive = mutableTaskActive.asStateFlow()
    private val mutablePaused = MutableStateFlow(false)
    val paused = mutablePaused.asStateFlow()

    fun setRunning(value: Boolean) {
        mutableRunning.value = value
    }

    fun setTaskState(active: Boolean, paused: Boolean) {
        mutableTaskActive.value = active
        mutablePaused.value = paused
    }
}

