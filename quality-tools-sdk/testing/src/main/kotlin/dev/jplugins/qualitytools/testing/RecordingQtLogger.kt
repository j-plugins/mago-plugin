package dev.jplugins.qualitytools.testing

import dev.jplugins.qualitytools.core.context.QtLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Captures every `QtLogger.log(...)` invocation for later assertion.
 * Used to verify the "one-shot per (toolId, key) dedup" contract from
 * phase 06 acceptance.
 */
public class RecordingQtLogger : QtLogger {

    public data class Entry(
        val level: String,
        val message: String,
        val throwable: Throwable?,
    )

    private val entries = CopyOnWriteArrayList<Entry>()

    override fun log(level: String, message: String, throwable: Throwable?) {
        entries += Entry(level, message, throwable)
    }

    public fun all(): List<Entry> = entries.toList()

    public fun assertLoggedOnce(level: String, contains: String) {
        val matches = entries.filter { it.level == level && contains in it.message }
        check(matches.size == 1) {
            "Expected exactly one $level log containing '$contains'; saw ${matches.size}:\n" +
                matches.joinToString("\n") { it.message }
        }
    }

    public fun clear() {
        entries.clear()
    }
}
