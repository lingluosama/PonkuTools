package com.ponkutools.gameassistant.tasks.forge

enum class ForgeMode(val displayName: String, val taskName: String) {
    SOULMATE("魂友能力锻造", "魂友能力锻造"),
    ARTIFACT("神器能力锻造", "神器能力锻造"),
}

enum class ForgeAttributeCategory(val displayName: String) {
    ATTACK("攻击"),
    DEFENSE("防御"),
    STAMINA("体力"),
    OTHER("其他"),

    ;

    companion object {
        fun forMode(mode: ForgeMode): List<ForgeAttributeCategory> = when (mode) {
            ForgeMode.SOULMATE -> entries
            ForgeMode.ARTIFACT -> listOf(ATTACK, OTHER)
        }
    }
}

enum class ForgeBookPolicy(val displayName: String) {
    GOLD_ONLY("仅使用金书"),
    SILVER_ONLY("仅使用银书"),
    GOLD_THEN_SILVER("金书 > 银书"),
    SILVER_THEN_GOLD("银书 > 金书"),
}

enum class ForgeBookTier(val displayName: String) {
    SILVER("银书"),
    GOLD("金书"),
}

data class ForgeStatDefinition(
    val id: String,
    val displayName: String,
    val category: ForgeAttributeCategory,
    val defaultThreshold: Int,
    val aliases: List<String> = listOf(displayName),
    val fuzzySignatures: List<List<String>> = emptyList(),
    val percentage: Boolean = false,
    val mode: ForgeMode = ForgeMode.SOULMATE,
)

object ForgeStats {
    val soulmate = listOf(
        ForgeStatDefinition("atk", "ATK", ForgeAttributeCategory.ATTACK, 1500, fuzzySignatures = listOf(listOf("ATK"))),
        ForgeStatDefinition("matk", "MATK", ForgeAttributeCategory.ATTACK, 1500, fuzzySignatures = listOf(listOf("MATK"))),
        ForgeStatDefinition(
            "damage_up", "伤害提升", ForgeAttributeCategory.ATTACK, 10,
            fuzzySignatures = listOf(listOf("伤害")), percentage = true,
        ),
        ForgeStatDefinition(
            "ability_damage_up", "能力伤害提升", ForgeAttributeCategory.ATTACK, 15,
            fuzzySignatures = listOf(listOf("能力", "伤害"), listOf("能力")), percentage = true,
        ),
        ForgeStatDefinition(
            "bond_damage_up", "神绊技能伤害提升", ForgeAttributeCategory.ATTACK, 15,
            fuzzySignatures = listOf(listOf("神绊", "伤害"), listOf("神绊")), percentage = true,
        ),
        ForgeStatDefinition("def", "DEF", ForgeAttributeCategory.DEFENSE, 1000, fuzzySignatures = listOf(listOf("DEF"))),
        ForgeStatDefinition("mdef", "MDEF", ForgeAttributeCategory.DEFENSE, 1000, fuzzySignatures = listOf(listOf("MDEF"))),
        ForgeStatDefinition(
            "element_resistance",
            "水/风/土/光/暗属性抗性提升",
            ForgeAttributeCategory.DEFENSE,
            20,
            aliases = listOf("水属性抗性提升", "风属性抗性提升", "土属性抗性提升", "光属性抗性提升", "暗属性抗性提升"),
            fuzzySignatures = listOf(
                listOf("属性", "抗性"),
                listOf("水", "抗性"),
                listOf("风", "抗性"),
                listOf("土", "抗性"),
                listOf("光", "抗性"),
                listOf("暗", "抗性"),
            ),
            percentage = true,
        ),
        ForgeStatDefinition("hp", "HP", ForgeAttributeCategory.STAMINA, 500, fuzzySignatures = listOf(listOf("HP"))),
        ForgeStatDefinition("mind", "MIND", ForgeAttributeCategory.STAMINA, 150, fuzzySignatures = listOf(listOf("MIND"))),
        ForgeStatDefinition(
            "ailment_resistance", "状态异常抗性提升", ForgeAttributeCategory.STAMINA, 20,
            fuzzySignatures = listOf(listOf("状态", "异常", "抗性"), listOf("异常", "抗性")), percentage = true,
        ),
        ForgeStatDefinition(
            "healing_up", "回复量提升", ForgeAttributeCategory.STAMINA, 20,
            fuzzySignatures = listOf(listOf("回复量"), listOf("回复")), percentage = true,
        ),
        ForgeStatDefinition(
            "ctr_rate", "CTR率", ForgeAttributeCategory.OTHER, 10,
            fuzzySignatures = listOf(listOf("CTR"), listOf("CRT")), percentage = true,
        ),
        ForgeStatDefinition(
            "ailment_chance",
            "状态异常赋予机率提升",
            ForgeAttributeCategory.OTHER,
            20,
            aliases = listOf("状态异常赋予机率提升", "状态异常赋予几率提升"),
            fuzzySignatures = listOf(
                listOf("状态", "异常", "赋予"),
                listOf("异常", "赋予"),
                listOf("赋予", "机率"),
                listOf("赋予", "几率"),
                listOf("赋予"),
            ),
            percentage = true,
        ),
        ForgeStatDefinition(
            "ability_cap_up", "能力伤害上限提升", ForgeAttributeCategory.OTHER, 10,
            fuzzySignatures = listOf(listOf("能力", "上限"), listOf("能力", "限")), percentage = true,
        ),
        ForgeStatDefinition(
            "bond_cap_up", "神绊技能伤害上限提升", ForgeAttributeCategory.OTHER, 10,
            fuzzySignatures = listOf(listOf("神绊", "上限"), listOf("神绊", "限")), percentage = true,
        ),
    )

