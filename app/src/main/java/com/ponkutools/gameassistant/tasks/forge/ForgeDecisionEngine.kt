package com.ponkutools.gameassistant.tasks.forge

enum class ForgeResultAction {
    ACCEPT_AND_COMPLETE,
    ACCEPT_AND_CONTINUE,
    RETRY,
}

object ForgeDecisionEngine {
    fun matchedAcceptedStat(stat: ParsedForgeStat, config: ForgeSlotConfig): ForgeStatDefinition? =
        config.acceptedStats().firstOrNull { candidate ->
            stat.definition.id == candidate.id && stat.value >= config.thresholdFor(candidate)
        }

    fun isSatisfied(stat: ParsedForgeStat, config: ForgeSlotConfig): Boolean =
        matchedAcceptedStat(stat, config) != null

    fun decide(
        current: ParsedForgeStat?,
        rolled: ParsedForgeStat,
        config: ForgeSlotConfig,
    ): ForgeResultAction {
        if (isSatisfied(rolled, config)) return ForgeResultAction.ACCEPT_AND_COMPLETE
        val target = config.targetStat()
        // Alternatives are completion-only. They must never be kept as a
        // same-type improvement while the configured primary target is below
        // threshold.
        if (!config.preserveBetterSameType || rolled.definition.id != target.id) {
            return ForgeResultAction.RETRY
        }

        val currentTargetValue = current
            ?.takeIf { it.definition.id == target.id }
            ?.value
        val improvement = currentTargetValue == null || rolled.value > currentTargetValue
        return if (improvement) ForgeResultAction.ACCEPT_AND_CONTINUE else ForgeResultAction.RETRY
    }

    fun chooseBook(
        policy: ForgeBookPolicy,
        silverCount: Int?,
        goldCount: Int?,
        unavailable: Set<ForgeBookTier> = emptySet(),
    ): ForgeBookTier? {
        fun available(tier: ForgeBookTier, count: Int?): Boolean = tier !in unavailable && (count == null || count > 0)
        return when (policy) {
            ForgeBookPolicy.GOLD_ONLY -> ForgeBookTier.GOLD.takeIf { available(it, goldCount) }
            ForgeBookPolicy.SILVER_ONLY -> ForgeBookTier.SILVER.takeIf { available(it, silverCount) }
            ForgeBookPolicy.GOLD_THEN_SILVER -> when {
                available(ForgeBookTier.GOLD, goldCount) -> ForgeBookTier.GOLD
                available(ForgeBookTier.SILVER, silverCount) -> ForgeBookTier.SILVER
                else -> null
            }
            ForgeBookPolicy.SILVER_THEN_GOLD -> when {
                available(ForgeBookTier.SILVER, silverCount) -> ForgeBookTier.SILVER
                available(ForgeBookTier.GOLD, goldCount) -> ForgeBookTier.GOLD
                else -> null
            }
        }
    }
}
