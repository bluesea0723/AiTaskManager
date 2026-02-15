package com.example.aitaskmanager.logic

import android.content.Context
import com.example.aitaskmanager.data.ScheduleData
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
import java.util.TimeZone

class CalendarHelper {

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

    suspend fun addEventToCalendar(
        context: Context,
        account: GoogleSignInAccount,
        schedule: ScheduleData
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(context, listOf(CalendarScopes.CALENDAR))
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("AiTaskManager").build()

                val event = Event().apply {
                    summary = schedule.title
                    description = schedule.description
                    // 日本時間の補正
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

    // ★修正: 今後1週間の予定を日本時間で取得する
    suspend fun fetchEvents(context: Context, account: GoogleSignInAccount): String {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(context, listOf(CalendarScopes.CALENDAR))
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("AiTaskManager").build()

                // 期間設定: 今日の00:00 〜 7日後
                val jstZone = TimeZone.getTimeZone("Asia/Tokyo")
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startDateTime = DateTime(calendar.time)

                calendar.add(java.util.Calendar.DAY_OF_MONTH, 7) // 1週間分取得
                val endDateTime = DateTime(calendar.time)

                val events = service.events().list("primary")
                    .setTimeMin(startDateTime)
                    .setTimeMax(endDateTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                val items = events.items
                if (items.isEmpty()) {
                    "今後1週間の予定はありません。"
                } else {
                    val sb = StringBuilder("【今後1週間の予定】\n")
                    // ★重要: 表示フォーマットを日本時間(Asia/Tokyo)に強制固定
                    val format = java.text.SimpleDateFormat("MM/dd(E) HH:mm", java.util.Locale.JAPAN)
                    format.timeZone = jstZone // これで世界標準時になるのを防ぐ

                    val dayFormat = java.text.SimpleDateFormat("MM/dd(E)", java.util.Locale.JAPAN)
                    dayFormat.timeZone = jstZone

                    for (event in items) {
                        val start = event.start.dateTime
                        val date = event.start.date

                        val timeStr = if (start != null) {
                            // 時間指定あり
                            format.format(java.util.Date(start.value))
                        } else {
                            // 終日予定
                            dayFormat.format(java.util.Date(date.value)) + " [終日]"
                        }

                        val summary = event.summary
                        sb.append("- $timeStr : $summary\n")
                    }
                    sb.toString()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                "予定の取得に失敗しました: ${e.localizedMessage}"
            }
        }
    }

    // 今日の日付を取得 (AIコンテキスト用)
    fun getTodayDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd (E)", java.util.Locale.JAPAN)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return dateFormat.format(java.util.Date())
    }

    // ★追加: 指定した年月の予定を「リスト」で返す関数
    suspend fun fetchMonthlyEvents(context: Context, account: GoogleSignInAccount, year: Int, month: Int): List<ScheduleData> {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(context, listOf(CalendarScopes.CALENDAR))
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("AiTaskManager").build()

                // 月の初め (1日 00:00)
                val jstZone = java.util.TimeZone.getTimeZone("Asia/Tokyo")
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(year, month - 1, 1, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startDateTime = DateTime(calendar.time)

                // 月の終わり (翌月の1日 00:00)
                calendar.add(java.util.Calendar.MONTH, 1)
                val endDateTime = DateTime(calendar.time)

                val events = service.events().list("primary")
                    .setTimeMin(startDateTime)
                    .setTimeMax(endDateTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                val items = events.items ?: emptyList()
                val resultList = mutableListOf<ScheduleData>()

                // 表示用にデータを変換
                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.JAPAN)

                for (event in items) {
                    val start = event.start.dateTime ?: event.start.date
                    val end = event.end.dateTime ?: event.end.date
                    // DateTime型から文字列に変換してScheduleDataに詰める
                    val startStr = if(event.start.dateTime != null) format.format(java.util.Date(start.value)) else start.toString()
                    val endStr = if(event.end.dateTime != null) format.format(java.util.Date(end.value)) else end.toString()

                    resultList.add(
                        ScheduleData(
                            title = event.summary ?: "タイトルなし",
                            start = startStr,
                            end = endStr,
                            description = event.description ?: ""
                        )
                    )
                }
                resultList
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}