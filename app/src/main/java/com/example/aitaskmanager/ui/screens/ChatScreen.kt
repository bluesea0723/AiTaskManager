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
import com.example.aitaskmanager.data.ChatMessage
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.logic.GeminiHelper
import com.example.aitaskmanager.ui.components.ChatBubble
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ChatScreen(
    account: GoogleSignInAccount?,
    onLoginClick: () -> Unit,
    bottomPadding: Dp // ★追加: MainScreenからメニューバーの高さを貰う
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val geminiHelper = remember { GeminiHelper() }
    val calendarHelper = remember { CalendarHelper() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(account) {
        if (account != null) {
            messages.add(ChatMessage(text = "ログインしました！準備OKです。", isUser = false))
        }
    }

    // ★隙間を消す計算ロジック
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density) // キーボードの高さ(px)
    val navBarPx = with(density) { bottomPadding.toPx() } // メニューバーの高さ(px)

    // キーボードが出ている時は「キーボードの高さ」、出ていない時は「メニューバーの高さ」を使う
    val actualBottomPadding = with(density) { max(imeBottom.toFloat(), navBarPx).toDp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // statusBarsのみ適用(top)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // --- チャットログ ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 0.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message)
            }
        }

        // --- 入力エリア ---
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                // ★修正: windowInsetsPadding(ime) をやめて、計算した余白(padding)を適用
                .padding(bottom = actualBottomPadding)
        ) {
            // 前回の修正(top=0.dp)も含めて適用
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 6.dp)) {

                if (account == null) {
                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) { Text("Googleカレンダーと連携して開始") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("メッセージ...") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isBlank()) return@IconButton
                            val userMsg = inputText
                            inputText = ""

                            messages.add(ChatMessage(text = userMsg, isUser = true))
                            scope.launch {
                                if (account == null) {
                                    messages.add(ChatMessage(text = "先にログインしてね", isUser = false))
                                    return@launch
                                }
                                val loading = ChatMessage(text = "...", isUser = false)
                                messages.add(loading)

                                val events = calendarHelper.fetchEvents(context, account)
                                val response = geminiHelper.consult(userMsg, events)

                                messages.remove(loading)
                                messages.add(ChatMessage(text = response, isUser = false))
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "相談", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isBlank()) return@IconButton
                            val userMsg = inputText
                            inputText = ""

                            messages.add(ChatMessage(text = userMsg, isUser = true))
                            scope.launch {
                                if (account == null) {
                                    messages.add(ChatMessage(text = "先にログインしてね", isUser = false))
                                    return@launch
                                }
                                val loading = ChatMessage(text = "...", isUser = false)
                                messages.add(loading)

                                val json = geminiHelper.generateScheduleJson(userMsg)
                                val schedules = calendarHelper.parseJson(json)

                                if (schedules.isNotEmpty()) {
                                    var count = 0
                                    schedules.forEach {
                                        if (calendarHelper.addEventToCalendar(context, account, it)) count++
                                    }
                                    messages.remove(loading)
                                    messages.add(ChatMessage(text = "$count 件登録しました！", isUser = false))
                                } else {
                                    messages.remove(loading)
                                    messages.add(ChatMessage(text = "予定情報を理解できませんでした。", isUser = false))
                                }
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "登録", tint = Color.White)
                    }
                }
            }
        }
    }
}