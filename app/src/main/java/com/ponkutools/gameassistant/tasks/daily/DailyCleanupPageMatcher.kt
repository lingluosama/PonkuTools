package com.ponkutools.gameassistant.tasks.daily

import com.ponkutools.gameassistant.tasks.OcrSnapshot
import com.ponkutools.gameassistant.ocr.OcrTextSimilarity

enum class DailyPage(val displayName: String) {
    HOME("首页"),
    LEVEL("关卡"),
    BATTLE_SKIP("仗魂"),
    EXPLORE("探索"),
    OUTE("欧特"),
    QUIZ("猜谜"),
    SHOP("商店"),
}

object DailyCleanupPageMatcher {
    fun matches(snapshot: OcrSnapshot, page: DailyPage): Boolean {
        val score = score(snapshot, page)
        val observed = snapshot.lines.map { it.text } + snapshot.text.replace("\n", "")
        val hasDistinctiveAnchor = requireNotNull(PAGE_DISTINCTIVE[page]).any { anchor ->
            observed.maxOfOrNull { text -> OcrTextSimilarity.partialSimilarity(text, anchor) }
                ?.let { it >= DISTINCTIVE_MATCH_THRESHOLD } == true
        }
        return score >= PAGE_MATCH_THRESHOLD && hasDistinctiveAnchor
    }

    fun score(snapshot: OcrSnapshot, page: DailyPage): Float {
        val observed = snapshot.lines.map { it.text } + snapshot.text.replace("\n", "")
        val anchorScores = requireNotNull(PAGE_TEMPLATES[page]).map { anchor ->
            observed.maxOfOrNull { text -> OcrTextSimilarity.partialSimilarity(text, anchor) } ?: 0f
        }.sortedDescending()
        if (anchorScores.isEmpty()) return 0f
        val strongest = anchorScores.take(2)
        val average = strongest.average().toFloat()
        val reliableCount = anchorScores.count { it >= ANCHOR_MATCH_THRESHOLD }
        return when {
            reliableCount >= 2 -> average
            anchorScores.first() >= STRONG_SINGLE_ANCHOR_THRESHOLD -> anchorScores.first()
            else -> average * 0.65f
        }
    }

    private val PAGE_TEMPLATES = mapOf(
        DailyPage.HOME to listOf("收藏", "探索", "公会", "聊天室", "礼物", "任务", "剧情"),
        DailyPage.LEVEL to listOf("关卡TOP", "故事关卡", "活动关卡", "练习室", "突破", "每日一键跳过", "武器关卡"),
        DailyPage.BATTLE_SKIP to listOf("仗魂争霸", "挑战次数", "掉落奖励", "EXCEEDPOINTS", "跳过", "出击"),
        DailyPage.EXPLORE to listOf("素材探索", "探索1", "探索2", "探索3", "立即返回", "全部回收"),
        DailyPage.OUTE to listOf("与欧特一起", "距离下个阶段还有", "立即领取的话", "领取", "关闭"),
        DailyPage.QUIZ to listOf("猜谜", "注意", "挑战猜谜", "问题", "前往图鉴", "回答"),
        DailyPage.SHOP to listOf("商店", "魂友神器武器", "维斯托利亚通行证", "道具", "金币", "原神石", "特别", "交换所", "武器召唤", "出售"),
    )

    private val PAGE_DISTINCTIVE = mapOf(
        DailyPage.HOME to listOf("尊荣卡", "礼物", "任务", "剧情", "公会", "聊天室"),
        DailyPage.LEVEL to listOf("关卡TOP", "故事关卡", "活动关卡", "练习室", "突破", "每日一键跳过", "武器关卡"),
        DailyPage.BATTLE_SKIP to listOf("仗魂争霸", "挑战次数", "掉落奖励", "EXCEEDPOINTS"),
        DailyPage.EXPLORE to listOf("素材探索", "探索1", "探索2", "探索3", "立即返回", "全部回收"),
        DailyPage.OUTE to listOf("与欧特一起", "距离下个阶段还有", "立即领取的话"),
        DailyPage.QUIZ to listOf("猜谜", "注意", "挑战猜谜", "问题", "前往图鉴"),
        DailyPage.SHOP to listOf("商店", "魂友神器武器", "维斯托利亚通行证", "特别", "交换所", "武器召唤"),
    )

    private const val PAGE_MATCH_THRESHOLD = 0.50f
    private const val ANCHOR_MATCH_THRESHOLD = 0.48f
    private const val STRONG_SINGLE_ANCHOR_THRESHOLD = 0.72f
    private const val DISTINCTIVE_MATCH_THRESHOLD = 0.48f
}
