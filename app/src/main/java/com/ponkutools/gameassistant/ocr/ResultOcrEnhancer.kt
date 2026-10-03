package com.ponkutools.gameassistant.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.ponkutools.gameassistant.automation.CoordinateMapper

data class EnhancedOcrRegion(
    val bitmap: Bitmap,
    private val offsetX: Int,
    private val offsetY: Int,
    private val scale: Int,
) : AutoCloseable {
    fun map(candidate: OcrCandidate): OcrCandidate = candidate.copy(
        bounds = Bounds(
            offsetX + candidate.bounds.left / scale,
            offsetY + candidate.bounds.top / scale,
            offsetX + candidate.bounds.right / scale,
            offsetY + candidate.bounds.bottom / scale,
        ),
        enhanced = true,
    )

    override fun close() = bitmap.recycle()
}

object ResultOcrEnhancer {
    fun create(source: Bitmap, screenWidth: Int, screenHeight: Int): List<EnhancedOcrRegion> =
        REGIONS.map { region ->
            val mapped = CoordinateMapper.designBoundsToScreen(region, screenWidth, screenHeight)
            val left = mapped.left.toInt().coerceIn(0, source.width - 1)
            val top = mapped.top.toInt().coerceIn(0, source.height - 1)
            val right = mapped.right.toInt().coerceIn(left + 1, source.width)
            val bottom = mapped.bottom.toInt().coerceIn(top + 1, source.height)
            val crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
            val scaled = Bitmap.createScaledBitmap(crop, crop.width * SCALE, crop.height * SCALE, true)
            val enhanced = enhance(scaled)
            scaled.recycle()
            crop.recycle()
            EnhancedOcrRegion(enhanced, left, top, SCALE)
        }

    private fun enhance(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val translate = 128f * (1f - CONTRAST)
        grayscale.postConcat(
            ColorMatrix(
                floatArrayOf(
                    CONTRAST, 0f, 0f, 0f, translate,
                    0f, CONTRAST, 0f, 0f, translate,
                    0f, 0f, CONTRAST, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                source,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(grayscale)
                },
            )
        }
        return output
    }

    private const val SCALE = 3
    private const val CONTRAST = 1.55f
    private val REGIONS = listOf(
        Bounds(35, 275, 505, 500),
        Bounds(35, 570, 505, 790),
    )
}
