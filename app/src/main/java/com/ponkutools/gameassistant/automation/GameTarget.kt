package com.ponkutools.gameassistant.automation

import android.content.Context
import android.content.Intent

object GameTarget {
    const val DISPLAY_NAME = "CraveSaga（神绊的导师）"
    const val PACKAGE_NAME = "games.johren.cravesaga.erolabsch.gg_x_a"

    fun bringToForeground(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) ?: return false
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        context.startActivity(launchIntent)
        return true
    }
}

