
package com.fin.assistant

/**
 * Converts recognized speech into a command intent.
 *
 * Learned rules are checked first.
 * Unknown commands remain UNKNOWN.
 */
class FinCommandUnderstanding(
    private val learningEngine: FinLearningEngine
) {

    data class CommandResult(
        val intent: String,
        val learned: Boolean,
        val originalText: String
    )

    fun understand(rawCommand: String): CommandResult {
        val text = rawCommand.trim().lowercase()

        if (text.isBlank()) {
            return CommandResult(
                intent = UNKNOWN,
                learned = false,
                originalText = rawCommand
            )
        }

        // 1. Check previously learned rules.
        val learnedIntent = try {
            learningEngine.recallLearnedIntent(text)
        } catch (_: Exception) {
            null
        }

        if (!learnedIntent.isNullOrBlank()) {
            return CommandResult(
                intent = learnedIntent,
                learned = true,
                originalText = rawCommand
            )
        }

        // 2. Use basic built-in command recognition.
        val intent = when {
            containsAny(
                text,
                "কল",
                "call"
            ) -> CALL_CONTACT

            containsAny(
                text,
                "মেসেজ",
                "বার্তা",
                "message"
            ) -> MESSAGE_CONTACT

            containsAny(
                text,
                "খোলো",
                "চালু",
                "open"
            ) -> OPEN_APP

            else -> UNKNOWN
        }

        return CommandResult(
            intent = intent,
            learned = false,
            originalText = rawCommand
        )
    }

    private fun containsAny(
        text: String,
        vararg words: String
    ): Boolean {
        return words.any { text.contains(it) }
    }

    companion object {
        const val OPEN_APP = "OPEN_APP"
        const val CALL_CONTACT = "CALL_CONTACT"
        const val MESSAGE_CONTACT = "MESSAGE_CONTACT"
        const val UNKNOWN = "UNKNOWN"
    }
}
