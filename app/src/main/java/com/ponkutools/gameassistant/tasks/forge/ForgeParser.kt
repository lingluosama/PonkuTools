package com.ponkutools.gameassistant.tasks.forge

object ForgeParser {
    private val aliases = ForgeStats.all
        .flatMap { definition -> definition.aliases.map { alias -> definition to normalize(alias) } }
        .sortedByDescending { (_, alias) -> alias.length }

    private fun order(mode: ForgeMode): List<ForgeStatDefinition> = when (mode) {
        ForgeMode.SOULMATE -> ForgeStats.soulmateFuzzyMatchOrder
        ForgeMode.ARTIFACT -> ForgeStats.artifactFuzzyMatchOrder
    }

    private fun fuzzySignatures(mode: ForgeMode) = order(mode).associateWith { definition ->
        definition.fuzzySignatures.map { signature -> signature.map(::normalize) }
    }

    fun normalize(text: String): String = text
        .replace("％", "%")
        .replace("＋", "+")
        .replace("﹢", "+")
        .replace("−", "-")
        .replace("—", "-")
        .replace("伤書提升", "伤害提升")
        .replace("伤畫堤升", "伤害提升")
        .replace("伤画堤升", "伤害提升")
        .replace("傷", "伤")
        .replace("絆", "绊")
        .replace("鍛", "锻")
        .replace("能カ", "能力")
        .replace("能カ", "能力")
        .replace("角色", "角色")
        .replace("我方角", "我方角色")
        .replace("擅长魔具的我方角", "擅长魔具的我方角色")
        .replace("MATK提昇", "MATK提升")
        .replace("ATK提昇", "ATK提升")
        .replace("技強化", "技能强化")
        .replace("被動", "被动")
        .replace("強化", "强化")
        .replace("連击", "连击")
        .replace("土限", "上限")
        .replace("止限", "上限")
        .replace(Regex("\\s+"), "")
        .uppercase()
        .replace("CRT率", "CTR率")

    fun parse(text: String, mode: ForgeMode = ForgeMode.SOULMATE): ParsedForgeStat? {
        val normalized = normalize(text)
        val exactMatch = aliases.firstOrNull { (definition, alias) -> definition.mode == mode && normalized.contains(alias) }
        val definition = exactMatch?.first ?: matchByKeywords(normalized, mode) ?: return null
        val valueText = exactMatch?.second?.let { normalized.substringAfter(it, missingDelimiterValue = "") }
            ?: normalized
        val rawValue = findValue(valueText, definition.percentage)
            ?: return null
        val value = repairDuplicatedPercentSign(definition, valueText, rawValue)
        if (value > definition.defaultThreshold) return null
        return ParsedForgeStat(definition, value, text)
    }

    private fun matchByKeywords(text: String, mode: ForgeMode): ForgeStatDefinition? {
        val signatures = fuzzySignatures(mode)
        return order(mode).firstOrNull { definition ->
            signatures[definition].orEmpty().any { signature ->
                signature.all(text::contains)
            }
        }
    }

    private fun findValue(text: String, percentage: Boolean): Int? {
        val match = if (percentage && '%' in text) {
            Regex("(-?\\d+)(?=%)").findAll(text).lastOrNull()
        } else {
            Regex("[+]?(-?\\d+)").findAll(text).lastOrNull()
        } ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun repairDuplicatedPercentSign(
        definition: ForgeStatDefinition,
        suffix: String,
        value: Int,
    ): Int = if (
        definition.percentage &&
        '%' in suffix &&
        value > definition.defaultThreshold &&
        value % 10 == 9 &&
        value / 10 <= definition.defaultThreshold
    ) {
        value / 10
    } else {
        value
    }

    fun parseFirst(texts: Iterable<String>, mode: ForgeMode = ForgeMode.SOULMATE): ParsedForgeStat? =
        texts.mapNotNull { parse(it, mode) }.firstOrNull()

    fun isLocked(texts: Iterable<String>): Boolean = texts.any { normalize(it).contains("解锁") }
}
