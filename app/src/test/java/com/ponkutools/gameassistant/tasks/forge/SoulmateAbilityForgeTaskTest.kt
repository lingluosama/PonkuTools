package com.ponkutools.gameassistant.tasks.forge

import com.ponkutools.gameassistant.ocr.Bounds
import com.ponkutools.gameassistant.ocr.CandidateLevel
import com.ponkutools.gameassistant.ocr.OcrCandidate
import com.ponkutools.gameassistant.tasks.AutomationSession
import com.ponkutools.gameassistant.tasks.OcrSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SoulmateAbilityForgeTaskTest {
    @Test
    fun usesCalibratedRelativeLayoutCoordinates() {
        assertEquals(296, SoulmateForgeLayout.slotCenters[0].x)
        assertEquals(396, SoulmateForgeLayout.slotCenters[0].y)
        assertEquals(178, SoulmateForgeLayout.slotCenters[1].x)
        assertEquals(450, SoulmateForgeLayout.slotCenters[1].y)
        assertEquals(256, SoulmateForgeLayout.slotCenters[2].x)
        assertEquals(510, SoulmateForgeLayout.slotCenters[2].y)
        assertEquals(460, SoulmateForgeLayout.forgeDecisionPoint.x)
        assertEquals(834, SoulmateForgeLayout.forgeDecisionPoint.y)
        assertEquals(361, SoulmateForgeLayout.resultDecisionPoint.x)
        assertEquals(855, SoulmateForgeLayout.resultDecisionPoint.y)
        assertEquals(382, SoulmateForgeLayout.confirmationDecisionPoint.x)
        assertEquals(690, SoulmateForgeLayout.confirmationDecisionPoint.y)
        assertEquals(225, SoulmateForgeLayout.bookPoint(ForgeAttributeCategory.ATTACK, ForgeBookTier.GOLD).x)
        assertEquals(668, SoulmateForgeLayout.bookPoint(ForgeAttributeCategory.ATTACK, ForgeBookTier.GOLD).y)
        assertEquals(400, SoulmateForgeLayout.bookPoint(ForgeAttributeCategory.DEFENSE, ForgeBookTier.SILVER).x)
        assertEquals(660, SoulmateForgeLayout.bookPoint(ForgeAttributeCategory.DEFENSE, ForgeBookTier.SILVER).y)
        assertEquals(481, SoulmateForgeLayout.bookPoint(ForgeAttributeCategory.OTHER, ForgeBookTier.GOLD).x)
        assertEquals(763, SoulmateForgeLayout.bookPoint(ForgeAttributeCategory.OTHER, ForgeBookTier.GOLD).y)
    }

    @Test
    fun parsesStatsAlignedOnTheSameRowsAsResultLabels() {
        val snapshot = OcrSnapshot(
            candidates = listOf(
                line("抽 前", 45, 400, 105, 425),
                line("MATK +750", 150, 395, 275, 420, enhanced = true),
                line("抽 后", 45, 610, 105, 635),
                line("能力 +129%", 150, 605, 360, 630, enhanced = true),
                line("重新抽取", 80, 850, 190, 880),
                line("决定", 410, 850, 480, 880),
            ),
            screenWidth = 540,
            screenHeight = 960,
        )

        val result = SoulmateAbilityForgeTask(ForgeConfig()).parseResult(snapshot)

        assertNotNull(result)
        assertEquals("matk", result?.before?.stat?.definition?.id)
        assertEquals(750, result?.before?.stat?.value)
        assertEquals("ability_damage_up", result?.after?.stat?.definition?.id)
        assertEquals(12, result?.after?.stat?.value)
    }

    @Test
    fun treatsMissingSlotTextAsEmptyAndUnlockTextAsLocked() {
        val snapshot = OcrSnapshot(
            candidates = listOf(
                line("神绊技能伤害上限提升+8%", 60, 385, 330, 415),
                line("解锁", 400, 495, 470, 525),
            ),
            screenWidth = 540,
            screenHeight = 960,
        )

        val slots = SoulmateAbilityForgeTask(ForgeConfig()).parseSlots(snapshot)

        assertEquals("bond_cap_up", (slots[0] as ForgeSlotState.Unlocked).stat.definition.id)
        assertSame(ForgeSlotState.Empty, slots[1])
        assertSame(ForgeSlotState.Locked, slots[2])
    }

    @Test
    fun parsesRollResultWhenTheBeforeSlotIsEmpty() {
        val snapshot = OcrSnapshot(
            candidates = listOf(
                line("抽取前", 45, 400, 105, 425),
                line("抽取后", 45, 610, 105, 635),
                line("ATK +1200", 150, 605, 300, 630, enhanced = true),
                line("重新抽取", 80, 850, 190, 880),
                line("决定", 410, 850, 480, 880),
            ),
            screenWidth = 540,
            screenHeight = 960,
        )

        val result = SoulmateAbilityForgeTask(ForgeConfig()).parseResult(snapshot)

        assertNotNull(result)
        assertNull(result?.before)
        assertEquals("atk", result?.after?.stat?.definition?.id)
        assertEquals(1200, result?.after?.stat?.value)
    }

    @Test
    fun noisyAbilityCapRollStillCompletesTheConfiguredTarget() {
        val snapshot = OcrSnapshot(
            candidates = listOf(
                line("抽取前", 45, 244, 103, 265),
                line("D能力伤害上限提是升 +5%", 50, 361, 311, 382, enhanced = true),
                line("抽取后", 46, 543, 105, 562),
                line("能カ伤害土限提升 +9%", 55, 659, 310, 679, enhanced = true),
            ),
            screenWidth = 540,
            screenHeight = 960,
        )
        val config = ForgeSlotConfig(
            category = ForgeAttributeCategory.OTHER,
            targetStatId = "ability_cap_up",
        ).withThreshold("ability_cap_up", 7)

        val result = SoulmateAbilityForgeTask(ForgeConfig()).parseResult(snapshot)

        assertNotNull(result)
        assertEquals("ability_cap_up", result?.after?.stat?.definition?.id)
        assertEquals(9, result?.after?.stat?.value)
        assertEquals(
            ForgeResultAction.ACCEPT_AND_COMPLETE,
            ForgeDecisionEngine.decide(result?.before?.stat, requireNotNull(result).after.stat, config),
        )
    }

    @Test
    fun exhaustedGoldBookFallsBackToSilverAfterResultTimeout() = runBlocking {
        val pendingPage = mainPage("能力伤害上限提升 +6%")
        val satisfiedPage = mainPage("能力伤害上限提升 +7%")
        val acceptedResult = resultPage(
            before = "能力伤害上限提升 +6%",
            after = "能力伤害上限提升 +7%",
        )
        val session = ScriptedSession(
            required = MutableList(7) { pendingPage }.apply { add(acceptedResult) },
            optional = mutableListOf(null, acceptedResult, satisfiedPage),
        )
        val slot = ForgeSlotConfig(
            category = ForgeAttributeCategory.OTHER,
            targetStatId = "ability_cap_up",
            bookPolicy = ForgeBookPolicy.GOLD_THEN_SILVER,
        ).withThreshold("ability_cap_up", 7)
        val task = SoulmateAbilityForgeTask(
            ForgeConfig(listOf(slot, ForgeSlotConfig(enabled = false), ForgeSlotConfig(enabled = false))),
        )

        task.run(session)

        assertTrue(session.taps.any { it.contains("选择其他金书") })
        assertTrue(session.taps.any { it.contains("选择其他银书") })
        assertTrue(session.logs.any { it.contains("金书判定为耗尽") })
        assertEquals(20_000L, session.optionalCalls.first().second)
    }

    @Test
    fun retryConfirmationTimeoutPreservesOldResultAndSkipsSingleTierPolicy() = runBlocking {
        val pendingPage = mainPage("能力伤害上限提升 +6%")
        val rejectedResult = resultPage(
            before = "能力伤害上限提升 +6%",
            after = "CTR率 +2%",
        )
        val session = ScriptedSession(
            required = MutableList(4) { pendingPage }.apply { add(pendingPage) },
            optional = mutableListOf(rejectedResult, null, rejectedResult, pendingPage),
        )
        val slot = ForgeSlotConfig(
            category = ForgeAttributeCategory.OTHER,
            targetStatId = "ability_cap_up",
            bookPolicy = ForgeBookPolicy.GOLD_ONLY,
        ).withThreshold("ability_cap_up", 7)
        val task = SoulmateAbilityForgeTask(
            ForgeConfig(listOf(slot, ForgeSlotConfig(enabled = false), ForgeSlotConfig(enabled = false))),
        )

        task.run(session)

        assertEquals(10_000L, session.optionalCalls[1].second)
        assertTrue(session.taps.any { it.contains("保留抽取前属性") })
        assertTrue(session.taps.any { it.contains("保存旧属性") })
        assertTrue(session.logs.any { it.contains("点击重新抽取后 10 秒未出现确定按钮") })
        assertTrue(session.logs.any { it.contains("跳过该词条任务") })
        assertTrue(session.taps.none { it.contains("银书") })
    }

    private fun mainPage(slotText: String) = OcrSnapshot(
        candidates = listOf(
            line("魂友能力锻造", 20, 90, 190, 125),
            line(slotText, 60, 385, 360, 415),
            line("持有道具一览", 35, 555, 180, 585),
            line("决定", 430, 820, 500, 855),
        ),
        screenWidth = 540,
        screenHeight = 960,
    )

    private fun resultPage(before: String, after: String) = OcrSnapshot(
        candidates = listOf(
            line("抽取前", 45, 244, 110, 270),
            line(before, 55, 350, 330, 385, enhanced = true),
            line("抽取后", 45, 530, 110, 565),
            line(after, 55, 650, 330, 685, enhanced = true),
            line("重新抽取", 65, 820, 205, 865),
            line("决定", 400, 820, 500, 865),
        ),
        screenWidth = 540,
        screenHeight = 960,
    )

    private class ScriptedSession(
        private val required: MutableList<OcrSnapshot>,
        private val optional: MutableList<OcrSnapshot?>,
    ) : AutomationSession {
        val logs = mutableListOf<String>()
        val taps = mutableListOf<String>()
        val optionalCalls = mutableListOf<Pair<String, Long>>()

        override fun log(message: String) {
            logs += message
        }

        override suspend fun awaitReady() = Unit

        override suspend fun recognizeValid(
            stateName: String,
            validator: (OcrSnapshot) -> Boolean,
        ): OcrSnapshot {
            val snapshot = required.removeAt(0)
            assertTrue("$stateName should accept scripted page", validator(snapshot))
            return snapshot
        }

        override suspend fun recognizeOptional(
            stateName: String,
            timeoutMs: Long,
            validator: (OcrSnapshot) -> Boolean,
        ): OcrSnapshot? {
            optionalCalls += stateName to timeoutMs
            val snapshot = optional.removeAt(0)
            if (snapshot != null) assertTrue("$stateName should accept scripted page", validator(snapshot))
            return snapshot
        }

        override suspend fun tap(x: Int, y: Int, description: String) {
            taps += description
        }

    }

    private fun line(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        enhanced: Boolean = false,
    ) = OcrCandidate(text, Bounds(left, top, right, bottom), CandidateLevel.LINE, enhanced)
}
