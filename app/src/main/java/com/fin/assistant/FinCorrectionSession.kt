
package com.fin.assistant

/**
 * Handles an explicit voice correction session.
 *
 * A correction is learned only after the user
 * clearly identifies the intended supported action.
 */
class FinCorrectionSession(
    private val learningEngine: FinLearningEngine
) {

    private var pendingCommand: String? = null

    data class CorrectionResponse(
        val completed: Boolean,
        val message: String,
        val result: FinLearningEngine.LearningResult? = null
    )

    fun begin(originalCommand: String): CorrectionResponse {
        val command = originalCommand.trim()

        if (command.isBlank()) {
            return CorrectionResponse(
                completed = false,
                message = "No previous command is available."
            )
        }

        pendingCommand = command

        return CorrectionResponse(
            completed = false,
            message = "Please say the correct action: open app, make a call, or send a message."
        )
    }

    fun accept(correctedSpeech: String): CorrectionResponse {
        val original = pendingCommand
            ?: return CorrectionResponse(
                completed = true,
                message = "No correction session is active."
            )

        val intent = identifyIntent(correctedSpeech)

        if (intent == null) {
            return CorrectionResponse(
                completed = false,
                message = "Action not recognized. Please say open app, make a call, or send a message."
            )
        }

        val result = learningEngine.learnFromCorrection(
            originalCommand = original,
            correctedIntent = intent
        )

        pendingCommand = null

        return CorrectionResponse(
            completed = true,
            message = result.message,
            result = result
        )
    }

    fun cancel() {
        pendingCommand = null
    }

    fun isActive(): Boolean = pendingCommand != null

    private fun identifyIntent(speech: String): String? {
        val text = speech.trim().lowercase()

        if (text.isBlank()) return null

        return when {
            containsAny(
                text,
                "অ্যাপ খোলা",
                "অ্যাপ ওপেন",
                "অ্যাপ চালু",
                "open app"
            ) -> FinCommandUnderstanding.OPEN_APP

            containsAny(
                text,
                "কল করা",
                "ফোন করা",
                "make a call",
                "call contact"
            ) -> FinCommandUnderstanding.CALL_CONTACT

            containsAny(
                text,
                "মেসেজ করা",
                "বার্তা পাঠানো",
                "send a message",
                "send message"
            ) -> FinCommandUnderstanding.MESSAGE_CONTACT

            else -> null
        }
    }

    private fun containsAny(
        text: String,
        vararg phrases: String
    ): Boolean {
        return phrases.any { text.contains(it) }
    }
}
