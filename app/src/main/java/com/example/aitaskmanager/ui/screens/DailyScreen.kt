package com.example.aitaskmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.data.ScheduleData
import com.example.aitaskmanager.ui.components.AddEventDialog
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DailyScreen(accounts: List<GoogleSignInAccount>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarHelper = remember { CalendarHelper() }

    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val year = selectedDate.get(Calendar.YEAR)
    val month = selectedDate.get(Calendar.MONTH) + 1
    val day = selectedDate.get(Calendar.DAY_OF_MONTH)

    var events by remember { mutableStateOf<List<ScheduleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(accounts.size, selectedDate, refreshKey) {
        if (accounts.isNotEmpty()) {
            isLoading = true
            events = calendarHelper.fetchDailyEvents(context, accounts, year, month, day)
            isLoading = false
        } else {
            events = emptyList()
        }
    }

    var offsetX by remember { mutableFloatStateOf(0f) }

    fun moveDate(days: Int) {
        val newCal = selectedDate.clone() as Calendar
        newCal.add(Calendar.DAY_OF_MONTH, days)
        selectedDate = newCal
        refreshKey++
    }

    fun onToggleCompletion(event: ScheduleData, isCompleted: Boolean) {
        scope.launch {
            events = events.map { if (it.id == event.id) it.copy(isCompleted = isCompleted) else it }
            calendarHelper.updateEventCompletion(context, event, isCompleted)
        }
    }

    val activeEvents = events.filter { !it.isCompleted }
    val completedEvents = events.filter { it.isCompleted }

    val (allDayEvents, timeEvents) = activeEvents.partition { event ->
        val startMin = getMinutesForDay(event.start, year, month, day, true)
        val endMin = getMinutesForDay(event.end, year, month, day, false)
        (endMin - startMin) >= 1439
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > 100) moveDate(-1)
                            else if (offsetX < -100) moveDate(1)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount }
                    )
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { moveDate(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前日") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val fmt = SimpleDateFormat("M/d (E)", Locale.JAPAN)
                    val dateStr = fmt.format(selectedDate.time)
                    val today = Calendar.getInstance()
                    val isToday = (today.get(Calendar.YEAR) == year && today.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR))
                    Text(text = if (isToday) "今日 $dateStr" else dateStr, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { moveDate(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "翌日") }
            }

            if (accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ログインしてください") }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("予定はありません", color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    if (allDayEvents.isNotEmpty()) {
                        item { Text("全日のタスク", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                        items(allDayEvents) { event -> TaskItem(event = event, isAllDay = true, onToggleCompletion = { onToggleCompletion(event, it) }) }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                    if (timeEvents.isNotEmpty()) {
                        item { Text("スケジュール", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                        items(timeEvents) { event -> TaskItem(event = event, isAllDay = false, onToggleCompletion = { onToggleCompletion(event, it) }) }
                    }
                    if (completedEvents.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            TextButton(onClick = { showCompleted = !showCompleted }, modifier = Modifier.padding(start = 8.dp)) {
                                Text("完了済み (${completedEvents.size})", color = MaterialTheme.colorScheme.onSurface)
                                Icon(imageVector = if (showCompleted) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (showCompleted) {
                            items(completedEvents) { event ->
                                val startMin = getMinutesForDay(event.start, year, month, day, true)
                                val endMin = getMinutesForDay(event.end, year, month, day, false)
                                val isAllDay = (endMin - startMin) >= 1439
                                Box(modifier = Modifier.alpha(0.5f)) { TaskItem(event = event, isAllDay = isAllDay, onToggleCompletion = { onToggleCompletion(event, it) }) }
                            }
                        }
                    }
                }
            }
        }

        if (accounts.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, "予定を追加", tint = Color.White) }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            initialDate = selectedDate,
            accounts = accounts, // ★追加
            onDismiss = { showAddDialog = false },
            onSave = { title, start, end, desc, targetAccount ->
                showAddDialog = false
                scope.launch {
                    isLoading = true
                    val newEvent = ScheduleData(title = title, start = start, end = end, description = desc, isCompleted = false)
                    calendarHelper.addEventToCalendar(context, targetAccount, newEvent)
                    events = calendarHelper.fetchDailyEvents(context, accounts, year, month, day, true)
                    isLoading = false
                }
            }
        )
    }
}

@Composable
fun TaskItem(event: ScheduleData, isAllDay: Boolean, onToggleCompletion: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = event.isCompleted, onCheckedChange = { isChecked -> onToggleCompletion(isChecked) })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!isAllDay) {
                    val timeStr = formatTimeRange(event.start, event.end)
                    Text(text = timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(text = event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textDecoration = if (event.isCompleted) TextDecoration.LineThrough else null)
            }
        }
    }
}

fun formatTimeRange(start: String, end: String): String {
    return try {
        val s = if(start.contains("T")) start.substringAfter("T").take(5) else ""
        val e = if(end.contains("T")) end.substringAfter("T").take(5) else ""
        "$s - $e"
    } catch (e: Exception) { "" }
}

fun getMinutesForDay(dateTimeStr: String, year: Int, month: Int, day: Int, isStart: Boolean): Int {
    return try {
        val targetDatePrefix = "%04d-%02d-%02d".format(year, month, day)
        if (dateTimeStr.startsWith(targetDatePrefix)) {
            val timePart = dateTimeStr.substringAfter("T").substringBefore("+").substringBefore("Z")
            val parts = timePart.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } else {
            if (isStart) 0 else 24 * 60
        }
    } catch (e: Exception) {
        if (isStart) 0 else 0
    }
}