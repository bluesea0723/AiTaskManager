package com.example.aitaskmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.data.ScheduleData
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.util.Calendar

@Composable
fun MonthlyScreen(account: GoogleSignInAccount?) {
    val context = LocalContext.current
    val calendarHelper = remember { CalendarHelper() }

    val calendar = Calendar.getInstance()
    val currentYear = calendar.get(Calendar.YEAR)
    val currentMonth = calendar.get(Calendar.MONTH) + 1

    var events by remember { mutableStateOf<List<ScheduleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        if (account != null) {
            isLoading = true
            events = calendarHelper.fetchMonthlyEvents(context, account, currentYear, currentMonth)
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = "${currentYear}年 ${currentMonth}月",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (account == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ログインしてください") }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            // カレンダーグリッド表示
            CalendarGrid(currentYear, currentMonth, events)
        }
    }
}

@Composable
fun CalendarGrid(year: Int, month: Int, events: List<ScheduleData>) {
    // カレンダー計算
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, 1)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1(日) 〜 7(土)

    // 空白セル + 日付セル のリストを作る
    val cells = mutableListOf<String>()
    // 1日の曜日まで空白を入れる (Calendar.SUNDAY=1 なので -1)
    repeat(firstDayOfWeek - 1) { cells.add("") }
    // 1日〜月末まで
    for (d in 1..daysInMonth) { cells.add(d.toString()) }

    // 曜日ヘッダー
    val weekDays = listOf("日", "月", "火", "水", "木", "金", "土")

    Column {
        // 曜日行
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

        // 日付グリッド
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cells) { dayStr ->
                if (dayStr.isEmpty()) {
                    Spacer(modifier = Modifier.aspectRatio(0.6f)) // 空白セル
                } else {
                    val dayInt = dayStr.toInt()
                    // この日の予定をフィルタリング
                    val dayEvents = events.filter {
                        // start文字列に "yyyy-MM-dd" が含まれているか簡易チェック
                        // (もっと厳密にするなら日付型で比較)
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
            .aspectRatio(0.6f) // 縦長にする
            .border(0.5.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(2.dp)) {
            // 日付
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            // 予定リスト (小さく表示)
            events.take(3).forEach { event -> // 最大3件まで表示
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