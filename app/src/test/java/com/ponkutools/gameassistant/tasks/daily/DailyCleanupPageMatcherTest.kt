package com.ponkutools.gameassistant.tasks.daily

import com.ponkutools.gameassistant.ocr.Bounds
import com.ponkutools.gameassistant.ocr.CandidateLevel
import com.ponkutools.gameassistant.ocr.OcrCandidate
import com.ponkutools.gameassistant.tasks.OcrSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCleanupPageMatcherTest {
    @Test
    fun acceptsHomeWithSplitKeywords() {
        assertTrue(matches(DailyPage.HOME, "尊 荣 卡 任务"))
    }

    @Test
    fun acceptsLevelWithPartialAnchors() {
        assertTrue(matches(DailyPage.LEVEL, "故事 活动"))
    }

    @Test
    fun acceptsBattleExploreOuteQuizAndShopAnchors() {
        assertTrue(matches(DailyPage.BATTLE_SKIP, "仗魂争霸 挑战次数 掉落奖励"))
        assertTrue(matches(DailyPage.EXPLORE, "素材 探索1"))
        assertTrue(matches(DailyPage.OUTE, "距离下个阶段还有 立即领取的话"))
        assertTrue(matches(DailyPage.QUIZ, "注意 猜谜"))
        assertTrue(matches(DailyPage.SHOP, "特别 道具"))
    }

    @Test
    fun rejectsUnrelatedPage() {
        assertFalse(matches(DailyPage.SHOP, "故事关卡 练习室"))
    }

    @Test
    fun doesNotTreatSharedBottomNavigationAsHome() {
        assertFalse(matches(DailyPage.HOME, "收藏 探索 武器关卡 故事关卡"))
    }

    private fun matches(page: DailyPage, text: String): Boolean =
        DailyCleanupPageMatcher.matches(
            OcrSnapshot(
                listOf(OcrCandidate(text, Bounds(0, 0, 100, 40), CandidateLevel.LINE)),
                540,
                960,
            ),
            page,
        )
}
