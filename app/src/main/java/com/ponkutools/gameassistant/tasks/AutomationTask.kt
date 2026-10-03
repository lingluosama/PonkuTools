package com.ponkutools.gameassistant.tasks

interface AutomationTask {
    val id: String
    val displayName: String
    suspend fun run(session: AutomationSession)
}

enum class AutomationTaskKind(val displayName: String) {
    SOULMATE_FORGE("魂友能力锻造"),
    ARTIFACT_FORGE("神器能力锻造"),
    DAILY_CLEANUP("清理日常（beta）"),
}

object TaskRegistry {
    const val SOULMATE_ABILITY_FORGE = "soulmate_ability_forge"
    const val DAILY_CLEANUP = "daily_cleanup"
}
