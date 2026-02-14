package com.example.aitaskmanager // ★自分のパッケージ名に合わせてください

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// GeminiからのJSONデータを受け取るための型
data class AiSchedule(
    val title: String,
    val start: String,
    val end: String,
    val description: String
)

class CalendarHelper {

    // 1. JSON文字列を解析して、リストにする関数
    fun parseJson(jsonString: String): List<AiSchedule> {
        return try {
            // Geminiが ```json ... ``` のようなマークダウン記法で返すことがあるので削除する
            val cleanJson = jsonString
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val listType = object : TypeToken<List<AiSchedule>>() {}.type
            Gson().fromJson(cleanJson, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 2. Googleカレンダーに予定を書き込む関数
    suspend fun addEventToCalendar(context: Context, account: GoogleSignInAccount, schedule: AiSchedule): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 認証情報の作成
                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf(CalendarScopes.CALENDAR)
                )
                credential.selectedAccount = account.account

                // カレンダーサービスの作成
                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("MyPlannerApp").build()

                // イベント情報の作成
                val event = Event().apply {
                    summary = schedule.title
                    description = schedule.description

                    // 開始時刻
                    start = EventDateTime().apply {
                        // もし "+09:00" が付いていなければ、後ろにくっつける
                        val jstStart = if (schedule.start.contains("+")) schedule.start else schedule.start + "+09:00"
                        dateTime = DateTime(jstStart)
                        timeZone = "Asia/Tokyo"
                    }

                    // ★ここも同様に修正
                    end = EventDateTime().apply {
                        val jstEnd = if (schedule.end.contains("+")) schedule.end else schedule.end + "+09:00"
                        dateTime = DateTime(jstEnd)
                        timeZone = "Asia/Tokyo"
                    }
                }

                // API実行（書き込み）
                service.events().insert("primary", event).execute()
                true // 成功
            } catch (e: Exception) {
                e.printStackTrace()
                false // 失敗
            }
        }
    }
}