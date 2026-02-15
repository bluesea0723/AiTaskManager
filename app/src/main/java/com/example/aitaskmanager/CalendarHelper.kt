package com.example.aitaskmanager // ★あなたのパッケージ名に合わせてください


import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
// AndroidHttp はここに入っています
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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

// 予定データの形式
data class ScheduleData(
    val title: String,
    val start: String,
    val end: String,
    val description: String
)

class CalendarHelper {

    // 1. JSON文字列を解析してリストにする関数
    fun parseJson(jsonString: String): List<ScheduleData> {
        return try {
            val gson = Gson()
            val listType = object : TypeToken<List<ScheduleData>>() {}.type
            gson.fromJson(jsonString, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 2. カレンダーに予定を追加する関数
    suspend fun addEventToCalendar(
        context: Context,
        account: GoogleSignInAccount,
        schedule: ScheduleData
    ): Boolean {
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
                ).setApplicationName("AiTaskManager").build()

                val event = Event().apply {
                    summary = schedule.title
                    description = schedule.description

                    // 日本時間の補正(+09:00)がない場合は追加する
                    start = EventDateTime().apply {
                        val jstStart = if (schedule.start.contains("+")) schedule.start else schedule.start + "+09:00"
                        dateTime = DateTime(jstStart)
                        timeZone = "Asia/Tokyo"
                    }
                    end = EventDateTime().apply {
                        val jstEnd = if (schedule.end.contains("+")) schedule.end else schedule.end + "+09:00"
                        dateTime = DateTime(jstEnd)
                        timeZone = "Asia/Tokyo"
                    }
                }

                service.events().insert("primary", event).execute()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // 3. 今日の予定を取得する関数
    suspend fun fetchEvents(context: Context, account: GoogleSignInAccount): String {
        return withContext(Dispatchers.IO) {
            try {
                // 読み取り時も権限は "CALENDAR" で統一（エラー回避のため）
                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf(CalendarScopes.CALENDAR)
                )
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("AiTaskManager").build()

                // 日本時間の今日の00:00〜翌00:00を設定
                val jstZone = java.util.TimeZone.getTimeZone("Asia/Tokyo")
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startDateTime = DateTime(calendar.time)

                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val endDateTime = DateTime(calendar.time)

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
                        val start = event.start.dateTime ?: event.start.date
                        val summary = event.summary
                        sb.append("- $start : $summary\n")
                    }
                    sb.toString()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                "予定の取得に失敗しました: ${e.localizedMessage}"
            }
        }
    }

    // 4. 今日の日付文字列を取得 (AIへのコンテキスト用)
    fun getTodayDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }
}