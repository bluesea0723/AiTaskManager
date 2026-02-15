package com.example.aitaskmanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import com.example.aitaskmanager.logic.CalendarHelper
import com.example.aitaskmanager.data.ChatMessage
import com.example.aitaskmanager.logic.GeminiHelper // ★これを使います
import com.example.aitaskmanager.ui.components.ChatBubble
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.launch

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // データの管理
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // 入力状態
    var inputText by remember { mutableStateOf("") }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    // ★ヘルパーの準備（これでAI機能が使えます）
    val geminiHelper = remember { GeminiHelper() }
    val calendarHelper = remember { CalendarHelper() }

    // 自動スクロール
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Googleログイン処理
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            signedInAccount = task.getResult(ApiException::class.java)
            messages.add(ChatMessage(text = "ログインしました！準備OKです。", isUser = false))
        } catch (e: ApiException) {
            messages.add(ChatMessage(text = "ログイン失敗: ${e.statusCode}", isUser = false))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // --- チャットログ ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
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
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                if (signedInAccount == null) {
                    Button(
                        onClick = {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(com.google.android.gms.common.api.Scope(CalendarScopes.CALENDAR))
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            googleSignInLauncher.launch(client.signInIntent)
                        },
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

                    // 相談ボタン
                    IconButton(
                        onClick = {
                            if (inputText.isBlank()) return@IconButton
                            val userMsg = inputText
                            inputText = ""

                            // 画面更新と非同期処理
                            messages.add(ChatMessage(text = userMsg, isUser = true))
                            scope.launch {
                                if (signedInAccount == null) {
                                    messages.add(ChatMessage(text = "先にログインしてね", isUser = false))
                                    return@launch
                                }
                                val loading = ChatMessage(text = "...", isUser = false)
                                messages.add(loading)

                                // ★GeminiHelperを使う！
                                val events = calendarHelper.fetchEvents(context, signedInAccount!!)
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

                    // 登録ボタン
                    IconButton(
                        onClick = {
                            if (inputText.isBlank()) return@IconButton
                            val userMsg = inputText
                            inputText = ""

                            messages.add(ChatMessage(text = userMsg, isUser = true))
                            scope.launch {
                                if (signedInAccount == null) {
                                    messages.add(ChatMessage(text = "先にログインしてね", isUser = false))
                                    return@launch
                                }
                                val loading = ChatMessage(text = "...", isUser = false)
                                messages.add(loading)

                                // ★GeminiHelperを使う！
                                val json = geminiHelper.generateScheduleJson(userMsg)
                                val schedules = calendarHelper.parseJson(json)

                                if (schedules.isNotEmpty()) {
                                    var count = 0
                                    schedules.forEach {
                                        if (calendarHelper.addEventToCalendar(context, signedInAccount!!, it)) count++
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