package com.ponkutools.gameassistant.tasks

import com.ponkutools.gameassistant.ocr.Bounds
import com.ponkutools.gameassistant.ocr.CandidateLevel
import com.ponkutools.gameassistant.ocr.OcrCandidate

data class OcrSnapshot(
    val candidates: List<OcrCandidate>,
    val screenWidth: Int,
    val screenHeight: Int,
) {
    val lines: List<OcrCandidate> get() = candidates.filter { it.level == CandidateLevel.LINE }
    val text: String get() = lines.joinToString("\n") { it.text }
}

/** Retry timing is task-specific: forge keeps its fast polling cadence while
 * daily page transitions get a longer loading window. */
data class OcrRetryPolicy(
    val maxAttempts: Int,
    val intervalMs: Long,
) {
    init {
        require(maxAttempts > 0)
        require(intervalMs >= 0)
    }

    companion object {
        // Preserve the existing forge cadence; daily loading gets its own
        // named policy so it can be tuned independently later.
        val FORGE = OcrRetryPolicy(maxAttempts = 5, intervalMs = 200L)
        val DAILY_PAGE = OcrRetryPolicy(maxAttempts = 10, intervalMs = 500L)
    }
}

data class VisualFingerprint(val samples: IntArray) {
    fun differenceRatio(other: VisualFingerprint): Float {
        if (samples.isEmpty() || other.samples.isEmpty()) return 1f
        val count = minOf(samples.size, other.samples.size)
        var changed = 0
        for (index in 0 until count) {
            val a = samples[index]
            val b = other.samples[index]
            val distance = kotlin.math.abs(((a shr 16) and 0xff) - ((b shr 16) and 0xff)) +
                kotlin.math.abs(((a shr 8) and 0xff) - ((b shr 8) and 0xff)) +
                kotlin.math.abs((a and 0xff) - (b and 0xff))
            if (distance >= 36) changed++
        }
        return changed.toFloat() / count
    }
}

interface AutomationSession {
    fun log(message: String)
    suspend fun awaitReady()
    suspend fun recognizeValid(
        stateName: String,
        validator: (OcrSnapshot) -> Boolean,
    ): OcrSnapshot

    suspend fun recognizeValidWithPolicy(
        stateName: String,
        retryPolicy: OcrRetryPolicy,
        validator: (OcrSnapshot) -> Boolean,
    ): OcrSnapshot = recognizeValid(stateName, validator)

    suspend fun recognizeOptional(
        stateName: String,
        timeoutMs: Long,
        validator: (OcrSnapshot) -> Boolean,
    ): OcrSnapshot?

    suspend fun tap(x: Int, y: Int, description: String)

    suspend fun dailyTapAndWait(x: Int, y: Int, description: String) {
        tap(x, y, description)
    }

    suspend fun tapVerified(x: Int, y: Int, description: String, retries: Int = 2): Boolean {
        tap(x, y, description)
        return true
    }

    /**
     * Verifies a click by comparing OCR text captured immediately before and
     * after it. Implementations may provide retries and overlay suppression.
     */
    suspend fun dailyTapOcrVerified(x: Int, y: Int, description: String, retries: Int = 2): Boolean {
        tap(x, y, description)
        return true
    }

    suspend fun tap(bounds: Bounds, description: String) =
        tap(bounds.centerX.toInt(), bounds.centerY.toInt(), description)
}

class AutomationHaltedException(message: String) : IllegalStateException(message)
