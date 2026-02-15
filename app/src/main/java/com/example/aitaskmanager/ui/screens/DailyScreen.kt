package com.example.aitaskmanager.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.data.ScheduleData
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DailyScreen(account: GoogleSignInAccount?) {
    val context = LocalContext.current
    val calendarHelper = remember { CalendarHelper() }

    // ★変更点1: 「今日」ではなく「選択中の日付」をStateで管理する
    // 初期値は今日
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }

    // 画面更新用のフラグ（Calendarの中身だけ変えても再描画されないことがあるため）
    var refreshKey by remember { mutableIntStateOf(0) }

    // 日付情報の取得
    val year = selectedDate.get(Calendar.YEAR)
    val month = selectedDate.get(Calendar.MONTH) + 1
    val day = selectedDate.get(Calendar.DAY_OF_MONTH)

    var events by remember { mutableStateOf<List<ScheduleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // ★日付が変わるたびにデータを再取得
    LaunchedEffect(account, selectedDate, refreshKey) {
        if (account != null) {
            isLoading = true
            // selectedDateの年・月・日を使って取得
            events = calendarHelper.fetchDailyEvents(
                context,
                account,
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH) + 1,
                selectedDate.get(Calendar.DAY_OF_MONTH)
            )
            isLoading = false
        }
    }

    // ★スワイプ検知用の変数
    var offsetX by remember { mutableFloatStateOf(0f) }

    // 全日予定と時間指定予定の振り分け
    val (allDayEvents, timeEvents) = events.partition { event ->
        val startMin = getMinutesForDay(event.start, year, month, day, true)
        val endMin = getMinutesForDay(event.end, year, month, day, false)
        (endMin - startMin) >= 1439
    }

    // 日付操作関数
    fun moveDate(days: Int) {
        val newCal = selectedDate.clone() as Calendar
        newCal.add(Calendar.DAY_OF_MONTH, days)
        selectedDate = newCal
        refreshKey++ // 強制再描画
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // ★変更点2: スワイプ検知エリアを画面全体に設定
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // ドラッグ終了時に移動判定 (閾値は100pxくらい)
                        if (offsetX > 100) {
                            moveDate(-1) // 右スワイプ → 前日（昨日）へ
                        } else if (offsetX < -100) {
                            moveDate(1)  // 左スワイプ → 翌日（明日）へ
                        }
                        offsetX = 0f // リセット
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
    ) {
        // --- ヘッダー部分 (日付表示と移動ボタン) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 前日へボタン
            IconButton(onClick = { moveDate(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前日")
            }

            // 日付表示
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val fmt = SimpleDateFormat("M/d (E)", Locale.JAPAN)
                val dateStr = fmt.format(selectedDate.time)

                // 「今日」かどうか判定して表示を変える
                val today = Calendar.getInstance()
                val isToday = (today.get(Calendar.YEAR) == year &&
                        today.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR))

                Text(
                    text = if(isToday) "今日 $dateStr" else dateStr,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // 翌日へボタン
            IconButton(onClick = { moveDate(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "翌日")
            }
        }

        // --- コンテンツエリア ---
        if (account == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ログインしてください") }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            // 1. 全日の予定（タスクエリア）
            if (allDayEvents.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "全日の予定・タスク",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    allDayEvents.forEach { event ->
                        AllDayEventCard(event)
                    }
                }
                Divider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f))
            }

            // 2. 時間指定の予定（タイムラインエリア）
            // 画面残りスペースを使う
            DailyTimeline(timeEvents, year, month, day, Modifier.weight(1f))
        }
    }
}

// 以下の AllDayEventCard, DailyTimeline, getMinutesForDay は以前と同じですが、
// 完全なコードにするために再掲します。

@Composable
fun AllDayEventCard(event: ScheduleData) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircleOutline,
                contentDescription = "Task",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DailyTimeline(
    events: List<ScheduleData>,
    year: Int,
    month: Int,
    day: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // 画面の高さを24等分する
        val hourHeight = maxHeight / 24

        Row(modifier = Modifier.fillMaxSize()) {
            // 左側：時間の目盛り
            Column(modifier = Modifier.width(40.dp)) {
                for (i in 0..24) {
                    Box(modifier = Modifier.height(hourHeight), contentAlignment = Alignment.TopCenter) {
                        Text(
                            text = "$i",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // 右側：予定の配置エリア
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // グリッド線
                for (i in 0..24) {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = hourHeight * i),
                        color = Color.LightGray.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }

                // 予定ブロック
                events.forEach { event ->
                    val startMin = getMinutesForDay(event.start, year, month, day, isStart = true)
                    val endMin = getMinutesForDay(event.end, year, month, day, isStart = false)

                    val duration = endMin - startMin

                    if (duration > 0) {
                        val startOffset = hourHeight * (startMin / 60f)
                        val minHeight = hourHeight * 0.5f
                        val calculatedHeight = hourHeight * (duration / 60f)
                        val height = maxOf(calculatedHeight, minHeight)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = startOffset)
                                .height(height)
                                .padding(end = 4.dp, bottom = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(2.dp)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getMinutesForDay(dateTimeStr: String, year: Int, month: Int, day: Int, isStart: Boolean): Int {
    try {
        val targetDatePrefix = "%04d-%02d-%02d".format(year, month, day)
        if (dateTimeStr.startsWith(targetDatePrefix)) {
            val timePart = dateTimeStr.substringAfter("T").substringBefore("+").substringBefore("Z")
            val parts = timePart.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            return h * 60 + m
        } else {
            val eventDatePart = dateTimeStr.substringBefore("T")
            if (isStart) {
                return 0
            } else {
                return 24 * 60
            }
        }
    } catch (e: Exception) {
        return if (isStart) 0 else 0
    }
}