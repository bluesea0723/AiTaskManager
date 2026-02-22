package com.example.aitaskmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "schedules")
data class ScheduleData(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val start: String,
    val end: String,
    val description: String,
    val isCompleted: Boolean = false,
    val colorId: String? = null,
    // ★追加: どのアカウントの予定かを識別する
    val accountEmail: String = ""
)