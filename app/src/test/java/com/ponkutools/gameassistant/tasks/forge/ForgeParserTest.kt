package com.ponkutools.gameassistant.tasks.forge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeParserTest {
    @Test
    fun parsesMindAndFailsDefaultThreshold() {
        val parsed = requireNotNull(ForgeParser.parse("MIND+15"))
        val config = ForgeSlotConfig(category = ForgeAttributeCategory.STAMINA)
        assertEquals("mind", parsed.definition.id)
        assertEquals(15, parsed.value)
        assertFalse(ForgeDecisionEngine.isSatisfied(parsed, config))
    }

    @Test
    fun parsesAbilityCapBeforeGenericDamage() {
        val parsed = requireNotNull(ForgeParser.parse("能力伤害上限提升 +10%"))
        assertEquals("ability_cap_up", parsed.definition.id)
        assertTrue(
            ForgeDecisionEngine.isSatisfied(
                parsed,
                ForgeSlotConfig(
                    category = ForgeAttributeCategory.OTHER,
                    targetStatId = "ability_cap_up",
                ),
            ),
        )
    }

    @Test
    fun specificAbilityDamageDoesNotBecomeGenericDamage() {
        val parsed = requireNotNull(ForgeParser.parse("能力伤害提升+12%"))
        assertEquals("ability_damage_up", parsed.definition.id)
    }

    @Test
    fun recognizesPartialResultLabelsBySpecificKeywords() {
        assertEquals("ability_cap_up", ForgeParser.parse("能力 上限 +10%")?.definition?.id)
        assertEquals("bond_cap_up", ForgeParser.parse("神絆 上限 +10%")?.definition?.id)
        assertEquals("ability_damage_up", ForgeParser.parse("能力 +12%")?.definition?.id)
        assertEquals("bond_damage_up", ForgeParser.parse("神绊 +15%")?.definition?.id)
        assertEquals("damage_up", ForgeParser.parse("伤害 +5%")?.definition?.id)
    }

    @Test
    fun artifactFuzzyMatchingDistinguishesWeaponAndElementAttackStats() {
        assertEquals(
            "artifact_element_atk",
            ForgeParser.parse("水属性的我方角色ATK提升 +3%", ForgeMode.ARTIFACT)?.definition?.id,
        )
        assertEquals(
            "artifact_weapon_atk",
            ForgeParser.parse("擅长魔具的我方角色ATK提升 +4%", ForgeMode.ARTIFACT)?.definition?.id,
        )
        assertEquals(
            "artifact_element_matk",
            ForgeParser.parse("暗属性的我方角色MATK提升 +3%", ForgeMode.ARTIFACT)?.definition?.id,
        )
        assertEquals(
            "artifact_weapon_matk",
            ForgeParser.parse("擅长魔具的我方角色MATK提升 +4%", ForgeMode.ARTIFACT)?.definition?.id,
        )
    }

    @Test
    fun artifactFuzzyMatchingHandlesNoisyCapAndPassiveLabels() {
        assertEquals(
            "artifact_ability_cap",
            ForgeParser.parse("能力伤害土限提升 +4%", ForgeMode.ARTIFACT)?.definition?.id,
        )
        assertEquals(
            "artifact_passive_skill",
            ForgeParser.parse("被動技能1強化 +5%", ForgeMode.ARTIFACT)?.definition?.id,
        )
        assertEquals(
            "artifact_combo_cap",
            ForgeParser.parse("連击上限提升 +4", ForgeMode.ARTIFACT)?.definition?.id,
        )
    }

    @Test
    fun artifactAttackStatsUseFourPercentMaximum() {
        ForgeStats.forCategory(ForgeMode.ARTIFACT, ForgeAttributeCategory.ATTACK).forEach { stat ->
            assertEquals(stat.displayName, 4, stat.defaultThreshold)
            assertEquals(stat.displayName, 4, ForgeParser.parse("${stat.displayName} +4%", ForgeMode.ARTIFACT)?.value)
        }
    }

    @Test
    fun parsesObservedBondCapAndCtrOcrNoise() {
        val before = ForgeParser.parse("ea神绊技能伤害上限堤升 +8%")
        assertEquals("(ACTR率+8%", ForgeParser.normalize("(ACRT率 +8%"))
        assertNotNull(ForgeParser.parse("CTR率 +8%"))
        assertNotNull(ForgeParser.parse("ACRT率 +8%"))
        val after = ForgeParser.parse("(ACRT率 +8%")
        assertEquals("bond_cap_up", before?.definition?.id)
        assertEquals(8, before?.value)
        assertEquals("ctr_rate", after?.definition?.id)
        assertEquals(8, after?.value)
    }

    @Test
    fun capStatsSurviveNoisyUpperLimitCharacters() {
        val ability = ForgeParser.parse("能カ伤害土限提升 +9%")
        val bond = ForgeParser.parse("神绊技能伤害止限提升 +8%")
        assertEquals("ability_cap_up", ability?.definition?.id)
        assertEquals(9, ability?.value)
        assertEquals("bond_cap_up", bond?.definition?.id)
        assertEquals(8, bond?.value)
    }

    @Test
    fun fuzzySignaturesIdentifyEveryForgeStat() {
        val cases = mapOf(
            "杂讯ATK +1500" to "atk",
            "杂讯MATK +1500" to "matk",
            "杂讯伤害 +10%" to "damage_up",
            "杂讯能力 +15%" to "ability_damage_up",
            "杂讯神绊 +15%" to "bond_damage_up",
            "杂讯DEF +1000" to "def",
            "杂讯MDEF +1000" to "mdef",
            "杂讯水抗性 +20%" to "element_resistance",
            "杂讯HP +500" to "hp",
            "杂讯MIND +150" to "mind",
            "杂讯异常抗性 +20%" to "ailment_resistance",
            "杂讯回复 +20%" to "healing_up",
            "杂讯CRT +10%" to "ctr_rate",
            "杂讯赋予 +20%" to "ailment_chance",
            "杂讯能力上限 +10%" to "ability_cap_up",
            "杂讯神绊上限 +10%" to "bond_cap_up",
        )

        cases.forEach { (text, expectedId) ->
            assertEquals(text, expectedId, ForgeParser.parse(text)?.definition?.id)
        }
    }

    @Test
    fun acceptsFullWidthSymbolsAndAlternativeChanceSpelling() {
        val parsed = ForgeParser.parse("状态异常赋予几率提升 ＋ 20％")
        assertNotNull(parsed)
        assertEquals("ailment_chance", parsed?.definition?.id)
        assertEquals(20, parsed?.value)
    }

    @Test
    fun recognizesLockedSlot() {
        assertTrue(ForgeParser.isLocked(listOf("解锁")))
    }

    @Test
    fun repairsObservedDamageCharacterConfusion() {
        val parsed = requireNotNull(ForgeParser.parse("伤書提升 +5%"))
        assertEquals("damage_up", parsed.definition.id)
        assertEquals(5, parsed.value)
    }

    @Test
    fun repairsObservedEnhancedDamageCharacterConfusion() {
        val parsed = requireNotNull(ForgeParser.parse("伤畫堤升+5%"))
        assertEquals("damage_up", parsed.definition.id)
        assertEquals(5, parsed.value)
    }

    @Test
    fun repairsPercentSignDuplicatedAsTrailingNine() {
        val parsed = requireNotNull(ForgeParser.parse("伤書提升 +59%"))
        assertEquals("damage_up", parsed.definition.id)
        assertEquals(5, parsed.value)
    }

    @Test
    fun usesEachStatsConfiguredMaximumForValidation() {
        assertEquals(9, ForgeParser.parse("伤害提升 +9%")?.value)
        assertEquals(10, ForgeParser.parse("伤害提升 +109%")?.value)
        assertNull(ForgeParser.parse("伤害提升 +55%"))
        assertNull(ForgeParser.parse("MATK +1501"))
    }
}
