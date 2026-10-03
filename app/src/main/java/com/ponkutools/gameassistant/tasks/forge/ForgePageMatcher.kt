package com.ponkutools.gameassistant.tasks.forge

import com.ponkutools.gameassistant.tasks.OcrSnapshot

object ForgePageMatcher {
    fun isMainPage(snapshot: OcrSnapshot): Boolean {
        val topLimit = snapshot.screenHeight * 0.2f
        val topText = snapshot.lines
            .filter { it.bounds.centerY <= topLimit }
            .joinToString("") { normalize(it.text) }
        val titleMatched = TITLE_KEYWORDS.containsMatchIn(topText)
        if (titleMatched) return true

        val texts = snapshot.lines.map { normalize(it.text) }
        val hasForgeEffect = texts.any { FORGE_EFFECT_KEYWORDS.countMatches(it) >= 2 }
        val hasInventory = texts.any { INVENTORY_KEYWORDS.countMatches(it) >= 2 }
        val hasDecision = texts.any { it.contains("决定") || (it.contains("决") && it.contains("定")) }
        return hasForgeEffect && hasInventory && hasDecision
    }

    internal fun isTitleLike(text: String): Boolean {
        val value = normalize(text)
        return TITLE_KEYWORDS.containsMatchIn(value)
    }

    private fun normalize(text: String): String = ForgeParser.normalize(text)
        .replace("カ", "力")
        .replace("鍛", "锻")

    private fun Regex.countMatches(text: String): Int = findAll(text).map { it.value }.distinct().count()

    private val TITLE_KEYWORDS = Regex("魂友|锻造|能力")
    private val FORGE_EFFECT_KEYWORDS = Regex("能力|锻造|效果")
    private val INVENTORY_KEYWORDS = Regex("持有|道具|一览")
}
