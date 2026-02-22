package com.example.aitaskmanager.logic

import android.accounts.Account
import android.content.Context
import com.example.aitaskmanager.data.AppDatabase
import com.example.aitaskmanager.data.ScheduleData
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

    private fun getDao(context: Context) = AppDatabase.getDatabase(context).scheduleDao()

    // 1. 日表示用のデータ取得
    suspend fun fetchDailyEvents(context: Context, accounts: List<Account>, year: Int, month: Int, day: Int, forceRefresh: Boolean = false): List<ScheduleData> {
        return withContext(Dispatchers.IO) {
            try {
                val (startStr, endStr) = getRangeStrings(year, month, day, 1, java.util.Calendar.DAY_OF_MONTH)
                val dao = getDao(context)

                if (!forceRefresh) {
                    val cached = dao.getEventsInRange(startStr, endStr)
                    if (cached.isNotEmpty()) return@withContext cached
                }

                // 全てのアカウントから予定を取得してマージする
                val allEventsFromApi = mutableListOf<ScheduleData>()
                for (account in accounts) {
                    val events = fetchFromApi(context, account, year, month, day, java.util.Calendar.DAY_OF_MONTH)
                    allEventsFromApi.addAll(events)
                }

                val existingEvents = dao.getEventsInRange(startStr, endStr)
                val completedIds = existingEvents.filter { it.isCompleted }.map { it.id }.toSet()

                val mergedEvents = allEventsFromApi.map { event ->
                    if (event.id in completedIds) event.copy(isCompleted = true) else event
                }

                dao.insertAll(mergedEvents)
                mergedEvents.sortedBy { it.start }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // 2. 月表示用のデータ取得
    suspend fun fetchMonthlyEvents(context: Context, accounts: List<Account>, year: Int, month: Int, forceRefresh: Boolean = false): List<ScheduleData> {
        return withContext(Dispatchers.IO) {
            try {
                val (startStr, endStr) = getRangeStrings(year, month, 1, 1, java.util.Calendar.MONTH)
                val dao = getDao(context)

                if (!forceRefresh) {
                    val cached = dao.getEventsInRange(startStr, endStr)
                    if (cached.isNotEmpty()) {
                        return@withContext cached
                    }
                }

                // 全てのアカウントから予定を取得してマージする
                val allEventsFromApi = mutableListOf<ScheduleData>()
                for (account in accounts) {
                    val events = fetchFromApi(context, account, year, month, 1, java.util.Calendar.MONTH)
                    allEventsFromApi.addAll(events)
                }

                val existingEvents = dao.getEventsInRange(startStr, endStr)
                val completedIds = existingEvents.filter { it.isCompleted }.map { it.id }.toSet()

                val mergedEvents = allEventsFromApi.map { event ->
                    if (event.id in completedIds) event.copy(isCompleted = true) else event
                }

                dao.insertAll(mergedEvents)
                mergedEvents.sortedBy { it.start }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun updateEventCompletion(context: Context, schedule: ScheduleData, isCompleted: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dao = getDao(context)
                val updatedSchedule = schedule.copy(isCompleted = isCompleted)
                dao.insert(updatedSchedule)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun deleteEvent(context: Context, account: Account, schedule: ScheduleData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                try {
                    val service = getCalendarService(context, account)
                    service.events().delete("primary", schedule.id).execute()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                getDao(context).delete(schedule)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // --- API通信の実処理 (共通化) ---
    private fun fetchFromApi(context: Context, account: Account, year: Int, month: Int, day: Int, field: Int): List<ScheduleData> {
        val service = getCalendarService(context, account)
        val jstZone = TimeZone.getTimeZone("Asia/Tokyo")

        val calendar = java.util.Calendar.getInstance(jstZone).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startDateTime = DateTime(calendar.time)

        calendar.add(field, 1)
        val endDateTime = DateTime(calendar.time)

        val events = service.events().list("primary")
            .setTimeMin(startDateTime)
            .setTimeMax(endDateTime)
            .setOrderBy("startTime")
            .setSingleEvents(true)
            .execute()

        return convertEventsToScheduleData(events.items ?: emptyList())
    }

    private fun getRangeStrings(year: Int, month: Int, day: Int, amount: Int, field: Int): Pair<String, String> {
        val jstZone = TimeZone.getTimeZone("Asia/Tokyo")
        val calendar = java.util.Calendar.getInstance(jstZone).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
        sdf.timeZone = jstZone
        val startStr = sdf.format(calendar.time)

        calendar.add(field, amount)
        val endStr = sdf.format(calendar.time)
        return startStr to endStr
    }

    // ==========================================
    //  AI相談・登録用
    // ==========================================

    fun getTodayDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd (E)", java.util.Locale.JAPAN)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return dateFormat.format(java.util.Date())
    }

    suspend fun fetchEvents(context: Context, accounts: List<Account>): String {
        return withContext(Dispatchers.IO) {
            try {
                val jstZone = TimeZone.getTimeZone("Asia/Tokyo")
                val calendar = java.util.Calendar.getInstance(jstZone).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }
                val startDateTime = DateTime(calendar.time)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
                val endDateTime = DateTime(calendar.time)

                val allItems = mutableListOf<Event>()

                for (account in accounts) {
                    val service = getCalendarService(context, account)
                    val apiEvents = service.events().list("primary")
                        .setTimeMin(startDateTime).setTimeMax(endDateTime)
                        .setOrderBy("startTime").setSingleEvents(true).execute()
                    apiEvents.items?.let { allItems.addAll(it) }
                }

                if (allItems.isEmpty()) {
                    "今後1週間の予定はありません。"
                } else {
                    // 時間順にソート
                    allItems.sortBy { it.start.dateTime?.value ?: it.start.date?.value ?: 0L }

                    val sb = StringBuilder("【今後1週間の予定】\n")
                    val format = java.text.SimpleDateFormat("MM/dd(E) HH:mm", java.util.Locale.JAPAN)
                    format.timeZone = jstZone
                    val dayFormat = java.text.SimpleDateFormat("MM/dd(E)", java.util.Locale.JAPAN)
                    dayFormat.timeZone = jstZone
                    for (event in allItems) {
                        val start = event.start.dateTime
                        val date = event.start.date
                        val timeStr = if (start != null) format.format(java.util.Date(start.value)) else dayFormat.format(java.util.Date(date.value)) + " [終日]"
                        sb.append("- $timeStr : ${event.summary ?: "(タイトルなし)"}\n")
                    }
                    sb.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "予定の取得に失敗しました: ${e.localizedMessage}"
            }
        }
    }

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

    suspend fun addEventToCalendar(context: Context, accounts: List<Account>, schedule: ScheduleData): Boolean {
        if (accounts.isEmpty()) return false
        val account = accounts.first() // ★メインアカウント(最初に追加したもの)に追加する
        return withContext(Dispatchers.IO) {
            try {
                val service = getCalendarService(context, account)
                val event = Event().apply {
                    summary = schedule.title
                    description = schedule.description
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
                val createdEvent = service.events().insert("primary", event).execute()

                if (createdEvent != null) {
                    val newScheduleData = convertEventsToScheduleData(listOf(createdEvent)).first()
                    getDao(context).insert(newScheduleData)
                }
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

    private fun getCalendarService(context: Context, account: Account): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(CalendarScopes.CALENDAR))
        credential.selectedAccount = account
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
                    id = event.id ?: java.util.UUID.randomUUID().toString(),
                    title = event.summary ?: "(タイトルなし)",
                    start = startStr,
                    end = endStr,
                    description = event.description ?: "",
                    isCompleted = false // デフォルトは未完了
                )
            )
        }
        return list
    }
}