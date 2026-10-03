package com.ponkutools.gameassistant.logging

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class LogEntry(
    val timestamp: String,
    val message: String,
) {
    override fun toString(): String = "$timestamp  $message"
}

object AutomationLogger {
    private const val MAX_VISIBLE_ENTRIES = 250
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
    private val writer = Executors.newSingleThreadExecutor()
    private val mutableEntries = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries: StateFlow<List<LogEntry>> = mutableEntries.asStateFlow()

    @Synchronized
    fun log(context: Context, message: String) {
        val entry = LogEntry(formatter.format(Date()), message)
        mutableEntries.value = (mutableEntries.value + entry).takeLast(MAX_VISIBLE_ENTRIES)
        writer.execute {
            runCatching {
                logFile(context).appendText(entry.toString() + System.lineSeparator())
            }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        mutableEntries.value = emptyList()
        writer.execute { runCatching { logFile(context).writeText("") } }
    }

    fun compactVerboseHistory(context: Context) {
        writer.execute {
            runCatching {
                val file = logFile(context)
                if (!file.exists()) return@runCatching
                val filtered = file.readLines()
                    .filterNot(::isVerboseHistoryLine)
                    .takeLast(MAX_HISTORY_ENTRIES)
                file.writeText(filtered.joinToString(System.lineSeparator(), postfix = if (filtered.isEmpty()) "" else System.lineSeparator()))
            }
        }
    }

    fun logFile(context: Context): File {
        val directory = File(context.filesDir, "logs").apply { mkdirs() }
        return File(directory, "automation.log")
    }

    private fun isVerboseHistoryLine(line: String): Boolean =
        line.contains("  OCR “") ||
            line.contains("  OCR 完成：") ||
            line.contains("  OCR[") ||
            line.contains("  应用已启动，日志文件：")

    private const val MAX_HISTORY_ENTRIES = 1_000
}

