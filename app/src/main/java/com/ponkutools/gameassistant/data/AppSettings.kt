package com.ponkutools.gameassistant.data

import android.content.Context
import androidx.core.content.edit
import com.ponkutools.gameassistant.tasks.forge.ForgeAttributeCategory
import com.ponkutools.gameassistant.tasks.forge.ForgeBookPolicy
import com.ponkutools.gameassistant.tasks.forge.ForgeConfig
import com.ponkutools.gameassistant.tasks.forge.ForgeSlotConfig
import com.ponkutools.gameassistant.tasks.forge.ForgeStats
import com.ponkutools.gameassistant.tasks.forge.ForgeMode
import com.ponkutools.gameassistant.tasks.AutomationTaskKind

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadForgeConfig(): ForgeConfig {
        val mode = enumValueOrDefault(preferences.getString(KEY_FORGE_MODE, null), ForgeMode.SOULMATE)
        val legacyCategory = enumValueOrDefault(
            preferences.getString(KEY_FORGE_CATEGORY, null), ForgeAttributeCategory.ATTACK,
        )
        val legacyBookPolicy = enumValueOrDefault(
            preferences.getString(KEY_FORGE_BOOK_POLICY, null), ForgeBookPolicy.GOLD_THEN_SILVER,
        )
        val legacyPreserve = preferences.getBoolean(KEY_FORGE_PRESERVE, true)
        return ForgeConfig(
            mode = mode,
            slots = List(ForgeConfig.SLOT_COUNT) { slotIndex ->
                val category = enumValueOrDefault(
                        preferences.getString(slotKey(slotIndex, KEY_CATEGORY_SUFFIX), null),
                        ForgeAttributeCategory.forMode(mode).firstOrNull() ?: legacyCategory,
                    )
                ForgeSlotConfig(
                    mode = mode,
                    enabled = preferences.getBoolean(
                        slotKey(slotIndex, KEY_ENABLED_SUFFIX), true,
                    ),
                    category = category,
                    targetStatId = preferences.getString(
                        slotKey(slotIndex, KEY_TARGET_STAT_SUFFIX),
                        null,
                    ) ?: ForgeStats.forCategory(mode, category).first().id,
                    alternativeStatIds = (0 until ForgeSlotConfig.MAX_ALTERNATIVES)
                        .mapNotNull { alternativeIndex ->
                            preferences.getString(
                                slotAlternativeKey(slotIndex, alternativeIndex),
                                null,
                            )
                        }
                        .filter { id ->
                            ForgeStats.byId(id)?.let { it.mode == mode && it.category == category } == true
                        }
                        .distinct()
                        .take(ForgeSlotConfig.MAX_ALTERNATIVES),
                    bookPolicy = enumValueOrDefault(
                        preferences.getString(slotKey(slotIndex, KEY_BOOK_POLICY_SUFFIX), null),
                        legacyBookPolicy,
                    ),
                    preserveBetterSameType = preferences.getBoolean(
                        slotKey(slotIndex, KEY_PRESERVE_SUFFIX), legacyPreserve,
                    ),
                    thresholds = ForgeStats.forMode(mode).associate { stat ->
                        stat.id to preferences.getInt(
                            slotThresholdKey(slotIndex, stat.id),
                            preferences.getInt(thresholdKey(stat.id), stat.defaultThreshold),
                        )
                    },
                )
            },
        )
    }

    fun loadTaskKind(): AutomationTaskKind = enumValueOrDefault(
        preferences.getString(KEY_TASK_KIND, null),
        if (loadForgeConfig().mode == ForgeMode.ARTIFACT) {
            AutomationTaskKind.ARTIFACT_FORGE
        } else {
            AutomationTaskKind.SOULMATE_FORGE
        },
    )

    fun saveTaskKind(kind: AutomationTaskKind) {
        preferences.edit { putString(KEY_TASK_KIND, kind.name) }
    }

    fun loadTapDurationMs(): Int = preferences.getInt(
        KEY_TAP_DURATION_MS,
        DEFAULT_TAP_DURATION_MS,
    ).coerceIn(MIN_TAP_DURATION_MS, MAX_TAP_DURATION_MS)

    fun saveTapDurationMs(durationMs: Int) {
        preferences.edit {
            putInt(
                KEY_TAP_DURATION_MS,
                durationMs.coerceIn(MIN_TAP_DURATION_MS, MAX_TAP_DURATION_MS),
            )
        }
    }

    fun saveForgeConfig(config: ForgeConfig) {
        preferences.edit {
            config.slots.forEachIndexed { slotIndex, slot ->
                putBoolean(slotKey(slotIndex, KEY_ENABLED_SUFFIX), slot.enabled)
                putString(slotKey(slotIndex, KEY_CATEGORY_SUFFIX), slot.category.name)
                putString(slotKey(slotIndex, KEY_TARGET_STAT_SUFFIX), slot.targetStat().id)
                repeat(ForgeSlotConfig.MAX_ALTERNATIVES) { alternativeIndex ->
                    slot.alternativeStatIds.getOrNull(alternativeIndex)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { putString(slotAlternativeKey(slotIndex, alternativeIndex), it) }
                        ?: remove(slotAlternativeKey(slotIndex, alternativeIndex))
                }
                putString(slotKey(slotIndex, KEY_BOOK_POLICY_SUFFIX), slot.bookPolicy.name)
                putBoolean(slotKey(slotIndex, KEY_PRESERVE_SUFFIX), slot.preserveBetterSameType)
                slot.thresholds.forEach { (id, value) -> putInt(slotThresholdKey(slotIndex, id), value) }
            }
            putString(KEY_FORGE_MODE, config.mode.name)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun thresholdKey(id: String) = "forge_threshold_$id"
    private fun slotKey(slotIndex: Int, suffix: String) = "forge_slot_${slotIndex + 1}_$suffix"
    private fun slotThresholdKey(slotIndex: Int, id: String) = "forge_slot_${slotIndex + 1}_threshold_$id"
    private fun slotAlternativeKey(slotIndex: Int, alternativeIndex: Int) =
        "forge_slot_${slotIndex + 1}_alternative_${alternativeIndex + 1}"

    companion object {
        const val DEFAULT_TAP_DURATION_MS = 500
        const val MIN_TAP_DURATION_MS = 100
        const val MAX_TAP_DURATION_MS = 1_500
        const val TAP_DURATION_STEP_MS = 50

        const val PREFERENCES_NAME = "automation_settings"
        private const val KEY_TAP_DURATION_MS = "tap_duration_ms"
        const val KEY_FORGE_CATEGORY = "forge_category"
        const val KEY_FORGE_MODE = "forge_mode"
        const val KEY_FORGE_BOOK_POLICY = "forge_book_policy"
        const val KEY_FORGE_PRESERVE = "forge_preserve_better"
        const val KEY_TASK_KIND = "automation_task_kind"
        const val KEY_ENABLED_SUFFIX = "enabled"
        const val KEY_CATEGORY_SUFFIX = "category"
        const val KEY_BOOK_POLICY_SUFFIX = "book_policy"
        const val KEY_PRESERVE_SUFFIX = "preserve_better"
        const val KEY_TARGET_STAT_SUFFIX = "target_stat"
    }
}

