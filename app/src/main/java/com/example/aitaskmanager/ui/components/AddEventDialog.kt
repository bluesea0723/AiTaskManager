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
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDate: Calendar,
    accounts: List<GoogleSignInAccount>, // ★追加
    onDismiss: () -> Unit,
    onSave: (title: String, startStr: String, endStr: String, description: String, account: GoogleSignInAccount) -> Unit // ★追加
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var startDateMillis by remember { mutableStateOf(initialDate.timeInMillis) }
    var startHour by remember { mutableStateOf(10) }
    var startMinute by remember { mutableStateOf(0) }

    var endDateMillis by remember { mutableStateOf(initialDate.timeInMillis) }
    var endHour by remember { mutableStateOf(11) }
    var endMinute by remember { mutableStateOf(0) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // ★追加: アカウント選択用
    var expanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    val dateFormatter = SimpleDateFormat("yyyy年M月d日(E)", Locale.JAPAN)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("予定を手動追加") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // アカウント選択ドロップダウン
                if (accounts.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.email ?: "アカウント未選択",
                            onValueChange = {}, readOnly = true,
                            label = { Text("保存先アカウント") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.email ?: "") },
                                    onClick = { selectedAccount = acc; expanded = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("タイトル") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("開始", modifier = Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateFormatter.format(Date(startDateMillis)), modifier = Modifier.weight(1f).clickable { showStartDatePicker = true }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    Text("%02d:%02d".format(startHour, startMinute), modifier = Modifier.clickable { showStartTimePicker = true }.padding(vertical = 12.dp, horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge)
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("終了", modifier = Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateFormatter.format(Date(endDateMillis)), modifier = Modifier.weight(1f).clickable { showEndDatePicker = true }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    Text("%02d:%02d".format(endHour, endMinute), modifier = Modifier.clickable { showEndTimePicker = true }.padding(vertical = 12.dp, horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("メモ") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = startDateMillis
                    val startStr = "%04d-%02d-%02dT%02d:%02d:00+09:00".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), startHour, startMinute)

                    cal.timeInMillis = endDateMillis
                    val endStr = "%04d-%02d-%02dT%02d:%02d:00+09:00".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), endHour, endMinute)

                    selectedAccount?.let { acc ->
                        onSave(title, startStr, endStr, description, acc)
                    }
                },
                enabled = title.isNotBlank() && selectedAccount != null
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(onDismissRequest = { showStartDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { startDateMillis = it }; showStartDatePicker = false }) { Text("決定") } }) { DatePicker(state = datePickerState) }
    }
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(onDismissRequest = { showEndDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { endDateMillis = it }; showEndDatePicker = false }) { Text("決定") } }) { DatePicker(state = datePickerState) }
    }
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)
        AlertDialog(onDismissRequest = { showStartTimePicker = false }, confirmButton = { TextButton(onClick = { startHour = timePickerState.hour; startMinute = timePickerState.minute; showStartTimePicker = false }) { Text("決定") } }, text = { TimePicker(state = timePickerState) })
    }
    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = true)
        AlertDialog(onDismissRequest = { showEndTimePicker = false }, confirmButton = { TextButton(onClick = { endHour = timePickerState.hour; endMinute = timePickerState.minute; showEndTimePicker = false }) { Text("決定") } }, text = { TimePicker(state = timePickerState) })
    }
}