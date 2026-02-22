package com.example.aitaskmanager.ui.screens

import android.accounts.Account
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import java.util.Calendar

@Composable
fun MonthlyScreen(accounts: List<Account>) {
    val context = LocalContext.current
    val calendarHelper = remember { CalendarHelper() }

    var selectedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val currentYear = selectedMonth.get(Calendar.YEAR)
    val currentMonth = selectedMonth.get(Calendar.MONTH) + 1

    var events by remember { mutableStateOf<List<ScheduleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var offsetX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(accounts, selectedMonth, refreshKey) {
        if (accounts.isNotEmpty()) {
            isLoading = true
            events = calendarHelper.fetchMonthlyEvents(context, accounts, currentYear, currentMonth)
            isLoading = false
        }
    }

    fun moveMonth(amount: Int) {
        val newCal = selectedMonth.clone() as Calendar
        newCal.add(Calendar.MONTH, amount)
        selectedMonth = newCal
        refreshKey++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 100) {
                            moveMonth(-1)
                        } else if (offsetX < -100) {
                            moveMonth(1)
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { moveMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "先月")
            }

            Text(
                text = "${currentYear}年 ${currentMonth}月",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = { moveMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "来月")
            }
        }

        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ログインしてください") }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            CalendarGrid(currentYear, currentMonth, events)
        }
    }
}

@Composable
fun CalendarGrid(year: Int, month: Int, events: List<ScheduleData>) {
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, 1)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

    val cells = mutableListOf<String>()
    repeat(firstDayOfWeek - 1) { cells.add("") }
    for (d in 1..daysInMonth) { cells.add(d.toString()) }

    val weekDays = listOf("日", "月", "火", "水", "木", "金", "土")

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cells) { dayStr ->
                if (dayStr.isEmpty()) {
                    Spacer(modifier = Modifier.aspectRatio(0.6f))
                } else {
                    val dayInt = dayStr.toInt()
                    val dayEvents = events.filter {
                        val dateKey = "%04d-%02d-%02d".format(year, month, dayInt)
                        it.start.contains(dateKey)
                    }

                    DayCell(day = dayStr, events = dayEvents)
                }
            }
        }
    }
}

@Composable
fun DayCell(day: String, events: List<ScheduleData>) {
    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(0.6f)
            .border(0.5.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(2.dp)) {
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            events.take(3).forEach { event ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.padding(vertical = 1.dp).fillMaxWidth()
                ) {
                    Text(
                        text = event.title,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
            if (events.size > 3) {
                Text("...", fontSize = 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}