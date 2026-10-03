package com.ponkutools.gameassistant.tasks.forge

import com.ponkutools.gameassistant.ocr.Bounds
import com.ponkutools.gameassistant.ocr.CandidateLevel
import com.ponkutools.gameassistant.ocr.OcrCandidate
import com.ponkutools.gameassistant.tasks.OcrSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgePageMatcherTest {
    @Test
    fun acceptsObservedKatakanaMisrecognition() {
        assertTrue(ForgePageMatcher.isMainPage(snapshot(line("魂友能カ锻造", 30, 110, 147, 129))))
    }

    @Test
    fun acceptsIndividualTitleKeywordsWithoutFullPhrase() {
        assertTrue(ForgePageMatcher.isMainPage(snapshot(line("魂友", 30, 110, 90, 129))))
        assertTrue(ForgePageMatcher.isMainPage(snapshot(line("鍛造", 30, 110, 90, 129))))
        assertTrue(ForgePageMatcher.isMainPage(snapshot(line("能力", 30, 110, 90, 129))))
    }

    @Test
    fun acceptsMainPageFromIndependentAnchorsWhenTitleIsMissing() {
        assertTrue(
            ForgePageMatcher.isMainPage(
                snapshot(
                    line("能力锻造效果", 48, 322, 148, 338),
                    line("持有道具一览", 40, 570, 157, 593),
                    line("决定", 439, 830, 476, 846),
                ),
            ),
        )
    }

    @Test
    fun acceptsSplitAnchorKeywords() {
        assertTrue(
            ForgePageMatcher.isMainPage(
                snapshot(
                    line("能力 效果", 48, 322, 148, 338),
                    line("持有 道具", 40, 570, 157, 593),
                    line("决 定", 439, 830, 476, 846),
                ),
            ),
        )
    }

    @Test
    fun doesNotAcceptTitleQuotedInsideAppLogArea() {
        assertFalse(ForgePageMatcher.isMainPage(snapshot(line("OCR 魂友能力锻造", 40, 400, 400, 430))))
    }

    private fun snapshot(vararg candidates: OcrCandidate) = OcrSnapshot(candidates.toList(), 540, 960)

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrCandidate(text, Bounds(left, top, right, bottom), CandidateLevel.LINE)
}
