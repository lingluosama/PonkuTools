package com.ponkutools.gameassistant.ocr

enum class CandidateLevel {
    ELEMENT,
    LINE,
}

data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class OcrCandidate(
    val text: String,
    val bounds: Bounds,
    val level: CandidateLevel,
    val enhanced: Boolean = false,
)

