package com.example.aitaskmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.data.ScheduleData
import com.example.aitaskmanager.ui.components.AddEventDialog
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun MonthlyScreen(accounts: List<GoogleSignInAccount>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarHelper = remember { CalendarHelper() }

    var selectedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val currentYear = selectedMonth.get(Calendar.YEAR)
    val currentMonth = selectedMonth.get(Calendar.MONTH) + 1

    var events by remember { mutableStateOf<List<ScheduleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(accounts.size, selectedMonth, refreshKey) {
        if (accounts.isNotEmpty()) {
            isLoading = true
            events = calendarHelper.fetchMonthlyEvents(context, accounts, currentYear, currentMonth)
            isLoading = false
        } else {
            events = emptyList()
        }
    }

    fun moveMonth(amount: Int) {
        val newCal = selectedMonth.clone() as Calendar
        newCal.add(Calendar.MONTH, amount)
        selectedMonth = newCal
        refreshKey++
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > 100) moveMonth(-1)
                            else if (offsetX < -100) moveMonth(1)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount }
                    )
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { moveMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "先月") }
                Text("${currentYear}年 ${currentMonth}月", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { moveMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "来月") }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("日", "月", "火", "水", "木", "金", "土").forEach { day ->
                    Text(
                        text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        color = if(day == "日") Color.Red else if(day == "土") Color.Blue else MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ログインしてください") }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                FullHeightCalendarGrid(currentYear, currentMonth, events, calendarHelper)
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
            initialDate = selectedMonth,
            accounts = accounts, // ★追加
            onDismiss = { showAddDialog = false },
            onSave = { title, start, end, desc, targetAccount ->
                showAddDialog = false
                scope.launch {
                    isLoading = true
                    val newEvent = ScheduleData(title = title, start = start, end = end, description = desc, isCompleted = false)
                    calendarHelper.addEventToCalendar(context, targetAccount, newEvent)
                    events = calendarHelper.fetchMonthlyEvents(context, accounts, currentYear, currentMonth, true)
                    isLoading = false
                }
            }
        )
    }
}

@Composable
fun FullHeightCalendarGrid(year: Int, month: Int, events: List<ScheduleData>, helper: CalendarHelper) {
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, 1)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_MONTH, -(firstDayOfWeek - 1))

    val days = mutableListOf<Calendar>()
    repeat(42) {
        days.add(cal.clone() as Calendar)
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        for (weekIndex in 0 until 6) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (dayIndex in 0 until 7) {
                    val currentDay = days[weekIndex * 7 + dayIndex]
                    val dYear = currentDay.get(Calendar.YEAR)
                    val dMonth = currentDay.get(Calendar.MONTH) + 1
                    val dDay = currentDay.get(Calendar.DAY_OF_MONTH)

                    val isCurrentMonth = (dMonth == month)
                    val dayEvents = events.filter { it.start.contains("%04d-%02d-%02d".format(dYear, dMonth, dDay)) }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.LightGray)
                            .background(if (isCurrentMonth) MaterialTheme.colorScheme.surface else Color.LightGray.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(2.dp)) {
                            Text(
                                text = dDay.toString(), style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentMonth) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            dayEvents.take(4).forEach { event ->
                                val eventColor = Color(helper.getEventColor(event.colorId))
                                Surface(
                                    color = eventColor.copy(alpha = if(event.isCompleted) 0.3f else 1.0f),
                                    shape = RoundedCornerShape(2.dp), modifier = Modifier.padding(vertical = 1.dp).fillMaxWidth()
                                ) {
                                    Text(
                                        text = event.title, fontSize = 9.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                        textDecoration = if (event.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}