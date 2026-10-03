package com.ponkutools.gameassistant.ocr

import com.ponkutools.gameassistant.tasks.OcrSnapshot
import com.ponkutools.gameassistant.tasks.forge.ForgeParser
import kotlin.math.max

object OcrTextSimilarity {
    fun normalize(text: String): String = ForgeParser.normalize(text)
        .replace("0", "O")
        .replace(Regex("[^A-Z0-9\u4E00-\u9FFF]"), "")

    fun similarity(left: String, right: String): Float {
        val a = normalize(left)
        val b = normalize(right)
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a.contains(b) || b.contains(a)) {
            return minOf(a.length, b.length).toFloat() / max(a.length, b.length)
        }
        val edit = 1f - levenshtein(a, b).toFloat() / max(a.length, b.length)
        val dice = ngramDice(a, b)
        return max(edit, dice)
    }

    fun partialSimilarity(observed: String, expected: String): Float {
        val source = normalize(observed)
        val target = normalize(expected)
        if (source.isEmpty() || target.isEmpty()) return 0f
        if (source.contains(target)) return 1f
        if (target.contains(source)) return source.length.toFloat() / target.length

        val lengthDelta = max(2, target.length / 3)
        var best = similarity(source, target)
        val minLength = max(1, target.length - lengthDelta)
        val maxLength = minOf(source.length, target.length + lengthDelta)
        for (windowLength in minLength..maxLength) {
            for (start in 0..source.length - windowLength) {
                best = max(best, similarity(source.substring(start, start + windowLength), target))
            }
        }
        return best
    }

    fun snapshotSimilarity(before: OcrSnapshot, after: OcrSnapshot): Float {
        val left = normalizedLines(before)
        val right = normalizedLines(after)
        if (left.isEmpty() && right.isEmpty()) return 1f
        if (left.isEmpty() || right.isEmpty()) return 0f
        return minOf(directedLineSimilarity(left, right), directedLineSimilarity(right, left))
    }

    private fun normalizedLines(snapshot: OcrSnapshot): List<String> = snapshot.lines
        .map { normalize(it.text) }
        .filter { line -> line.length >= 2 && line.any { it.isLetter() } }
        .distinct()

    private fun directedLineSimilarity(source: List<String>, target: List<String>): Float {
        var weightedScore = 0f
        var totalWeight = 0
        source.forEach { line ->
            val weight = line.length.coerceAtMost(16)
            val best = target.maxOfOrNull { candidate -> similarity(line, candidate) } ?: 0f
            weightedScore += best * weight
            totalWeight += weight
        }
        return if (totalWeight == 0) 0f else weightedScore / totalWeight
    }

    private fun ngramDice(left: String, right: String): Float {
        val size = if (minOf(left.length, right.length) >= 4) 2 else 1
        val leftCounts = ngramCounts(left, size)
        val rightCounts = ngramCounts(right, size)
        var intersection = 0
        leftCounts.forEach { (gram, count) ->
            intersection += minOf(count, rightCounts[gram] ?: 0)
        }
        val total = leftCounts.values.sum() + rightCounts.values.sum()
        return if (total == 0) 0f else 2f * intersection / total
    }

    private fun ngramCounts(text: String, size: Int): Map<String, Int> = buildMap {
        if (text.length < size) {
            put(text, 1)
        } else {
            for (index in 0..text.length - size) {
                val gram = text.substring(index, index + size)
                put(gram, (get(gram) ?: 0) + 1)
            }
        }
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        left.forEachIndexed { leftIndex, leftChar ->
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
