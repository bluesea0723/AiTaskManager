package com.example.aitaskmanager.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// ▼ (既存) スケジュール用のDAO
@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE start >= :startStr AND start < :endStr ORDER BY start ASC")
    suspend fun getEventsInRange(startStr: String, endStr: String): List<ScheduleData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleData>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleData)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}

// ▼ ★追加: チャットメッセージ用のDAO
@Dao
interface ChatMessageDao {
    // 全メッセージを古い順（時間順）に取得
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>

    // メッセージを保存
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    // 全削除（履歴クリア機能用）
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}

// ▼ ★変更: entitiesにChatMessageを追加し、versionを「2」に変更
@Database(entities = [ScheduleData::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    // ★追加: チャット用のDAOを呼び出せるようにする
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule_database"
                )
                    // ★追加: データベースの構造が変わった時に、クラッシュせずに作り直す設定
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}