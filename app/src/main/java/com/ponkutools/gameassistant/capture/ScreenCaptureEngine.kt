package com.ponkutools.gameassistant.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import java.util.concurrent.atomic.AtomicBoolean

data class CapturedFrame(
    val bitmap: Bitmap,
    val screenWidth: Int,
    val screenHeight: Int,
)

class ScreenCaptureEngine(
    context: Context,
    private val mediaProjection: MediaProjection,
    private val onProjectionStopped: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val captureThread = HandlerThread("ponku-screen-capture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)
    private val callbackHandler = Handler(appContext.mainLooper)
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            callbackHandler.post(onProjectionStopped)
        }
    }

    init {
        mediaProjection.registerCallback(projectionCallback, captureHandler)
        createDisplay()
    }

    fun capture(callback: (Result<CapturedFrame>) -> Unit) {
        runCatching { ensureCurrentDimensions() }
            .onFailure {
                callbackHandler.post { callback(Result.failure(it)) }
                return
            }

        val reader = imageReader
        if (reader == null) {
            callbackHandler.post {
                callback(Result.failure(IllegalStateException("截图通道未初始化")))
            }
            return
        }

        val completed = AtomicBoolean(false)
        fun attempt(remainingAttempts: Int) {
            captureHandler.postDelayed({
                if (completed.get()) return@postDelayed
                val image = runCatching { reader.acquireLatestImage() }.getOrNull()
                if (image != null && completed.compareAndSet(false, true)) {
                    val result = runCatching { image.use { convertImage(it) } }
                    callbackHandler.post { callback(result) }
                } else if (remainingAttempts > 0) {
                    attempt(remainingAttempts - 1)
                } else if (completed.compareAndSet(false, true)) {
                    callbackHandler.post {
                        callback(Result.failure(IllegalStateException("等待屏幕图像超时")))
                    }
                }
            }, if (remainingAttempts == CAPTURE_ATTEMPTS) 0 else CAPTURE_RETRY_DELAY_MS)
        }
        attempt(CAPTURE_ATTEMPTS)
    }

    private fun createDisplay() {
        val metrics = realDisplayMetrics()
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "PonkuAutomationCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler,
        )
    }

    private fun ensureCurrentDimensions() {
        val metrics = realDisplayMetrics()
        if (metrics.widthPixels == width && metrics.heightPixels == height) return

        val replacement = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2,
        )
        virtualDisplay?.surface = replacement.surface
        virtualDisplay?.resize(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        imageReader?.close()
        imageReader = replacement
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi
    }

    @Suppress("DEPRECATION")
    private fun realDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = appContext.getSystemService(WindowManager::class.java)
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun convertImage(image: Image): CapturedFrame {
        val plane = image.planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val paddedBitmap = createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        paddedBitmap.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        if (cropped !== paddedBitmap) paddedBitmap.recycle()
        return CapturedFrame(cropped, width, height)
    }

    fun release() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection.unregisterCallback(projectionCallback)
        mediaProjection.stop()
        captureThread.quitSafely()
    }

    private companion object {
        const val CAPTURE_ATTEMPTS = 20
        const val CAPTURE_RETRY_DELAY_MS = 75L
    }
}

