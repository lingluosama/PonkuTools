package com.ponkutools.gameassistant.tasks.forge

import com.ponkutools.gameassistant.ocr.Bounds
import com.ponkutools.gameassistant.ocr.OcrCandidate
import com.ponkutools.gameassistant.tasks.AutomationHaltedException
import com.ponkutools.gameassistant.tasks.AutomationSession
import com.ponkutools.gameassistant.tasks.AutomationTask
import com.ponkutools.gameassistant.tasks.OcrSnapshot
import com.ponkutools.gameassistant.tasks.TaskRegistry
import kotlin.math.abs

class SoulmateAbilityForgeTask(
    private val config: ForgeConfig,
) : AutomationTask {
    override val id = TaskRegistry.SOULMATE_ABILITY_FORGE
    override val displayName = config.mode.taskName

    override suspend fun run(session: AutomationSession) {
        val firstPage = mainPage(session)
        val slots = parseSlots(firstPage)
        val pending = slots.mapIndexedNotNull { index, state ->
            val slotConfig = config[index]
            if (!slotConfig.enabled) {
                session.log("槽位 ${index + 1} 已设置为手动跳过")
                null
            } else when (state) {
                ForgeSlotState.Locked -> {
                    session.log("槽位 ${index + 1} 尚未解锁，跳过")
                    null
                }
                ForgeSlotState.Empty -> {
                    session.log("槽位 ${index + 1} 未识别到有效词条，加入洗练任务")
                    index
                }
                is ForgeSlotState.Unlocked -> {
                    val satisfied = ForgeDecisionEngine.isSatisfied(state.stat, slotConfig)
                    val configured = slotConfig.acceptedStats().joinToString(" / ") { it.displayName }
                    session.log(
                        "槽位 ${index + 1}(目标=$configured)：${state.stat.definition.displayName}+${state.stat.value}" +
                            if (satisfied) "，已达标" else "，加入洗练任务",
                    )
                    index.takeUnless { satisfied }
                }
            }
        }

        if (pending.isEmpty()) {
            session.log("没有需要执行的词条任务，任务完成")
            return
        }

        pending.forEachIndexed { taskIndex, slotIndex ->
            processSlot(session, slotIndex, taskIndex, pending.size)
        }
        session.log("全部词条任务已完成")
    }

    private suspend fun processSlot(
        session: AutomationSession,
        slotIndex: Int,
        taskIndex: Int,
        taskCount: Int,
    ) {
        val slotConfig = config[slotIndex]
        val taskLabel = "词条任务 ${taskIndex + 1}/$taskCount（属性槽 ${slotIndex + 1}）"
        session.log("开始处理$taskLabel，目标=${slotConfig.targetStat().displayName}")
        val unavailableBooks = mutableSetOf<ForgeBookTier>()
        while (true) {
            val page = mainPage(session, taskLabel)
            val slotState = parseSlots(page)[slotIndex]
            when (slotState) {
                ForgeSlotState.Locked -> {
                    session.log("槽位 ${slotIndex + 1} 尚未解锁，跳过该词条任务")
                    return
                }
                ForgeSlotState.Empty -> {
                    session.log("槽位 ${slotIndex + 1} 当前为空或无法识别，继续洗练")
                }
                is ForgeSlotState.Unlocked -> {
                    if (ForgeDecisionEngine.isSatisfied(slotState.stat, slotConfig)) {
                        session.log("槽位 ${slotIndex + 1} 已在重检时达标")
                        return
                    }
                }
            }

            val counts = readBookCounts(page, slotConfig.category)
            val tier = ForgeDecisionEngine.chooseBook(
                slotConfig.bookPolicy,
                silverCount = counts[ForgeBookTier.SILVER],
                goldCount = counts[ForgeBookTier.GOLD],
                unavailable = unavailableBooks,
            )
            if (tier == null) {
                session.log(
                    "槽位 ${slotIndex + 1} 没有符合“${slotConfig.bookPolicy.displayName}”策略的可用书籍，跳过该词条任务",
                )
                return
            }

            val (slotX, slotY) = SoulmateForgeLayout.slotCenters[slotIndex]
                .scale(page.screenWidth, page.screenHeight)
            session.tap(slotX, slotY, "$taskLabel · 选择属性槽")
            var selectedPage = mainPage(session, taskLabel)

            val (bookX, bookY) = SoulmateForgeLayout.bookPoint(slotConfig.category, tier)
                .scale(selectedPage.screenWidth, selectedPage.screenHeight)
            val countText = counts[tier]?.toString() ?: "未识别，按优先级尝试"
            session.log("槽位 ${slotIndex + 1} 使用${slotConfig.category.displayName}${tier.displayName}，数量=$countText")
            session.tap(bookX, bookY, "$taskLabel · 选择${slotConfig.category.displayName}${tier.displayName}")
            selectedPage = mainPage(session, taskLabel)
            val (decideX, decideY) = SoulmateForgeLayout.forgeDecisionPoint
                .scale(selectedPage.screenWidth, selectedPage.screenHeight)
            session.tap(decideX, decideY, "$taskLabel · 开始抽取（固定坐标）")

            val initialResult = resultPageWithin(session, taskLabel, RESULT_WAIT_TIMEOUT_MS)
            if (initialResult == null) {
                markBookUnavailable(session, slotIndex, slotConfig, tier, unavailableBooks, "点击决定后 20 秒未进入抽取结果")
                continue
            }
            var result: ForgeRollResult = requireNotNull(initialResult)
            var current = result.before?.stat
            resultLoop@ while (true) {
                val action = ForgeDecisionEngine.decide(current, result.after.stat, slotConfig)
                val accepted = ForgeDecisionEngine.matchedAcceptedStat(result.after.stat, slotConfig)
                session.log(
                    "槽位 ${slotIndex + 1} 抽取：${format(result.before?.stat)} -> ${format(result.after.stat)}" +
                        accepted?.let { "，命中${it.displayName}" }.orEmpty() +
                        "，决策=$action",
                )
                when (action) {
                    ForgeResultAction.ACCEPT_AND_COMPLETE,
                    ForgeResultAction.ACCEPT_AND_CONTINUE,
                    -> {
                        val (afterX, afterY) = SoulmateForgeLayout.resultAfterCenter
                            .scale(result.snapshot.screenWidth, result.snapshot.screenHeight)
                        session.tap(afterX, afterY, "$taskLabel · 选择抽取后属性（固定坐标）")
                        val selectedResult = resultPage(session, taskLabel)
                        val (resultDecideX, resultDecideY) = SoulmateForgeLayout.resultDecisionPoint
                            .scale(selectedResult.snapshot.screenWidth, selectedResult.snapshot.screenHeight)
                        session.tap(resultDecideX, resultDecideY, "$taskLabel · 保存抽取后属性（固定坐标）")
                        awaitMainPage(session, taskLabel, RESULT_WAIT_TIMEOUT_MS)
                        if (action == ForgeResultAction.ACCEPT_AND_COMPLETE) return
                        break@resultLoop
                    }

                    ForgeResultAction.RETRY -> {
                        val (retryX, retryY) = SoulmateForgeLayout.retryPoint
                            .scale(result.snapshot.screenWidth, result.snapshot.screenHeight)
                        session.tap(retryX, retryY, "$taskLabel · 重新抽取（固定坐标）")
                        val confirmation = session.recognizeOptional(
                            "$taskLabel · 重新抽取确认",
                            RETRY_CONFIRM_TIMEOUT_MS,
                        ) { snapshot ->
                            isRetryConfirmationPage(snapshot)
                        }
                        if (confirmation == null) {
                            markBookUnavailable(
                                session,
                                slotIndex,
                                slotConfig,
                                tier,
                                unavailableBooks,
                                "点击重新抽取后 10 秒未出现确定按钮",
                            )
                            preserveOldResult(session, result, taskLabel)
                            break@resultLoop
                        }
                        val (confirmX, confirmY) = SoulmateForgeLayout.confirmationDecisionPoint
                            .scale(confirmation.screenWidth, confirmation.screenHeight)
                        session.tap(confirmX, confirmY, "$taskLabel · 确认重新抽取（固定坐标）")
                        result = resultPageWithin(session, taskLabel, RESULT_WAIT_TIMEOUT_MS)
                            ?: throw AutomationHaltedException("确认抽取后 20 秒仍未出现结果，请检查网络或游戏状态")
                        current = result.before?.stat
                    }
                }
            }
        }
    }

    private fun markBookUnavailable(
        session: AutomationSession,
        slotIndex: Int,
        slotConfig: ForgeSlotConfig,
        tier: ForgeBookTier,
        unavailableBooks: MutableSet<ForgeBookTier>,
        reason: String,
    ) {
        unavailableBooks += tier
        session.log(
            "槽位 ${slotIndex + 1} 的${slotConfig.category.displayName}${tier.displayName}判定为耗尽：$reason；将按“${slotConfig.bookPolicy.displayName}”切换或跳过",
        )
    }

    private suspend fun preserveOldResult(
        session: AutomationSession,
        result: ForgeRollResult,
        taskLabel: String,
    ) {
        val (x, y) = SoulmateForgeLayout.resultBeforeCenter
            .scale(result.snapshot.screenWidth, result.snapshot.screenHeight)
        session.tap(x, y, "$taskLabel · 书籍耗尽，保留抽取前属性（固定坐标）")
        val selected = session.recognizeOptional(
            "$taskLabel · 保留旧属性",
            PRESERVE_OLD_TIMEOUT_MS,
        ) { snapshot ->
            containsText(snapshot, "抽取前") && containsText(snapshot, "抽取后")
        } ?: result.snapshot
        val (decideX, decideY) = SoulmateForgeLayout.resultDecisionPoint
            .scale(selected.screenWidth, selected.screenHeight)
        session.tap(decideX, decideY, "$taskLabel · 保存旧属性并结束本次抽取（固定坐标）")
        awaitMainPage(session, taskLabel, RESULT_WAIT_TIMEOUT_MS)
    }

    private suspend fun mainPage(session: AutomationSession, taskLabel: String? = null): OcrSnapshot =
        session.recognizeValid(
            taskLabel?.let { "$it · 魂友能力锻造主页面" }
                ?: "检查全部词条 · 魂友能力锻造主页面",
        ) { snapshot ->
        ForgePageMatcher.isMainPage(snapshot)
    }

    private suspend fun resultPage(session: AutomationSession, taskLabel: String): ForgeRollResult {
        var parsed: ForgeRollResult? = null
        val snapshot = session.recognizeValid("$taskLabel · 抽取结果") { current ->
            parsed = parseResult(current)
            parsed != null
        }
        return requireNotNull(parsed).copy(snapshot = snapshot)
    }

    private suspend fun resultPageWithin(
        session: AutomationSession,
        taskLabel: String,
        timeoutMs: Long,
    ): ForgeRollResult? {
        var parsed: ForgeRollResult? = null
        val snapshot = session.recognizeOptional("$taskLabel · 抽取结果", timeoutMs) { current ->
            parsed = parseResult(current)
            parsed != null
        } ?: return null
        return requireNotNull(parsed).copy(snapshot = snapshot)
    }

    private suspend fun awaitMainPage(
        session: AutomationSession,
        taskLabel: String,
        timeoutMs: Long,
    ): OcrSnapshot = session.recognizeOptional(
        "$taskLabel · 魂友能力锻造主页面",
        timeoutMs,
    ) { snapshot -> ForgePageMatcher.isMainPage(snapshot) }
        ?: throw AutomationHaltedException("保存属性后 ${timeoutMs / 1000} 秒仍未返回能力锻造主页面")

    internal fun parseSlots(snapshot: OcrSnapshot): List<ForgeSlotState> =
        SoulmateForgeLayout.slotCenters.indices.map { index ->
            if (!config[index].enabled) return@map ForgeSlotState.Locked
            val area = SoulmateForgeLayout.slotBounds(index, snapshot.screenWidth, snapshot.screenHeight)
            val texts = snapshot.lines.filter { area.contains(it.bounds.centerX, it.bounds.centerY) }.map { it.text }
            when {
                ForgeParser.isLocked(texts) -> ForgeSlotState.Locked
                else -> ForgeParser.parseFirst(texts, config.mode)?.let(ForgeSlotState::Unlocked) ?: ForgeSlotState.Empty
            }
        }

    private fun readBookCounts(
        snapshot: OcrSnapshot,
        category: ForgeAttributeCategory,
    ): Map<ForgeBookTier, Int?> = readBookCountsOrNull(snapshot, category)

    private fun readBookCountsOrNull(
        snapshot: OcrSnapshot,
        category: ForgeAttributeCategory,
    ): Map<ForgeBookTier, Int?> = ForgeBookTier.entries.associateWith { tier ->
            val area = SoulmateForgeLayout.bookCountBounds(
                category,
                tier,
                snapshot.screenWidth,
                snapshot.screenHeight,
            )
            snapshot.candidates
                .filter { area.contains(it.bounds.centerX, it.bounds.centerY) }
                .mapNotNull { Regex("\\d+").find(it.text)?.value?.toIntOrNull() }
                .maxOrNull()
        }

    internal fun parseResult(snapshot: OcrSnapshot): ForgeRollResult? {
        val beforeLabel = findText(snapshot, "抽取前") ?: return null
        val afterLabel = findText(snapshot, "抽取后") ?: return null
        val parseable = snapshot.lines.mapNotNull { candidate ->
            ForgeParser.parse(candidate.text, config.mode)?.let { ParsedCandidate(it, candidate) }
        }
        val before = parseable
            .filter {
                it.candidate.bounds.bottom >= beforeLabel.bounds.top &&
                    it.candidate.bounds.centerY < afterLabel.bounds.top
            }
            .sortedWith(
                compareByDescending<ParsedCandidate> { it.candidate.enhanced }
                    .thenBy { abs(it.candidate.bounds.centerY - beforeLabel.bounds.centerY) },
            )
            .firstOrNull()
        val after = parseable
            .filter { it.candidate.bounds.bottom >= afterLabel.bounds.top }
            .sortedWith(
                compareByDescending<ParsedCandidate> { it.candidate.enhanced }
                    .thenBy { abs(it.candidate.bounds.centerY - afterLabel.bounds.centerY) },
            )
            .firstOrNull()
            ?: return null
        return ForgeRollResult(snapshot, before, after)
    }

    private fun findText(snapshot: OcrSnapshot, target: String): OcrCandidate? {
        val normalizedTarget = ForgeParser.normalize(target)
        return snapshot.candidates
            .filter { matchesKeywords(ForgeParser.normalize(it.text), normalizedTarget) }
            .sortedWith(
                compareBy<OcrCandidate> { !ForgeParser.normalize(it.text).contains(normalizedTarget) }
                    .thenByDescending { it.bounds.width },
            )
            .firstOrNull()
    }

    private fun matchesKeywords(text: String, target: String): Boolean {
        if (text.contains(target)) return true
        return when (target) {
            "抽取前" -> text.contains("抽") && text.contains("前")
            "抽取后" -> text.contains("抽") && text.contains("后")
            "重新抽取" -> hasRetryKeywords(text)
            "重新抽取确认" -> hasRetryKeywords(text) && hasConfirmKeywords(text)
            "决定" -> (text.contains("决") || text.contains("确")) && text.contains("定")
            else -> false
        }
    }

    private fun containsText(snapshot: OcrSnapshot, target: String) = findText(snapshot, target) != null

    private fun isRetryConfirmationPage(snapshot: OcrSnapshot): Boolean {
        // The confirmation dialog is often split into several OCR lines and the title
        // may be read as "重抽取确认" or "重新抽...决定". Match semantic tokens rather
        // than requiring one exact string, while keeping a retry token mandatory so a
        // normal forge page cannot be mistaken for this dialog.
        val text = snapshot.lines.joinToString("") { ForgeParser.normalize(it.text) }
        val hasRetryToken = hasRetryKeywords(text)
        val hasConfirmToken = hasConfirmKeywords(text)
        val hasPromptToken = text.contains("放弃") ||
            (text.contains("进行") && (text.contains("吗") || text.contains("？") || text.contains("?")))

        // OCR may lose one or more characters in the title. The dialog is still
        // unambiguous when either side of the prompt survives: “放弃…决定” or
        // “重…抽…进行吗”.
        return (hasRetryToken && (hasConfirmToken || hasPromptToken)) ||
            (text.contains("放弃") && (hasConfirmToken || text.contains("进行")))
    }

    private fun hasRetryKeywords(text: String): Boolean =
        (text.contains("重新") && (text.contains("抽") || text.contains("取"))) ||
            text.contains("重抽") ||
            (text.contains("重") && text.contains("抽"))

    private fun hasConfirmKeywords(text: String): Boolean =
        text.contains("确认") ||
            text.contains("确定") ||
            (text.contains("决") && text.contains("定"))

    private fun Bounds.contains(x: Float, y: Float): Boolean = x in left.toFloat()..right.toFloat() && y in top.toFloat()..bottom.toFloat()

    private fun format(stat: ParsedForgeStat?) = stat?.let {
        "${it.definition.displayName}+${it.value}${if (it.definition.percentage) "%" else ""}"
    } ?: "空词条"

    internal data class ParsedCandidate(
        val stat: ParsedForgeStat,
        val candidate: OcrCandidate,
    )

    internal data class ForgeRollResult(
        val snapshot: OcrSnapshot,
        val before: ParsedCandidate?,
        val after: ParsedCandidate,
    )

    private companion object {
        const val RETRY_CONFIRM_TIMEOUT_MS = 10_000L
        const val RESULT_WAIT_TIMEOUT_MS = 20_000L
        const val PRESERVE_OLD_TIMEOUT_MS = 5_000L
    }
}
