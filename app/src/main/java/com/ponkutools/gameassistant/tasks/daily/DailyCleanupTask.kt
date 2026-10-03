package com.ponkutools.gameassistant.tasks.daily

import com.ponkutools.gameassistant.tasks.AutomationHaltedException
import com.ponkutools.gameassistant.tasks.AutomationSession
import com.ponkutools.gameassistant.tasks.AutomationTask
import com.ponkutools.gameassistant.tasks.OcrRetryPolicy
import com.ponkutools.gameassistant.tasks.TaskRegistry
import com.ponkutools.gameassistant.tasks.forge.DesignPoint

class DailyCleanupTask : AutomationTask {
    override val id = TaskRegistry.DAILY_CLEANUP
    override val displayName = "清理日常（beta）"

    override suspend fun run(session: AutomationSession) {
        // Navigation is conditional. If the task was started on the target
        // page, continue directly with the next business action instead of
        // tapping the navigation button and treating an unchanged screen as a
        // failed click.
        ensurePage(session, DailyPage.LEVEL, DailyPoint.LEVEL, "前往关卡")
        subtask(session, "跳过每日任务", DailyPage.LEVEL) {
            action(session, DailyPoint.DAILY_SKIP, "每日跳过", verify = false)
            action(session, DailyPoint.DAILY_CONFIRM, "确认跳过每日")
            action(session, DailyPoint.DAILY_SETTLE, "结算每日跳过")
            action(session, DailyPoint.LEVEL_FROM_DAILY, "回到关卡")
        }
        subtask(session, "跳过仗魂", DailyPage.LEVEL) {
            action(session, DailyPoint.BATTLE_SPIRIT, "进入仗魂", verify = false)
            action(session, DailyPoint.CHALLENGE_SPIRIT, "挑战仗魂魄")
            action(session, DailyPoint.SKIP_BATTLE, "跳过战斗")
            action(session, DailyPoint.CONFIRM_SKIP, "确认跳过战斗")
            action(session, DailyPoint.BATTLE_SETTLE, "结算仗魂")
            action(session, DailyPoint.BATTLE_SETTLE, "再次结算仗魂")
            action(session, DailyPoint.LEVEL_FROM_BATTLE, "回到关卡")
        }
        ensurePage(session, DailyPage.HOME, DailyPoint.HOME, "回到首页")
        subtask(session, "结算探索", DailyPage.HOME) {
            action(session, DailyPoint.EXPLORE, "前往探索", verify = false)
            action(session, DailyPoint.EXPLORE_SETTLE, "结算探索")
            action(session, DailyPoint.EXPLORE_AGAIN, "再次探索")
            action(session, DailyPoint.HOME, "回到首页")
        }
        subtask(session, "喂食欧特", DailyPage.HOME) {
            action(session, DailyPoint.SIDEBAR, "展开侧栏", verify = false)
            action(session, DailyPoint.OUTE, "前往欧特")
            action(session, DailyPoint.FEED_ONE, "使用好感度道具 1")
            action(session, DailyPoint.FEED_TWO, "使用好感度道具 2")
            action(session, DailyPoint.CLOSE_OUTE, "关闭欧特")
        }
        subtask(session, "前往猜谜", DailyPage.HOME) {
            action(session, DailyPoint.SIDEBAR, "展开侧栏", verify = false)
            action(session, DailyPoint.QUIZ, "前往猜谜")
            action(session, DailyPoint.QUIZ_START, "出题")
            action(session, DailyPoint.QUIZ_ANSWER, "回答")
            action(session, DailyPoint.QUIZ_EXPLAIN, "前往解说")
            action(session, DailyPoint.QUIZ_REWARD, "领取奖励")
        }
        ensurePage(session, DailyPage.HOME, DailyPoint.HOME, "回到首页（商店前）")
        ensurePage(session, DailyPage.SHOP, DailyPoint.SHOP, "前往商店")
        subtask(session, "商店免费石头", DailyPage.SHOP) {
            action(session, DailyPoint.SPECIAL_SHOP, "打开特别商品", verify = false)
            action(session, DailyPoint.FREE_STONE, "领取免费石头")
            action(session, DailyPoint.CONFIRM_STONE, "确认领取石头")
            action(session, DailyPoint.HOME, "回到首页")
        }
        session.log("清理日常（beta）任务结束")
    }

