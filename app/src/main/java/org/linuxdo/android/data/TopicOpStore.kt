package org.linuxdo.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TopicOpRecord(
    val topicId: Long,
    val excerpt: String?,
    val coverImageUrl: String?,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 工业级轻量本地持久化存储。
 * 基于 Android 原生 SQLite 构建，无需任何第三方库或编译期注解依赖。
 * 用于 0ms 瞬间还原历史刷过或嗅探过的首楼正文两行摘要与高清封面配图。
 */
class TopicOpStore(context: Context) {
    private val dbHelper = DatabaseHelper(context.applicationContext)
    private val lock = Any()

    companion object {
        private const val DB_NAME = "linuxdo_topic_op.db"
        private const val DB_VERSION = 2
        private const val TABLE_NAME = "topic_op"
        private const val COL_TOPIC_ID = "topic_id"
        private const val COL_EXCERPT = "excerpt"
        private const val COL_COVER_IMAGE = "cover_image"
        private const val COL_UPDATED_AT = "updated_at"
        private const val MAX_RECORDS = 1500
    }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    $COL_TOPIC_ID INTEGER PRIMARY KEY,
                    $COL_EXCERPT TEXT,
                    $COL_COVER_IMAGE TEXT,
                    $COL_UPDATED_AT INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_topic_op_updated ON $TABLE_NAME ($COL_UPDATED_AT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    fun get(topicId: Long): TopicOpRecord? {
        if (topicId <= 0L) return null
        synchronized(lock) {
            return try {
                val db = dbHelper.readableDatabase
                db.query(
                    TABLE_NAME,
                    arrayOf(COL_TOPIC_ID, COL_EXCERPT, COL_COVER_IMAGE, COL_UPDATED_AT),
                    "$COL_TOPIC_ID = ?",
                    arrayOf(topicId.toString()),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        TopicOpRecord(
                            topicId = cursor.getLong(0),
                            excerpt = cursor.getString(1),
                            coverImageUrl = cursor.getString(2),
                            updatedAt = cursor.getLong(3),
                        )
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun put(topicId: Long, excerpt: String?, coverImageUrl: String?) {
        if (topicId <= 0L || (excerpt.isNullOrBlank() && coverImageUrl.isNullOrBlank())) return
        synchronized(lock) {
            try {
                val db = dbHelper.writableDatabase
                val values = ContentValues().apply {
                    put(COL_TOPIC_ID, topicId)
                    put(COL_EXCERPT, excerpt)
                    put(COL_COVER_IMAGE, coverImageUrl)
                    put(COL_UPDATED_AT, System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                trimLruLocked(db)
            } catch (_: Exception) {}
        }
    }

    fun getAll(topicIds: Collection<Long>): Map<Long, TopicOpRecord> {
        val validIds = topicIds.filter { it > 0L }
        if (validIds.isEmpty()) return emptyMap()
        synchronized(lock) {
            return try {
                val db = dbHelper.readableDatabase
                val result = mutableMapOf<Long, TopicOpRecord>()
                validIds.chunked(500).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    val args = chunk.map { it.toString() }.toTypedArray()
                    db.query(
                        TABLE_NAME,
                        arrayOf(COL_TOPIC_ID, COL_EXCERPT, COL_COVER_IMAGE, COL_UPDATED_AT),
                        "$COL_TOPIC_ID IN ($placeholders)",
                        args,
                        null,
                        null,
                        null,
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            result[id] = TopicOpRecord(
                                topicId = id,
                                excerpt = cursor.getString(1),
                                coverImageUrl = cursor.getString(2),
                                updatedAt = cursor.getLong(3),
                            )
                        }
                    }
                }
                result
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    private fun trimLruLocked(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                DELETE FROM $TABLE_NAME WHERE $COL_TOPIC_ID NOT IN (
                    SELECT $COL_TOPIC_ID FROM $TABLE_NAME ORDER BY $COL_UPDATED_AT DESC LIMIT $MAX_RECORDS
                )
                """.trimIndent(),
            )
        } catch (_: Exception) {}
    }
}
