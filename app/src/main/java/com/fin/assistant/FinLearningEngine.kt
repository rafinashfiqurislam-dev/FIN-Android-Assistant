
package com.fin.assistant

import android.content.Context

/**
 * FIN's experience-based learning coordinator.
 *
 * Stores command outcomes and learns only from supported,
 * explicit user corrections.
 */
class FinLearningEngine(context: Context) {

    private val store = FinExperienceStore(context)

    data class LearningResult(
        val saved: Boolean,
        val confidence: Double,
        val trusted: Boolean,
        val message: String
    )

    /**
     * Save an observed command and its actual outcome.
     */
    fun observeOutcome(
        userCommand: String,
        detectedIntent: String,
        successful: Boolean
    ): LearningResult {

        val command = userCommand.trim()
        val intent = detectedIntent.trim()

        if (command.isEmpty() || intent.isEmpty()) {
            return result(
                false,
                message = "Command information is incomplete."
            )
        }

        val outcome = if (successful) "SUCCESS" else "FAILURE"

        val savedId = try {
            store.recordExperience(
                transcript = command,
                intent = intent,
                outcome = outcome
            )
        } catch (_: Exception) {
            -1L
        }

        if (savedId < 0) {
            return result(
                false,
                message = "Experience could not be saved."
            )
        }

        return result(
            true,
            message = "Experience recorded."
        )
    }

    /**
     * Learn only from supported explicit corrections.
     *
     * Supported intents:
     * OPEN_APP, CALL_CONTACT, MESSAGE_CONTACT
     */
    fun learnFromCorrection(
        originalCommand: String,
        correctedIntent: String
    ): LearningResult {

        val command = originalCommand.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")

        val intent = correctedIntent.trim().uppercase()

        if (command.isBlank()) {
            return result(
                false,
                message = "Original command is required."
            )
        }

        if (intent !in SUPPORTED_INTENTS) {
            return result(
                false,
                message = "Unsupported correction intent."
            )
        }

        val savedId = try {
            store.recordExperience(
                transcript = command,
                intent = intent,
                outcome = "USER_CORRECTION",
                correction = intent
            )
        } catch (_: Exception) {
            -1L
        }

        if (savedId < 0) {
            return result(
                false,
                message = "Correction could not be saved."
            )
        }

        val confidence = try {
            store.recordRuleEvidence(
                trigger = command,
                intent = intent,
                successful = true
            )
        } catch (_: Exception) {
            return result(
                true,
                message = "Correction saved, but rule evidence could not be updated."
            )
        }

        val trusted = confidence >= TRUST_THRESHOLD

        return LearningResult(
            saved = true,
            confidence = confidence,
            trusted = trusted,
            message = if (trusted) {
                "Correction learned with sufficient evidence."
            } else {
                "Correction saved. More evidence is needed."
            }
        )
    }

    /**
     * Recall a previously trusted learned intent.
     */
    fun recallLearnedIntent(
        currentCommand: String
    ): String? {

        val command = currentCommand.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")

        if (command.isBlank()) return null

        return try {
            store.findMatchingRules(
                text = command,
                minimumConfidence = TRUST_THRESHOLD
            )
                .firstOrNull()
                ?.second
                ?.takeIf { it in SUPPORTED_INTENTS }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Read recent experience history.
     */
    fun getRecentHistory(
        limit: Int = 20
    ): List<FinExperience> {
        return try {
            store.getRecentExperiences(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Delete all locally stored learning data.
     */
    fun clearAllLearning() {
        store.clearLearningData()
    }

    private fun result(
        saved: Boolean,
        confidence: Double = 0.0,
        trusted: Boolean = false,
        message: String
    ) = LearningResult(
        saved = saved,
        confidence = confidence,
        trusted = trusted,
        message = message
    )

    companion object {
        private const val TRUST_THRESHOLD = 0.75

        private val SUPPORTED_INTENTS = setOf(
            "OPEN_APP",
            "CALL_CONTACT",
            "MESSAGE_CONTACT"
        )
    }
}
