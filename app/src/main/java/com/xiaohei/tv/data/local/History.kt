package com.xiaohei.tv.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history", primaryKeys = ["providerId", "videoId"])
data class HistoryEntry(
    val videoId: String,
    val title: String,
    val poster: String,
    val typeName: String = "",
    val remarks: String = "",
    val sourceIndex: Int = 0,
    val episodeIndex: Int = 0,
    val episodeName: String = "",
    val episodeHref: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val providerId: String = "",
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE providerId = :providerId AND videoId = :videoId LIMIT 1")
    suspend fun find(providerId: String, videoId: String): HistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE providerId = :providerId AND videoId = :videoId")
    suspend fun delete(providerId: String, videoId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntry::class], version = 3, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: HistoryDatabase? = null

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN providerId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE history_new (videoId TEXT NOT NULL, title TEXT NOT NULL, poster TEXT NOT NULL, typeName TEXT NOT NULL, remarks TEXT NOT NULL, sourceIndex INTEGER NOT NULL, episodeIndex INTEGER NOT NULL, episodeName TEXT NOT NULL, episodeHref TEXT NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL, providerId TEXT NOT NULL, PRIMARY KEY(providerId, videoId))")
                db.execSQL("INSERT INTO history_new SELECT videoId, title, poster, typeName, remarks, sourceIndex, episodeIndex, episodeName, episodeHref, positionMs, durationMs, updatedAt, CASE WHEN providerId = '' THEN 'xiaoheimi' ELSE providerId END FROM history")
                db.execSQL("DROP TABLE history")
                db.execSQL("ALTER TABLE history_new RENAME TO history")
            }
        }

        fun get(context: Context): HistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "xiaohei.db",
                )
                    .addMigrations(migration1To2, migration2To3)
                    .build()
                    .also { instance = it }
            }
    }
}