    val artifact = listOf(
        ForgeStatDefinition(
            "artifact_element_atk", "属性我方角色ATK提升", ForgeAttributeCategory.ATTACK, 4,
            fuzzySignatures = listOf(listOf("我方", "角色", "ATK"), listOf("属性", "ATK"), listOf("角色", "ATK")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_weapon_atk", "擅长类型的我方角色ATK提升", ForgeAttributeCategory.ATTACK, 4,
            fuzzySignatures = listOf(listOf("擅长", "ATK"), listOf("擅长", "角色", "ATK")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_element_matk", "属性我方角色MATK提升", ForgeAttributeCategory.ATTACK, 4,
            fuzzySignatures = listOf(listOf("我方", "角色", "MATK"), listOf("属性", "MATK"), listOf("角色", "MATK")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_weapon_matk", "擅长类型的我方角色MATK提升", ForgeAttributeCategory.ATTACK, 4,
            fuzzySignatures = listOf(listOf("擅长", "MATK"), listOf("擅长", "角色", "MATK")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_ability_damage", "能力伤害提升", ForgeAttributeCategory.ATTACK, 4,
            fuzzySignatures = listOf(listOf("能力", "伤害")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_bond_damage", "神绊技能伤害提升", ForgeAttributeCategory.ATTACK, 4,
            fuzzySignatures = listOf(listOf("神绊", "技能", "伤害"), listOf("神绊", "伤害")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_combo_cap", "连击上限提升", ForgeAttributeCategory.OTHER, 4,
            fuzzySignatures = listOf(listOf("连击", "上限")), mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_passive_skill", "被动技能1强化", ForgeAttributeCategory.OTHER, 5,
            fuzzySignatures = listOf(listOf("被动", "技能", "强化")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_ability_cap", "能力伤害上限提升", ForgeAttributeCategory.OTHER, 4,
            fuzzySignatures = listOf(listOf("能力", "上限"), listOf("能力", "限")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
        ForgeStatDefinition(
            "artifact_bond_cap", "神绊技能伤害上限提升", ForgeAttributeCategory.OTHER, 4,
            fuzzySignatures = listOf(listOf("神绊", "上限"), listOf("神绊", "限")), percentage = true, mode = ForgeMode.ARTIFACT,
        ),
    )

    val all = soulmate + artifact

    val soulmateFuzzyMatchOrder: List<ForgeStatDefinition> = listOf(
        "bond_cap_up",
        "ability_cap_up",
        "ailment_chance",
        "ailment_resistance",
        "element_resistance",
        "bond_damage_up",
        "ability_damage_up",
        "ctr_rate",
        "healing_up",
        "mind",
        "matk",
        "mdef",
        "atk",
        "def",
        "hp",
        "damage_up",
    ).mapNotNull(::byId)

    val artifactFuzzyMatchOrder: List<ForgeStatDefinition> = listOf(
        "artifact_bond_cap", "artifact_ability_cap", "artifact_passive_skill", "artifact_combo_cap",
        "artifact_bond_damage", "artifact_ability_damage", "artifact_weapon_matk", "artifact_element_matk",
        "artifact_weapon_atk", "artifact_element_atk",
    ).mapNotNull(::byId)

    val fuzzyMatchOrder: List<ForgeStatDefinition> get() = soulmateFuzzyMatchOrder
    fun forMode(mode: ForgeMode) = if (mode == ForgeMode.ARTIFACT) artifact else soulmate
    fun forCategory(category: ForgeAttributeCategory) = soulmate.filter { it.category == category }
    fun forCategory(mode: ForgeMode, category: ForgeAttributeCategory) = forMode(mode).filter { it.category == category }
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

data class ForgeSlotConfig(
    val mode: ForgeMode = ForgeMode.SOULMATE,
    val enabled: Boolean = true,
    val category: ForgeAttributeCategory = ForgeAttributeCategory.ATTACK,
    val targetStatId: String = ForgeStats.forCategory(mode, category).first().id,
    /** Up to two additional stats which can independently complete this slot. */
    val alternativeStatIds: List<String> = emptyList(),
    val bookPolicy: ForgeBookPolicy = ForgeBookPolicy.GOLD_THEN_SILVER,
    val preserveBetterSameType: Boolean = true,
    val thresholds: Map<String, Int> = ForgeStats.forMode(mode).associate { it.id to it.defaultThreshold },
) {
    fun thresholdFor(stat: ForgeStatDefinition): Int =
        (thresholds[stat.id] ?: stat.defaultThreshold).coerceIn(1, stat.defaultThreshold)
    fun targetStat(): ForgeStatDefinition =
        ForgeStats.byId(targetStatId)?.takeIf { it.category == category && it.mode == mode }
            ?: ForgeStats.forCategory(mode, category).first()

    fun alternativeStats(): List<ForgeStatDefinition> = alternativeStatIds
        .asSequence()
        .mapNotNull { id -> ForgeStats.byId(id) }
        .filter { it.category == category && it.mode == mode && it.id != targetStat().id }
        .distinctBy { it.id }
        .take(MAX_ALTERNATIVES)
        .toList()

    fun acceptedStats(): List<ForgeStatDefinition> = listOf(targetStat()) + alternativeStats()

    fun withThreshold(id: String, value: Int): ForgeSlotConfig {
        val maximum = ForgeStats.byId(id)?.defaultThreshold
        val normalized = maximum?.let { value.coerceIn(1, it) } ?: value.coerceAtLeast(1)
        return copy(thresholds = thresholds + (id to normalized))
    }
    fun withCategory(value: ForgeAttributeCategory): ForgeSlotConfig = copy(
        category = value,
        targetStatId = ForgeStats.forCategory(mode, value).first().id,
        alternativeStatIds = emptyList(),
    )

    fun withAlternative(index: Int, statId: String?): ForgeSlotConfig {
        require(index in 0 until MAX_ALTERNATIVES) { "备选词条索引超出范围" }
        val updated = alternativeStatIds.toMutableList()
        while (updated.size <= index) updated += ""
        updated[index] = statId.orEmpty()
        return copy(
            alternativeStatIds = updated
                .filter { it.isNotBlank() && it != targetStat().id }
                .distinct()
                .take(MAX_ALTERNATIVES),
        )
    }

    companion object {
        const val MAX_ALTERNATIVES = 2
    }
}

data class ForgeConfig(
    val slots: List<ForgeSlotConfig> = List(SLOT_COUNT) { ForgeSlotConfig() },
    val mode: ForgeMode = ForgeMode.SOULMATE,
) {
    init {
        require(slots.size == SLOT_COUNT) { "魂友能力锻造必须配置三个属性槽位" }
    }

    operator fun get(slotIndex: Int): ForgeSlotConfig = slots[slotIndex]

    fun withMode(value: ForgeMode): ForgeConfig = copy(
        mode = value,
        slots = slots.map { slot ->
            val category = ForgeAttributeCategory.forMode(value).firstOrNull { it == slot.category }
                ?: ForgeAttributeCategory.forMode(value).first()
            val previousTarget = slot.targetStatId
            val target = ForgeStats.forCategory(value, category)
                .firstOrNull { it.id == previousTarget }
                ?: ForgeStats.forCategory(value, category).first()
            ForgeSlotConfig(
                mode = value,
                enabled = slot.enabled,
                category = category,
                targetStatId = target.id,
                alternativeStatIds = slot.alternativeStatIds
                    .mapNotNull { ForgeStats.byId(it) }
                    .filter { it.mode == value && it.category == category && it.id != target.id }
                    .map { it.id }
                    .distinct()
                    .take(ForgeSlotConfig.MAX_ALTERNATIVES),
                bookPolicy = slot.bookPolicy,
                preserveBetterSameType = slot.preserveBetterSameType,
                thresholds = ForgeStats.forMode(value).associate { it.id to it.defaultThreshold },
            )
        },
    )

    fun updateSlot(slotIndex: Int, value: ForgeSlotConfig): ForgeConfig =
        copy(slots = slots.toMutableList().also { it[slotIndex] = value.copy(mode = mode) })

    companion object {
        const val SLOT_COUNT = 3
    }
}

data class ParsedForgeStat(
    val definition: ForgeStatDefinition,
    val value: Int,
    val sourceText: String,
)

sealed interface ForgeSlotState {
    data object Empty : ForgeSlotState
    data object Locked : ForgeSlotState
    data class Unlocked(val stat: ParsedForgeStat) : ForgeSlotState
}
