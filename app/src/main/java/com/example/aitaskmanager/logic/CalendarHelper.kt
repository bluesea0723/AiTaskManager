package com.example.aitaskmanager.logic

import android.content.Context
import com.example.aitaskmanager.data.AppDatabase
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.TimeZone

class CalendarHelper {

    private fun getDao(context: Context) = AppDatabase.getDatabase(context).scheduleDao()

    // 1. 日表示用のデータ取得 (複数アカウント対応)
    suspend fun fetchDailyEvents(
        context: Context,
        accounts: List<GoogleSignInAccount>,
        year: Int,
        month: Int,
        day: Int,
        forceRefresh: Boolean = false
    ): List<ScheduleData> {
        if (accounts.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val (startStr, endStr) = getRangeStrings(year, month, day, 1, java.util.Calendar.DAY_OF_MONTH)
                val dao = getDao(context)

                if (!forceRefresh) {
                    val cached = dao.getEventsInRange(startStr, endStr)
                    if (cached.isNotEmpty()) return@withContext cached
                }

                // 全アカウントから非同期で並列取得
                val deferredEvents = accounts.map { account ->
                    async { fetchFromApi(context, account, year, month, day, java.util.Calendar.DAY_OF_MONTH) }
                }
                val eventsFromApi = deferredEvents.awaitAll().flatten()

                val existingEvents = dao.getEventsInRange(startStr, endStr)
                val completedIds = existingEvents.filter { it.isCompleted }.map { it.id }.toSet()

                val mergedEvents = eventsFromApi.map { event ->
                    if (event.id in completedIds) event.copy(isCompleted = true) else event
                }

                dao.insertAll(mergedEvents)
                mergedEvents
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // 2. 月表示用のデータ取得 (複数アカウント対応)
    suspend fun fetchMonthlyEvents(
        context: Context,
        accounts: List<GoogleSignInAccount>,
        year: Int,
        month: Int,
        forceRefresh: Boolean = false
    ): List<ScheduleData> {
        if (accounts.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val (startStr, endStr) = getRangeStrings(year, month, 1, 1, java.util.Calendar.MONTH)
                val dao = getDao(context)

                if (!forceRefresh) {
                    val cached = dao.getEventsInRange(startStr, endStr)
                    if (cached.isNotEmpty()) return@withContext cached
                }

                val deferredEvents = accounts.map { account ->
                    async { fetchFromApi(context, account, year, month, 1, java.util.Calendar.MONTH) }
                }
                val eventsFromApi = deferredEvents.awaitAll().flatten()

                val existingEvents = dao.getEventsInRange(startStr, endStr)
                val completedIds = existingEvents.filter { it.isCompleted }.map { it.id }.toSet()

                val mergedEvents = eventsFromApi.map { event ->
                    if (event.id in completedIds) event.copy(isCompleted = true) else event
                }

                dao.insertAll(mergedEvents)
                mergedEvents
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

    suspend fun deleteEvent(context: Context, account: GoogleSignInAccount, schedule: ScheduleData): Boolean {
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

    private fun fetchFromApi(
        context: Context, account: GoogleSignInAccount, year: Int, month: Int, day: Int, field: Int
    ): List<ScheduleData> {
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

        return convertEventsToScheduleData(events.items ?: emptyList(), account.email ?: "")
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

    fun getTodayDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd (E)", java.util.Locale.JAPAN)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return dateFormat.format(java.util.Date())
    }

    // AI用：全アカウントの予定テキストを取得
    suspend fun fetchEvents(context: Context, accounts: List<GoogleSignInAccount>): String {
        if (accounts.isEmpty()) return "予定はありません。"
        return withContext(Dispatchers.IO) {
            try {
                val sb = StringBuilder("【今後1週間の予定】\n")
                val jstZone = TimeZone.getTimeZone("Asia/Tokyo")
                val format = java.text.SimpleDateFormat("MM/dd(E) HH:mm", java.util.Locale.JAPAN).apply { timeZone = jstZone }
                val dayFormat = java.text.SimpleDateFormat("MM/dd(E)", java.util.Locale.JAPAN).apply { timeZone = jstZone }

                val deferredEvents = accounts.map { account ->
                    async {
                        val service = getCalendarService(context, account)
                        val calendar = java.util.Calendar.getInstance(jstZone).apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val startDateTime = DateTime(calendar.time)
                        calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
                        val endDateTime = DateTime(calendar.time)

                        val apiEvents = service.events().list("primary")
                            .setTimeMin(startDateTime).setTimeMax(endDateTime)
                            .setOrderBy("startTime").setSingleEvents(true).execute()
                        apiEvents.items ?: emptyList()
                    }
                }

                val allItems = deferredEvents.awaitAll().flatten().sortedBy { it.start?.dateTime?.value ?: it.start?.date?.value }

                if (allItems.isEmpty()) {
                    return@withContext "今後1週間の予定はありません。"
                } else {
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

    suspend fun addEventToCalendar(context: Context, account: GoogleSignInAccount, schedule: ScheduleData): Boolean {
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
                    val newScheduleData = convertEventsToScheduleData(listOf(createdEvent), account.email ?: "").first()
                    getDao(context).insert(newScheduleData)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun getCalendarService(context: Context, account: GoogleSignInAccount): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(CalendarScopes.CALENDAR))
        credential.selectedAccount = account.account
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("AiTaskManager").build()
    }

    private fun convertEventsToScheduleData(items: List<Event>, accountEmail: String): List<ScheduleData> {
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
                    isCompleted = false,
                    colorId = event.colorId,
                    accountEmail = accountEmail // ★追加
                )
            )
        }
        return list
    }

    fun getEventColor(colorId: String?): Long {
        return when (colorId) {
            "1" -> 0xFF7986CB; "2" -> 0xFF33B679; "3" -> 0xFF8E24AA; "4" -> 0xFFE67C73
            "5" -> 0xFFF6BF26; "6" -> 0xFFF4511E; "7" -> 0xFF039BE5; "8" -> 0xFF616161
            "9" -> 0xFF3F51B5; "10" -> 0xFF0B8043; "11" -> 0xFFD50000
            else -> 0xFF039BE5
        }
    }
}