package com.ponkutools.gameassistant.ocr

import com.ponkutools.gameassistant.tasks.OcrSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextSimilarityTest {
    @Test
    fun toleratesTypicalOcrCharacterErrors() {
        assertTrue(OcrTextSimilarity.partialSimilarity("素村探素", "素材探索") >= 0.5f)
        assertTrue(OcrTextSimilarity.partialSimilarity("EXCEEDP0INTS", "EXCEEDPOINTS") >= 0.8f)
    }

    @Test
    fun samePageWithOcrNoiseRemainsSimilar() {
        val before = snapshot("素材探索", "探索1", "立即返回", "全部回收")
        val after = snapshot("素村探素", "探索I", "立即返同", "全部回收")
        assertTrue(OcrTextSimilarity.snapshotSimilarity(before, after) >= 0.72f)
    }

    @Test
    fun differentPagesHaveLowSimilarity() {
        val before = snapshot("素材探索", "探索1", "立即返回", "全部回收")
        val after = snapshot("确认购买", "已购买以下道具", "原神石免费", "OK")
        assertTrue(OcrTextSimilarity.snapshotSimilarity(before, after) < 0.72f)
    }

    private fun snapshot(vararg lines: String) = OcrSnapshot(
        candidates = lines.mapIndexed { index, text ->
            OcrCandidate(text, Bounds(0, index * 20, 200, index * 20 + 18), CandidateLevel.LINE)
        },
        screenWidth = 540,
        screenHeight = 960,
    )
}
