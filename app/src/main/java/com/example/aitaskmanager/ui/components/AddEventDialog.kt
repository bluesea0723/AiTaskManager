package com.example.aitaskmanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDate: Calendar,
    onDismiss: () -> Unit,
    onSave: (title: String, startStr: String, endStr: String, description: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // --- 状態管理: 日付(ミリ秒)と時間(時・分) ---
    var startDateMillis by remember { mutableStateOf(initialDate.timeInMillis) }
    var startHour by remember { mutableStateOf(10) }
    var startMinute by remember { mutableStateOf(0) }

    var endDateMillis by remember { mutableStateOf(initialDate.timeInMillis) }
    var endHour by remember { mutableStateOf(11) }
    var endMinute by remember { mutableStateOf(0) }

    // --- ダイアログの表示オン/オフ ---
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // 画面表示用のフォーマット (例: 2026年2月19日(木))
    val dateFormatter = SimpleDateFormat("yyyy年M月d日(E)", Locale.JAPAN)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("予定を手動追加") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // タイトル入力
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- 開始日時 (タップできるテキスト行) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "開始",
                        modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 日付 (タップでカレンダーを開く)
                    Text(
                        text = dateFormatter.format(Date(startDateMillis)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showStartDatePicker = true }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    // 時間 (タップで時計を開く)
                    Text(
                        text = "%02d:%02d".format(startHour, startMinute),
                        modifier = Modifier
                            .clickable { showStartTimePicker = true }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // --- 終了日時 (タップできるテキスト行) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "終了",
                        modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 日付 (タップでカレンダーを開く)
                    Text(
                        text = dateFormatter.format(Date(endDateMillis)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showEndDatePicker = true }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    // 時間 (タップで時計を開く)
                    Text(
                        text = "%02d:%02d".format(endHour, endMinute),
                        modifier = Modifier
                            .clickable { showEndTimePicker = true }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // メモ入力
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("メモ") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // APIに送る形式(ISO8601)に変換して保存
                    val cal = Calendar.getInstance()

                    cal.timeInMillis = startDateMillis
                    val startStr = "%04d-%02d-%02dT%02d:%02d:00+09:00".format(
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), startHour, startMinute
                    )

                    cal.timeInMillis = endDateMillis
                    val endStr = "%04d-%02d-%02dT%02d:%02d:00+09:00".format(
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), endHour, endMinute
                    )

                    onSave(title, startStr, endStr, description)
                },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )

    // ==========================================
    //  選択用ダイアログ群 (DatePicker / TimePicker)
    // ==========================================

    // 開始日のカレンダー
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { startDateMillis = it }
                    showStartDatePicker = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 終了日のカレンダー
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { endDateMillis = it }
                    showEndDatePicker = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 開始時間の時計
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startHour = timePickerState.hour
                    startMinute = timePickerState.minute
                    showStartTimePicker = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("キャンセル") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    // 終了時間の時計
    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endHour = timePickerState.hour
                    endMinute = timePickerState.minute
                    showEndTimePicker = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("キャンセル") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}