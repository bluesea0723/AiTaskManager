package com.example.aitaskmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// ★ @Entity をつけるとデータベースの「テーブル」になります
@Entity(tableName = "schedules")
data class ScheduleData(
    // ★ IDを追加 (GoogleカレンダーのIDを入れる。なければランダム生成)
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val start: String,
    val end: String,
    val description: String
)