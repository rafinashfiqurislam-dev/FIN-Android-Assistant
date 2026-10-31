
package com.fin.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class FinExperience(
    val transcript: String,
    val intent: String,
    val outcome: String,
    val correction: String,
    val createdAt: Long
)

class FinExperienceStore(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        "fin_experience.db",
        null,
        1
    ) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE experiences (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transcript TEXT NOT NULL,
                intent TEXT NOT NULL,
                outcome TEXT NOT NULL,
                correction TEXT DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE learned_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trigger_text TEXT NOT NULL,
                intent TEXT NOT NULL,
                evidence_count INTEGER NOT NULL DEFAULT 1,
                success_count INTEGER NOT NULL DEFAULT 0,
                confidence REAL NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                UNIQUE(trigger_text, intent)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Preserve existing learning data.
    }

    @Synchronized
    fun recordExperience(
        transcript: String,
        intent: String,
        outcome: String,
        correction: String = ""
    ): Long {
        if (transcript.isBlank()) return -1L

        val values = ContentValues().apply {
            put("transcript", transcript.take(1000))
            put("intent", intent.take(100))
            put("outcome", outcome.take(100))
            put("correction", correction.take(1000))
            put("created_at", System.currentTimeMillis())
        }

        return writableDatabase.insert(
            "experiences",
            null,
            values
        )
    }

    @Synchronized
    fun recordRuleEvidence(
        trigger: String,
        intent: String,
        successful: Boolean
    ): Double {
        val key = trigger.trim().lowercase()
        if (key.isEmpty() || intent.isBlank()) return 0.0

        val db = writableDatabase

        val old = db.rawQuery(
            """
            SELECT evidence_count, success_count
            FROM learned_rules
            WHERE trigger_text = ? AND intent = ?
            """.trimIndent(),
            arrayOf(key.take(300), intent.take(100))
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0) to cursor.getInt(1)
            } else null
        }

        val evidence = (old?.first ?: 0) + 1
        val successes = (old?.second ?: 0) +
            if (successful) 1 else 0

        val confidence =
            (successes + 1.0) / (evidence + 2.0)

        val values = ContentValues().apply {
            put("trigger_text", key.take(300))
            put("intent", intent.take(100))
            put("evidence_count", evidence)
            put("success_count", successes)
            put("confidence", confidence)
            put("updated_at", System.currentTimeMillis())
        }

        db.insertWithOnConflict(
            "learned_rules",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        return confidence
    }

    fun findMatchingRules(
        text: String,
        minimumConfidence: Double = 0.75
    ): List<Pair<String, String>> {
        val input = text.trim().lowercase()
        if (input.isEmpty()) return emptyList()

        val matches = mutableListOf<Pair<String, String>>()

        readableDatabase.rawQuery(
            """
            SELECT trigger_text, intent
            FROM learned_rules
            WHERE confidence >= ?
            ORDER BY confidence DESC
            LIMIT 30
            """.trimIndent(),
            arrayOf(minimumConfidence.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val trigger = cursor.getString(0)
                val intent = cursor.getString(1)

                if (input.contains(trigger)) {
                    matches.add(trigger to intent)
                }
            }
        }

        return matches
    }

    fun getRecentExperiences(
        limit: Int = 20
    ): List<FinExperience> {
        val safeLimit = limit.coerceIn(1, 200)
        val result = mutableListOf<FinExperience>()

        readableDatabase.rawQuery(
            """
            SELECT transcript, intent, outcome, correction, created_at
            FROM experiences
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    FinExperience(
                        transcript = cursor.getString(0),
                        intent = cursor.getString(1),
                        outcome = cursor.getString(2),
                        correction = cursor.getString(3),
                        createdAt = cursor.getLong(4)
                    )
                )
            }
        }

        return result
    }

    @Synchronized
    fun clearLearningData() {
        val db = writableDatabase

        db.beginTransaction()
        try {
            db.delete("experiences", null, null)
            db.delete("learned_rules", null, null)
            db.setTransactionSuccessful()
                      } finally {
            db.endTransaction()
        }
    }
}
