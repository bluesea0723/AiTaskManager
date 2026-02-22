package com.example.aitaskmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aitaskmanager.data.AppDatabase
import com.example.aitaskmanager.data.ChatMessage
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.logic.GeminiHelper
import com.example.aitaskmanager.ui.components.ChatBubble
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ChatScreen(
    accounts: List<GoogleSignInAccount>, // ★変更
    onLoginClick: () -> Unit,
    bottomPadding: Dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    val geminiHelper = remember { GeminiHelper() }
    val calendarHelper = remember { CalendarHelper() }
    val chatDao = remember { AppDatabase.getDatabase(context).chatMessageDao() }

    LaunchedEffect(Unit) {
        val savedMessages = chatDao.getAllMessages()
        messages.addAll(savedMessages)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBarPx = with(density) { bottomPadding.toPx() }
    val actualBottomPadding = with(density) { max(imeBottom.toFloat(), navBarPx).toDp() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 0.dp)
        ) {
            items(messages) { message -> ChatBubble(message = message) }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().padding(bottom = actualBottomPadding)) {
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 6.dp)) {

                // ★追加: アカウント追加ボタンを常に表示
                Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(if (accounts.isEmpty()) "Googleカレンダーと連携して開始" else "別のアカウントを追加")
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it }, placeholder = { Text("メッセージ...") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 3, shape = RoundedCornerShape(24.dp)
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isBlank()) return@IconButton
                            val userText = inputText; inputText = ""
                            val userMsg = ChatMessage(text = userText, isUser = true)
                            messages.add(userMsg)
                            scope.launch { chatDao.insert(userMsg) }

                            scope.launch {
                                if (accounts.isEmpty()) {
                                    val errorMsg = ChatMessage(text = "先にログインしてね", isUser = false)
                                    messages.add(errorMsg)
                                    chatDao.insert(errorMsg)
                                    return@launch
                                }
                                val loading = ChatMessage(text = "...", isUser = false)
                                messages.add(loading)

                                val events = calendarHelper.fetchEvents(context, accounts)
                                val responseText = geminiHelper.consult(userText, events)

                                messages.remove(loading)
                                val aiMsg = ChatMessage(text = responseText, isUser = false)
                                messages.add(aiMsg)
                                chatDao.insert(aiMsg)
                            }
                        },
                        enabled = inputText.isNotBlank(), modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) { Icon(Icons.Default.Send, "相談", tint = Color.White) }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isBlank()) return@IconButton
                            val userText = inputText; inputText = ""
                            val userMsg = ChatMessage(text = userText, isUser = true)
                            messages.add(userMsg)
                            scope.launch { chatDao.insert(userMsg) }

                            scope.launch {
                                if (accounts.isEmpty()) {
                                    val errorMsg = ChatMessage(text = "先にログインしてね", isUser = false)
                                    messages.add(errorMsg)
                                    chatDao.insert(errorMsg)
                                    return@launch
                                }
                                val loading = ChatMessage(text = "...", isUser = false)
                                messages.add(loading)

                                val json = geminiHelper.generateScheduleJson(userText)
                                val schedules = calendarHelper.parseJson(json)

                                messages.remove(loading)
                                val responseText = if (schedules.isNotEmpty()) {
                                    var count = 0
                                    // ★AIでの登録時はとりあえずリストの最初のアカウントに登録
                                    schedules.forEach { if (calendarHelper.addEventToCalendar(context, accounts.first(), it)) count++ }
                                    "$count 件登録しました！"
                                } else {
                                    "予定情報を理解できませんでした。"
                                }

                                val aiMsg = ChatMessage(text = responseText, isUser = false)
                                messages.add(aiMsg)
                                chatDao.insert(aiMsg)
                            }
                        },
                        enabled = inputText.isNotBlank(), modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)
                    ) { Icon(Icons.Default.AddCircle, "登録", tint = Color.White) }
                }
            }
        }
    }
}