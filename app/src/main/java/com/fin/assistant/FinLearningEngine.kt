
package com.fin.assistant

import android.content.Context

/**
 * FIN's experience-based learning coordinator.
 *
 * Learns from explicit corrections and confirmed outcomes.
 * A single observation never becomes a trusted rule.
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

        if (userCommand.isBlank() || detectedIntent.isBlank()) {
            return LearningResult(
                false, 0.0, false,
                "Command information is incomplete."
            )
        }

        val outcome = if (successful) "SUCCESS" else "FAILURE"

        val savedId = store.recordExperience(
            transcript = userCommand,
            intent = detectedIntent,
            outcome = outcome
        )

        if (savedId < 0) {
            return LearningResult(
                false, 0.0, false,
                "Experience could not be saved."
            )
        }

        return LearningResult(
            true, 0.0, false,
            "Experience recorded."
        )
    }

    /**
     * Learn from an explicit user correction.
     *
     * Example:
     * Original command: "ইউটিউবে গজল চালাও"
     * Corrected intent: "SEARCH_YOUTUBE"
     *
     * The corrected rule needs repeated evidence
     * before it becomes trusted.
     */
    fun learnFromCorrection(
        originalCommand: String,
        correctedIntent: String
    ): LearningResult {

        val command = originalCommand.trim()
        val intent = correctedIntent.trim()

        if (command.isEmpty() || intent.isEmpty()) {
            return LearningResult(
                false, 0.0, false,
                "Correction information is incomplete."
            )
        }

        val savedId = store.recordExperience(
            transcript = command,
            intent = intent,
            outcome = "USER_CORRECTION",
            correction = intent
        )

        if (savedId < 0) {
            return LearningResult(
                false, 0.0, false,
                "Correction could not be saved."
            )
        }

        val confidence = store.recordRuleEvidence(
            trigger = command,
            intent = intent,
            successful = true
        )

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
     * Search previously learned rules.
     */
    fun recallLearnedIntent(
        currentCommand: String
    ): String? {

        if (currentCommand.isBlank()) return null

        val matches = store.findMatchingRules(
            text = currentCommand,
            minimumConfidence = TRUST_THRESHOLD
        )

        return matches.firstOrNull()?.second
    }

    /**
     * Read recent experience history.
     */
    fun getRecentHistory(
        limit: Int = 20
    ): List<FinExperience> {
        return store.getRecentExperiences(limit)
    }

    /**
     * Delete all locally stored learning data.
     */
    fun clearAllLearning() {
        store.clearLearningData()
    }

    companion object {
        private const val TRUST_THRESHOLD = 0.75
    }
}
