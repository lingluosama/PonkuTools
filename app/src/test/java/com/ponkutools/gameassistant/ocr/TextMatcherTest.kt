package com.ponkutools.gameassistant.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextMatcherTest {
    @Test
    fun exactElementWinsOverContainingLine() {
        val candidates = listOf(
            OcrCandidate("开始战斗 免费", Bounds(0, 0, 200, 60), CandidateLevel.LINE),
            OcrCandidate("开始战斗", Bounds(0, 0, 100, 60), CandidateLevel.ELEMENT),
        )

        assertEquals("开始战斗", TextMatcher.findBest(candidates, " 开始战斗 ")?.text)
    }

    @Test
    fun returnsNullWhenTargetIsMissing() {
        val candidates = listOf(
            OcrCandidate("返回", Bounds(0, 0, 100, 60), CandidateLevel.ELEMENT),
        )

        assertNull(TextMatcher.findBest(candidates, "确认"))
    }
}