    private suspend fun ensurePage(
        session: AutomationSession,
        page: DailyPage,
        navigationPoint: DailyPoint,
        navigationLabel: String,
    ) {
        val current = session.recognizeOptional(
            "清理日常（beta） · 当前页面确认 · ${page.name}",
            CURRENT_PAGE_PROBE_TIMEOUT_MS,
        ) { snapshot ->
            DailyCleanupPageMatcher.matches(snapshot, page)
        }
        if (current != null) {
            screenWidth = current.screenWidth
            screenHeight = current.screenHeight
            val score = DailyCleanupPageMatcher.score(current, page)
            session.log("当前已在${page.displayName}，跳过“$navigationLabel”，匹配度=${"%.1f".format(score * 100)}%")
            return
        }
        session.log("当前不在${page.displayName}，执行导航：$navigationLabel")
        // The following subtask owns the destination-page validation. This
        // navigation tap only starts the transition and therefore has no
        // before/after OCR-change requirement.
        action(session, navigationPoint, navigationLabel, verify = false)
    }

    private suspend fun subtask(
        session: AutomationSession,
        name: String,
        page: DailyPage,
        actions: suspend () -> Unit,
    ) {
        val snapshot = session.recognizeValidWithPolicy("清理日常（beta） · $name · 页面确认", OcrRetryPolicy.DAILY_PAGE) { current ->
            DailyCleanupPageMatcher.matches(current, page)
        }
        screenWidth = snapshot.screenWidth
        screenHeight = snapshot.screenHeight
        val similarity = DailyCleanupPageMatcher.score(snapshot, page)
        session.log("子任务开始：$name，页面=${page.displayName}，匹配度=${"%.1f".format(similarity * 100)}%")
        actions()
    }

    private suspend fun action(
        session: AutomationSession,
        point: DailyPoint,
        label: String,
        verify: Boolean = true,
    ) {
        val (x, y) = point.point.scale(screenWidth, screenHeight)
        if (!verify) {
            session.dailyTapAndWait(x, y, "清理日常（beta） · $label")
            return
        }
        val success = session.dailyTapOcrVerified(
            x,
            y,
            "清理日常（beta） · $label",
        )
        if (!success) throw AutomationHaltedException("动作“$label”执行后页面没有变化，重试后仍未确认")
    }

    private enum class DailyPoint(val point: DesignPoint) {
        LEVEL(DesignPoint(305, 917)),
        DAILY_SKIP(DesignPoint(97, 551)),
        DAILY_CONFIRM(DesignPoint(377, 830)),
        DAILY_SETTLE(DesignPoint(282, 910)),
        LEVEL_FROM_DAILY(DesignPoint(282, 910)),
        BATTLE_SPIRIT(DesignPoint(110, 772)),
        CHALLENGE_SPIRIT(DesignPoint(460, 780)),
        SKIP_BATTLE(DesignPoint(276, 806)),
        CONFIRM_SKIP(DesignPoint(382, 690)),
        BATTLE_SETTLE(DesignPoint(282, 910)),
        LEVEL_FROM_BATTLE(DesignPoint(355, 910)),
        EXPLORE(DesignPoint(327, 660)),
        EXPLORE_SETTLE(DesignPoint(427, 727)),
        // The supplied 1590,908 is outside the 540×960 reference canvas;
        // treat it as the likely missing zero: 159,908.
        EXPLORE_AGAIN(DesignPoint(159, 908)),
        HOME(DesignPoint(52, 903)),
        SIDEBAR(DesignPoint(14, 462)),
        OUTE(DesignPoint(50, 379)),
        FEED_ONE(DesignPoint(358, 789)),
        FEED_TWO(DesignPoint(175, 789)),
        CLOSE_OUTE(DesignPoint(268, 905)),
        QUIZ(DesignPoint(44, 460)),
        QUIZ_START(DesignPoint(268, 789)),
        QUIZ_ANSWER(DesignPoint(97, 708)),
        QUIZ_EXPLAIN(DesignPoint(258, 786)),
        QUIZ_REWARD(DesignPoint(258, 786)),
        SHOP(DesignPoint(489, 921)),
        SPECIAL_SHOP(DesignPoint(477, 197)),
        FREE_STONE(DesignPoint(230, 329)),
        CONFIRM_STONE(DesignPoint(285, 697)),
    }

    private var screenWidth: Int = 540
    private var screenHeight: Int = 960

    private companion object {
        const val CURRENT_PAGE_PROBE_TIMEOUT_MS = 800L
    }
}
