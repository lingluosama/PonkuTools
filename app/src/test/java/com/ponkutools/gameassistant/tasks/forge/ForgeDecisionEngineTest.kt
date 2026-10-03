package com.ponkutools.gameassistant.tasks.forge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeDecisionEngineTest {
    private fun parsed(text: String) = requireNotNull(ForgeParser.parse(text))

    @Test
    fun newTargetReplacesCurrentNonTargetAndContinues() {
        val action = ForgeDecisionEngine.decide(
            current = parsed("MIND+15"),
            rolled = parsed("ATK+1200"),
            config = ForgeSlotConfig(category = ForgeAttributeCategory.ATTACK),
        )
        assertEquals(ForgeResultAction.ACCEPT_AND_CONTINUE, action)
    }

    @Test
    fun largerSameTargetContinuesBelowThreshold() {
        val action = ForgeDecisionEngine.decide(
            current = parsed("ATK+1000"),
            rolled = parsed("ATK+1200"),
            config = ForgeSlotConfig(category = ForgeAttributeCategory.ATTACK),
        )
        assertEquals(ForgeResultAction.ACCEPT_AND_CONTINUE, action)
    }

    @Test
    fun smallerSameTargetRetries() {
        val action = ForgeDecisionEngine.decide(
            current = parsed("ATK+1200"),
            rolled = parsed("ATK+1000"),
            config = ForgeSlotConfig(category = ForgeAttributeCategory.ATTACK),
        )
        assertEquals(ForgeResultAction.RETRY, action)
    }

    @Test
    fun thresholdMatchCompletes() {
        val action = ForgeDecisionEngine.decide(
            current = parsed("MIND+15"),
            rolled = parsed("ATK+1500"),
            config = ForgeSlotConfig(category = ForgeAttributeCategory.ATTACK),
        )
        assertEquals(ForgeResultAction.ACCEPT_AND_COMPLETE, action)
    }

    @Test
    fun qualifyingAlternativeCompletesTheSlot() {
        val config = ForgeSlotConfig(
            category = ForgeAttributeCategory.ATTACK,
            targetStatId = "atk",
            alternativeStatIds = listOf("matk"),
        )
        assertEquals(
            ForgeResultAction.ACCEPT_AND_COMPLETE,
            ForgeDecisionEngine.decide(parsed("ATK+1000"), parsed("MATK+1500"), config),
        )
        assertEquals("matk", ForgeDecisionEngine.matchedAcceptedStat(parsed("MATK+1500"), config)?.id)
    }

    @Test
    fun unqualifiedAlternativeDoesNotParticipateInSameTypeReplacement() {
        val config = ForgeSlotConfig(
            category = ForgeAttributeCategory.ATTACK,
            targetStatId = "atk",
            alternativeStatIds = listOf("matk"),
        )
        assertEquals(
            ForgeResultAction.RETRY,
            ForgeDecisionEngine.decide(parsed("ATK+1000"), parsed("MATK+1200"), config),
        )
    }

    @Test
    fun alternativesAreLimitedToTwoAndDoNotIncludePrimaryTarget() {
        val config = ForgeSlotConfig(
            category = ForgeAttributeCategory.ATTACK,
            targetStatId = "atk",
            alternativeStatIds = listOf("atk", "matk", "damage_up", "bond_damage_up"),
        )
        assertEquals(listOf("matk", "damage_up"), config.alternativeStats().map { it.id })
    }

    @Test
    fun emptySlotAcceptsAUsefulTargetAndContinues() {
        val action = ForgeDecisionEngine.decide(
            current = null,
            rolled = parsed("ATK+1200"),
            config = ForgeSlotConfig(category = ForgeAttributeCategory.ATTACK),
        )
        assertEquals(ForgeResultAction.ACCEPT_AND_CONTINUE, action)
    }

    @Test
    fun emptySlotRetriesANonTargetResult() {
        val action = ForgeDecisionEngine.decide(
            current = null,
            rolled = parsed("MIND+15"),
            config = ForgeSlotConfig(category = ForgeAttributeCategory.ATTACK),
        )
        assertEquals(ForgeResultAction.RETRY, action)
    }

    @Test
    fun bookPolicyUsesConfiguredFallback() {
        assertEquals(
            ForgeBookTier.SILVER,
            ForgeDecisionEngine.chooseBook(ForgeBookPolicy.GOLD_THEN_SILVER, silverCount = 2, goldCount = 0),
        )
        assertEquals(
            ForgeBookTier.GOLD,
            ForgeDecisionEngine.chooseBook(ForgeBookPolicy.SILVER_THEN_GOLD, silverCount = 0, goldCount = 3),
        )
        assertEquals(
            ForgeBookTier.GOLD,
            ForgeDecisionEngine.chooseBook(ForgeBookPolicy.GOLD_THEN_SILVER, silverCount = null, goldCount = null),
        )
        assertEquals(
            ForgeBookTier.SILVER,
            ForgeDecisionEngine.chooseBook(
                ForgeBookPolicy.GOLD_THEN_SILVER,
                silverCount = null,
                goldCount = null,
                unavailable = setOf(ForgeBookTier.GOLD),
            ),
        )
        assertEquals(
            null,
            ForgeDecisionEngine.chooseBook(
                ForgeBookPolicy.GOLD_ONLY,
                silverCount = 99,
                goldCount = null,
                unavailable = setOf(ForgeBookTier.GOLD),
            ),
        )
    }

    @Test
    fun sameCategoryDifferentTargetDoesNotSatisfySlot() {
        val config = ForgeSlotConfig(
            category = ForgeAttributeCategory.ATTACK,
            targetStatId = "matk",
        )
        assertEquals(
            ForgeResultAction.RETRY,
            ForgeDecisionEngine.decide(parsed("ATK+1200"), parsed("ATK+1300"), config),
        )
        assertEquals(
            ForgeResultAction.ACCEPT_AND_COMPLETE,
            ForgeDecisionEngine.decide(parsed("ATK+1200"), parsed("MATK+1500"), config),
        )
    }

    @Test
    fun slotConfigurationUpdatesRemainIndependent() {
        val original = ForgeConfig()
        val changed = original.updateSlot(
            1,
            ForgeSlotConfig(
                enabled = false,
                category = ForgeAttributeCategory.OTHER,
                targetStatId = "ability_cap_up",
            ),
        )
        assertTrue(changed[0].enabled)
        assertFalse(changed[1].enabled)
        assertTrue(changed[2].enabled)
        assertEquals("atk", changed[0].targetStat().id)
        assertEquals("ability_cap_up", changed[1].targetStat().id)
        assertEquals("atk", changed[2].targetStat().id)
    }

    @Test
    fun configuredThresholdCannotExceedTheStatsMaximum() {
        val atk = requireNotNull(ForgeStats.byId("atk"))
        val damage = requireNotNull(ForgeStats.byId("damage_up"))
        val config = ForgeSlotConfig()
            .withThreshold(atk.id, 9999)
            .withThreshold(damage.id, 99)
        assertEquals(1500, config.thresholdFor(atk))
        assertEquals(10, config.thresholdFor(damage))
    }
}
