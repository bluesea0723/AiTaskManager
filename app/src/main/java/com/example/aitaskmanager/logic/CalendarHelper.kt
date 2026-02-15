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

    // ==========================================
    //  新しい機能（画面表示用：リスト形式で取得）
    // ==========================================

    // 1. 日表示用のデータ取得
    suspend fun fetchDailyEvents(context: Context, account: GoogleSignInAccount, year: Int, month: Int, day: Int): List<ScheduleData> {
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context, account)
                val jstZone = TimeZone.getTimeZone("Asia/Tokyo")

                // 指定日の 00:00
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(year, month - 1, day, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startDateTime = DateTime(calendar.time)

                // 翌日の 00:00
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val endDateTime = DateTime(calendar.time)

                val events = service.events().list("primary")
                    .setTimeMin(startDateTime)
                    .setTimeMax(endDateTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                convertEventsToScheduleData(events.items ?: emptyList())
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // 2. 月表示用のデータ取得
    suspend fun fetchMonthlyEvents(context: Context, account: GoogleSignInAccount, year: Int, month: Int): List<ScheduleData> {
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context, account)
                val jstZone = TimeZone.getTimeZone("Asia/Tokyo")

                // 月初 (1日 00:00)
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(year, month - 1, 1, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startDateTime = DateTime(calendar.time)

                // 月末 (翌月1日 00:00)
                calendar.add(java.util.Calendar.MONTH, 1)
                val endDateTime = DateTime(calendar.time)

                val events = service.events().list("primary")
                    .setTimeMin(startDateTime)
                    .setTimeMax(endDateTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                convertEventsToScheduleData(events.items ?: emptyList())
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // ==========================================
    //  以前の機能（AI相談・登録用：文字列操作など）
    //  ※ここを復活させます！
    // ==========================================

    // 3. 今日の日付文字列を取得 (AIへのコンテキスト用)
    fun getTodayDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd (E)", java.util.Locale.JAPAN)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return dateFormat.format(java.util.Date())
    }

    // 4. 今後1週間の予定をテキストで取得 (チャット相談用)
    suspend fun fetchEvents(context: Context, account: GoogleSignInAccount): String {
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context, account)
                val jstZone = TimeZone.getTimeZone("Asia/Tokyo")

                // 今日の00:00
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startDateTime = DateTime(calendar.time)

                // 7日後まで取得
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
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
                    val format = java.text.SimpleDateFormat("MM/dd(E) HH:mm", java.util.Locale.JAPAN)
                    format.timeZone = jstZone

                    val dayFormat = java.text.SimpleDateFormat("MM/dd(E)", java.util.Locale.JAPAN)
                    dayFormat.timeZone = jstZone

                    for (event in items) {
                        val start = event.start.dateTime
                        val date = event.start.date

                        val timeStr = if (start != null) {
                            format.format(java.util.Date(start.value))
                        } else {
                            dayFormat.format(java.util.Date(date.value)) + " [終日]"
                        }

                        val summary = event.summary ?: "(タイトルなし)"
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

    // 5. JSON文字列を解析してリストにする関数 (登録用)
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

    // 6. カレンダーに予定を追加する関数 (登録用)
    suspend fun addEventToCalendar(
        context: Context,
        account: GoogleSignInAccount,
        schedule: ScheduleData
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context, account)

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

    // ==========================================
    //  共通ユーティリティ
    // ==========================================

    private fun getCalendarService(context: Context, account: GoogleSignInAccount): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(CalendarScopes.CALENDAR))
        credential.selectedAccount = account.account
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("AiTaskManager").build()
    }

    private fun convertEventsToScheduleData(items: List<Event>): List<ScheduleData> {
        val list = mutableListOf<ScheduleData>()
        for (event in items) {
            val startStr = event.start.dateTime?.toString() ?: "${event.start.date}T00:00:00+09:00"
            val endStr = event.end.dateTime?.toString() ?: "${event.end.date}T23:59:59+09:00"

            list.add(
                ScheduleData(
                    title = event.summary ?: "(タイトルなし)",
                    start = startStr,
                    end = endStr,
                    description = event.description ?: ""
                )
            )
        }
        return list
    }
}