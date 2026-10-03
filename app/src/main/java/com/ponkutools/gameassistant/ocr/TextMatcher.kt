package com.ponkutools.gameassistant.ocr

object TextMatcher {
    fun findBest(candidates: List<OcrCandidate>, target: String): OcrCandidate? {
        val normalizedTarget = normalize(target)
        if (normalizedTarget.isEmpty()) return null

        return candidates
            .asSequence()
            .filter { normalize(it.text).contains(normalizedTarget) }
            .sortedWith(
                compareByDescending<OcrCandidate> { normalize(it.text) == normalizedTarget }
                    .thenByDescending { it.level == CandidateLevel.ELEMENT }
                    .thenBy { it.bounds.width.toLong() * it.bounds.height },
            )
            .firstOrNull()
    }

    internal fun normalize(value: String): String = value
        .filterNot(Char::isWhitespace)
        .lowercase()
}

