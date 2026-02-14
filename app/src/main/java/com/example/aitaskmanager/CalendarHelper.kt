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
    suspend fun addEventToCalendar(
        context: Context,
        account: GoogleSignInAccount,
        schedule: AiSchedule
    ): Boolean {
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
                        val jstStart =
                            if (schedule.start.contains("+")) schedule.start else schedule.start + "+09:00"
                        dateTime = DateTime(jstStart)
                        timeZone = "Asia/Tokyo"
                    }

                    // ★ここも同様に修正
                    end = EventDateTime().apply {
                        val jstEnd =
                            if (schedule.end.contains("+")) schedule.end else schedule.end + "+09:00"
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

    // 3. 指定した日の予定を取得する関数（これを追加！）
    suspend fun fetchEvents(context: Context, account: GoogleSignInAccount): String {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf(CalendarScopes.CALENDAR)
                )
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("MyPlannerApp").build()

                // ★ここを修正：日本時間の「今日の00:00:00」を正確に作る
                val jstZone = java.util.TimeZone.getTimeZone("Asia/Tokyo")
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                val startDateTime = DateTime(calendar.time) // 今日の00:00 (JST)

                // 明日の00:00 (JST) を作るために1日進める
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val endDateTime = DateTime(calendar.time)

                // API実行
                val events = service.events().list("primary")
                    .setTimeMin(startDateTime)
                    .setTimeMax(endDateTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                val items = events.items
                if (items.isEmpty()) {
                    "今日の予定はありません。"
                } else {
                    val sb = StringBuilder("【今日の予定】\n")
                    for (event in items) {
                        // 開始時刻の表示も見やすく整形
                        val start = event.start.dateTime ?: event.start.date
                        val summary = event.summary
                        sb.append("- $start : $summary\n")
                    }
                    sb.toString()
                }
            } catch (e: Exception) {
                "予定の取得に失敗しました: ${e.message}"
            }
        }
    }
}