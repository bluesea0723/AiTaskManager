package com.example.aitaskmanager.data

// カレンダーに登録する予定データの「型」
data class ScheduleData(
    val title: String,
    val start: String,
    val end: String,
    val description: String
)