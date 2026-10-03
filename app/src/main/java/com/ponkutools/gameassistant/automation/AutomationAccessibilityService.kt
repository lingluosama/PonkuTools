package com.ponkutools.gameassistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.ponkutools.gameassistant.logging.AutomationLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutomationAccessibilityService : AccessibilityService() {
    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        mutableConnected.value = true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
        mutableConnected.value = true
        AutomationLogger.log(this, "无障碍点击服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        markDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        markDisconnected()
        super.onDestroy()
    }

    private fun markDisconnected() {
        if (currentInstance !== this) return
        currentInstance = null
        mutableConnected.value = false
        AutomationLogger.log(this, "无障碍点击服务已断开")
    }

    fun tap(x: Int, y: Int, callback: (Boolean) -> Unit) {
        press(x, y, TAP_DURATION_MS, callback)
    }

    fun press(x: Int, y: Int, durationMs: Long, callback: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L)))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback(false)
                }
            },
            null,
        )
        if (!accepted) callback(false)
    }

    companion object {
        private const val TAP_DURATION_MS = 80L

        @Volatile
        private var currentInstance: AutomationAccessibilityService? = null
        private val mutableConnected = MutableStateFlow(false)

        val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
        val current: AutomationAccessibilityService? get() = currentInstance
        val isConnected: Boolean get() = currentInstance != null
    }
}

