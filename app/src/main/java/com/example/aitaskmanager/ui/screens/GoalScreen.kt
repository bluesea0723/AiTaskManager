package com.example.aitaskmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitaskmanager.data.ScheduleData
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.logic.GeminiHelper
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(account: GoogleSignInAccount?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ロジッククラス
    val geminiHelper = remember { GeminiHelper() }
    val calendarHelper = remember { CalendarHelper() }

    // --- ステート管理 ---
    var step by remember { mutableIntStateOf(1) } // 1:分析入力, 2:条件設定, 3:プレビュー&修正
    var isLoading by remember { mutableStateOf(false) }

    // Step 1: 目標と現状
    var goalText by remember { mutableStateOf("") }      // 目標
    var currentStatus by remember { mutableStateOf("") } // 現状スコアなど
    var resources by remember { mutableStateOf("") }     // 参考書など
    var deadlineDate by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.MONTH, 1) }.timeInMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Step 2: 分析結果と希望
    var analysisResult by remember { mutableStateOf("") } // AIからの分析コメント
    var userAvailability by remember { mutableStateOf("") } // ユーザーの希望時間帯

    // Step 3: ドラフト（下書き）
    var draftSchedules by remember { mutableStateOf<List<ScheduleData>>(emptyList()) }
    var feedbackText by remember { mutableStateOf("") } // 修正指示

    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // ヘッダー（進捗バー的なもの）
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepIndicator(1, "現状", step)
            StepIndicator(2, "対策", step)
            StepIndicator(3, "計画", step)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("AIが思考中...", modifier = Modifier.padding(top = 48.dp))
            }
        } else {
            when (step) {
                1 -> {
                    // --- Step 1: 目標と現状の入力 ---
                    Text("目標と現状を教えてください", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = goalText, onValueChange = { goalText = it },
                        label = { Text("目標 (例: TOEIC 800点)") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = currentStatus, onValueChange = { currentStatus = it },
                        label = { Text("現状 (例: 500点, 苦手はリスニング)") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = resources, onValueChange = { resources = it },
                        label = { Text("持っている教材 (例: 金のフレーズ)") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dateFormatter.format(deadlineDate), onValueChange = {},
                        label = { Text("いつまでに？") }, readOnly = true,
                        trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (goalText.isBlank()) return@Button
                            scope.launch {
                                isLoading = true
                                // 分析を実行
                                analysisResult = geminiHelper.analyzeGoal(
                                    goalText, currentStatus, resources, dateFormatter.format(deadlineDate)
                                )
                                isLoading = false
                                step = 2 // 次へ
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = goalText.isNotBlank()
                    ) {
                        Text("分析して対策を考える")
                        Icon(Icons.Default.ArrowForward, null)
                    }
                }

                2 -> {
                    // --- Step 2: 分析結果確認 & 希望入力 ---
                    Text("AIによる分析結果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(analysisResult, modifier = Modifier.padding(16.dp))
                    }

                    Text("いつ勉強しますか？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=8.dp))
                    Text("AIの分析を参考に、実行可能な時間を入力してください。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    OutlinedTextField(
                        value = userAvailability,
                        onValueChange = { userAvailability = it },
                        label = { Text("希望時間帯 (例: 平日は20-22時, 土日は午前中)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                // 既存の予定を取得
                                val existingEvents = if (account != null) calendarHelper.fetchEvents(context, account) else ""
                                // スケジュール案を作成
                                val json = geminiHelper.generateDraftPlan(
                                    goalText, dateFormatter.format(deadlineDate), analysisResult, userAvailability, existingEvents
                                )
                                draftSchedules = calendarHelper.parseJson(json)
                                isLoading = false
                                step = 3 // 次へ
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("仮のスケジュールを作成")
                        Icon(Icons.Default.Schedule, null)
                    }
                }

                3 -> {
                    // --- Step 3: プレビュー & 修正 & 登録 ---
                    Text("スケジュール案の確認", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("カレンダー形式で確認できます。修正したい場合は下の欄に入力してください。", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(8.dp))

                    // 簡易カレンダープレビュー
                    if (draftSchedules.isNotEmpty()) {
                        DraftPreviewCalendar(draftSchedules)
                    } else {
                        Text("うまく作成できませんでした。条件を変えて試してください。", color = Color.Red)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 修正指示エリア
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("修正指示 (例: 日曜日は休みにしたい)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 再生成ボタン
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val existingEvents = if (account != null) calendarHelper.fetchEvents(context, account) else ""
                                    // 修正指示を含めて再生成
                                    val json = geminiHelper.generateDraftPlan(
                                        goalText, dateFormatter.format(deadlineDate), analysisResult, userAvailability, existingEvents, feedbackText
                                    )
                                    draftSchedules = calendarHelper.parseJson(json)
                                    feedbackText = "" // リセット
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("修正して再作成")
                        }

                        // 確定ボタン
                        Button(
                            onClick = {
                                scope.launch {
                                    if (account != null) {
                                        isLoading = true
                                        var count = 0
                                        draftSchedules.forEach {
                                            if (calendarHelper.addEventToCalendar(context, account, it)) count++
                                        }
                                        isLoading = false
                                        // 完了したら最初に戻るか、ダイアログを出す（ここではリセット）
                                        step = 1
                                        goalText = ""
                                        currentStatus = ""
                                        draftSchedules = emptyList()
                                        // TODO: 完了メッセージを表示すると親切
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("これで登録")
                        }
                    }
                }
            }
        }

        // DatePickerダイアログ
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = deadlineDate)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { deadlineDate = it }
                        showDatePicker = false
                    }) { Text("決定") }
                }
            ) { DatePicker(state = datePickerState) }
        }
    }
}

// ステップ表示用の小さなコンポーネント
@Composable
fun StepIndicator(stepNum: Int, label: String, currentStep: Int) {
    val isActive = stepNum <= currentStep
    val color = if (isActive) MaterialTheme.colorScheme.primary else Color.LightGray
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(stepNum.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ドラフト確認用の簡易カレンダー
@Composable
fun DraftPreviewCalendar(schedules: List<ScheduleData>) {
    // 最初の予定の月を表示（簡易化のため）
    val firstEvent = schedules.firstOrNull() ?: return
    val year = firstEvent.start.substring(0, 4).toIntOrNull() ?: 2026
    val month = firstEvent.start.substring(5, 7).toIntOrNull() ?: 1

    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("${year}年 ${month}月のイメージ", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))

            // 簡易カレンダーグリッド（MonthlyScreenのロジックを流用・簡略化）
            val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

            val cells = mutableListOf<String>()
            repeat(firstDayOfWeek - 1) { cells.add("") }
            for (d in 1..daysInMonth) { cells.add(d.toString()) }

            LazyVerticalGrid(columns = GridCells.Fixed(7)) {
                items(cells) { dayStr ->
                    if (dayStr.isNotEmpty()) {
                        val dayInt = dayStr.toInt()
                        // その日の予定があるか？
                        val dayEvents = schedules.filter {
                            it.start.startsWith("%04d-%02d-%02d".format(year, month, dayInt))
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dayStr, fontSize = 10.sp)
                                if (dayEvents.isNotEmpty()) {
                                    // 予定があれば丸印などをつける
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(2.dp),
                                        modifier = Modifier.padding(1.dp).fillMaxWidth()
                                    ) {
                                        Text(
                                            text = dayEvents[0].title,
                                            fontSize = 6.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.aspectRatio(1f))
                    }
                }
            }
        }
    }
}