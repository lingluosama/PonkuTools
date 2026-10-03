package com.ponkutools.gameassistant.tasks.forge

import com.ponkutools.gameassistant.automation.CoordinateMapper
import com.ponkutools.gameassistant.ocr.Bounds

data class DesignPoint(val x: Int, val y: Int) {
    fun scale(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val point = CoordinateMapper.designToScreen(x, y, screenWidth, screenHeight)
        return point.x to point.y
    }
}

object SoulmateForgeLayout {
    val resultBeforeCenter = DesignPoint(277, 388)
    val resultAfterCenter = DesignPoint(190, 720)
    val retryPoint = DesignPoint(169, 852)
    val forgeDecisionPoint = DesignPoint(460, 834)
    val resultDecisionPoint = DesignPoint(361, 855)
    // 382,672 is the supplied top-edge reference; gestures use the safe interior point.
    val confirmationDecisionPoint = DesignPoint(382, 690)

    val slotCenters = listOf(
        DesignPoint(296, 396),
        DesignPoint(178, 450),
        DesignPoint(256, 510),
    )

    private val silverBook = mapOf(
        ForgeAttributeCategory.ATTACK to DesignPoint(161, 652),
        ForgeAttributeCategory.DEFENSE to DesignPoint(400, 660),
        ForgeAttributeCategory.STAMINA to DesignPoint(158, 770),
        ForgeAttributeCategory.OTHER to DesignPoint(394, 757),
    )
    private val goldBook = mapOf(
        ForgeAttributeCategory.ATTACK to DesignPoint(225, 668),
        ForgeAttributeCategory.DEFENSE to DesignPoint(462, 647),
        ForgeAttributeCategory.STAMINA to DesignPoint(208, 768),
        ForgeAttributeCategory.OTHER to DesignPoint(481, 763),
    )

    fun bookPoint(category: ForgeAttributeCategory, tier: ForgeBookTier): DesignPoint =
        when (tier) {
            ForgeBookTier.SILVER -> requireNotNull(silverBook[category])
            ForgeBookTier.GOLD -> requireNotNull(goldBook[category])
        }

    fun slotBounds(index: Int, screenWidth: Int, screenHeight: Int): Bounds {
        val center = slotCenters[index]
        return CoordinateMapper.designBoundsToScreen(
            Bounds(45, center.y - 28, 500, center.y + 28),
            screenWidth,
            screenHeight,
        )
    }

    fun bookCountBounds(
        category: ForgeAttributeCategory,
        tier: ForgeBookTier,
        screenWidth: Int,
        screenHeight: Int,
    ): Bounds {
        val point = bookPoint(category, tier)
        return CoordinateMapper.designBoundsToScreen(
            Bounds(
                point.x - 34,
                point.y - 8,
                point.x + 34,
                point.y + 34,
            ),
            screenWidth,
            screenHeight,
        )
    }
}
