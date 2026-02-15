package com.example.aitaskmanager.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// ▼ DAO: データベースへの命令文
@Dao
interface ScheduleDao {
    // 指定した期間の予定を取得
    @Query("SELECT * FROM schedules WHERE start >= :startStr AND start < :endStr ORDER BY start ASC")
    suspend fun getEventsInRange(startStr: String, endStr: String): List<ScheduleData>

    // 予定を保存 (同じIDなら上書き)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleData>)

    // 1件保存
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleData)

    // 全削除 (デバッグ用やリセット用)
    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}

// ▼ Database: データベース本体の設定
@Database(entities = [ScheduleData::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule_database" // データベースファイル名
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}