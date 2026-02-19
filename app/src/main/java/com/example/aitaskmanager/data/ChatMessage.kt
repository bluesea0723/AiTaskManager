package com.example.aitaskmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// ★ @Entity をつけてデータベースのテーブルにする
@Entity(tableName = "chat_messages")
data class ChatMessage(
    // ★ IDを主キー（PrimaryKey）に設定
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)