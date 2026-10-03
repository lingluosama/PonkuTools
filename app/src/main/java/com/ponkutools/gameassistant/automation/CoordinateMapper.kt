package com.ponkutools.gameassistant.automation

import com.ponkutools.gameassistant.ocr.Bounds
import kotlin.math.min
import kotlin.math.roundToInt

data class ScreenPoint(val x: Int, val y: Int)

object CoordinateMapper {
    data class DesignViewport(
        val scale: Float,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    )

    data class RelativeTap(
        val rawX: Int,
        val rawY: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val designX: Int,
        val designY: Int,
        val normalizedX: Float,
        val normalizedY: Float,
    ) {
        fun message(): String =
            "坐标采集：原始=($rawX,$rawY)，屏幕=${screenWidth}×${screenHeight}，设计坐标=($designX,$designY)，相对=(${normalizedX.formatPercent()}%,${normalizedY.formatPercent()}%)"

        private fun Float.formatPercent(): String = "%.2f".format(java.util.Locale.US, this * 100f)
    }

    fun relativeTap(x: Int, y: Int, screenWidth: Int, screenHeight: Int): RelativeTap {
        require(screenWidth > 0 && screenHeight > 0)
        val viewport = designViewport(screenWidth, screenHeight)
        return RelativeTap(
            rawX = x.coerceIn(0, screenWidth - 1),
            rawY = y.coerceIn(0, screenHeight - 1),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            designX = ((x - viewport.left) / viewport.scale).roundToInt().coerceIn(0, DESIGN_WIDTH),
            designY = ((y - viewport.top) / viewport.scale).roundToInt().coerceIn(0, DESIGN_HEIGHT),
            normalizedX = x / screenWidth.toFloat(),
            normalizedY = y / screenHeight.toFloat(),
        )
    }

    fun designViewport(screenWidth: Int, screenHeight: Int): DesignViewport {
        val safeWidth = screenWidth.coerceAtLeast(1)
        val safeHeight = screenHeight.coerceAtLeast(1)
        val scale = min(
            safeWidth / DESIGN_WIDTH.toFloat(),
            safeHeight / DESIGN_HEIGHT.toFloat(),
        )
        val width = DESIGN_WIDTH * scale
        val height = DESIGN_HEIGHT * scale
        return DesignViewport(
            scale = scale,
            left = (safeWidth - width) / 2f,
            top = (safeHeight - height) / 2f,
            width = width,
            height = height,
        )
    }

    fun designToScreen(x: Int, y: Int, screenWidth: Int, screenHeight: Int): ScreenPoint {
        val viewport = designViewport(screenWidth, screenHeight)
        return ScreenPoint(
            x = (viewport.left + x * viewport.scale).roundToInt().coerceIn(0, screenWidth - 1),
            y = (viewport.top + y * viewport.scale).roundToInt().coerceIn(0, screenHeight - 1),
        )
    }

    fun designBoundsToScreen(
        bounds: Bounds,
        screenWidth: Int,
        screenHeight: Int,
    ): Bounds {
        val topLeft = designToScreen(bounds.left.toInt(), bounds.top.toInt(), screenWidth, screenHeight)
        val bottomRight = designToScreen(bounds.right.toInt(), bounds.bottom.toInt(), screenWidth, screenHeight)
        return Bounds(
            left = topLeft.x,
            top = topLeft.y,
            right = bottomRight.x,
            bottom = bottomRight.y,
        )
    }

    fun centerToScreen(
        bounds: Bounds,
        imageWidth: Int,
        imageHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): ScreenPoint {
        require(imageWidth > 0 && imageHeight > 0)
        require(screenWidth > 0 && screenHeight > 0)

        val scaledX = bounds.centerX * screenWidth / imageWidth
        val scaledY = bounds.centerY * screenHeight / imageHeight
        return ScreenPoint(
            scaledX.roundToInt().coerceIn(0, screenWidth - 1),
            scaledY.roundToInt().coerceIn(0, screenHeight - 1),
        )
    }

    const val DESIGN_WIDTH = 540
    const val DESIGN_HEIGHT = 960
}

