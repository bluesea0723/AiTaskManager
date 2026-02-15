package com.example.aitaskmanager // ★あなたのパッケージ名に合わせてください

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // システムバーの領域もアプリで使えるようにする（Edge-to-Edge）
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen()
                }
            }
        }
    }
}

// --- データクラス ---
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// --- UIコンポーネント ---
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    val apiKey = BuildConfig.GEMINI_API_KEY

    val registerModel = remember {
        GenerativeModel(
            modelName = BuildConfig.GEMINI_MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig { responseMimeType = "application/json" }
        )
    }
    val chatModel = remember {
        GenerativeModel(
            modelName = BuildConfig.GEMINI_MODEL_NAME,
            apiKey = apiKey
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            signedInAccount = task.getResult(ApiException::class.java)
            messages.add(ChatMessage(text = "ログインしました！", isUser = false))
        } catch (e: ApiException) {
            messages.add(ChatMessage(text = "ログイン失敗: ${e.statusCode}", isUser = false))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // --- 1. チャットエリア ---
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

        // --- 2. 入力エリア ---
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                // ★★★ ここが修正ポイント ★★★
                // .union を使うことで、「キーボードが出ている時はキーボードの高さ」
                // 「出ていない時はナビゲーションバーの高さ」に自動で切り替わります。
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
                    ) { Text("連携して開始") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("メッセージ...") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )

                    IconButton(
                        onClick = {
                            sendMessage(inputText, false, messages, scope, signedInAccount, chatModel, registerModel, context)
                            inputText = ""
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "相談", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            sendMessage(inputText, true, messages, scope, signedInAccount, chatModel, registerModel, context)
                            inputText = ""
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "登録", tint = Color.White)
                    }
                }
            }
        }
    }
}

// 吹き出しのデザイン
@Composable
fun ChatBubble(message: ChatMessage) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val isUser = message.isUser

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormat.format(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// 送信ロジック
fun sendMessage(
    text: String,
    isRegister: Boolean,
    messages: androidx.compose.runtime.snapshots.SnapshotStateList<ChatMessage>,
    scope: CoroutineScope,
    account: GoogleSignInAccount?,
    chatModel: GenerativeModel,
    registerModel: GenerativeModel,
    context: Context
) {
    if (text.isBlank()) return
    messages.add(ChatMessage(text = text, isUser = true))

    scope.launch {
        if (account == null) {
            messages.add(ChatMessage(text = "先に「連携」ボタンを押してログインしてください。", isUser = false))
            return@launch
        }

        // 思考中メッセージを表示
        val loadingMsg = ChatMessage(text = "...", isUser = false)
        messages.add(loadingMsg)

        try {
            val responseText = if (isRegister) {
                // --- 登録モード ---
                val prompt = """
                    ユーザー入力から予定を抽出しJSON配列で出力してください。
                    日付が指定されていない場合は今日(${CalendarHelper().getTodayDate()})を基準にしてください。
                    出力フォーマット: [{"title": "...", "start": "yyyy-MM-ddTHH:mm:ss", "end": "yyyy-MM-ddTHH:mm:ss", "description": "..."}]
                    入力: $text
                """.trimIndent()

                val res = registerModel.generateContent(prompt)
                // JSONのコードブロック記号 ```json ... ``` がある場合に除去する処理
                val json = res.text?.replace("```json", "")?.replace("```", "")?.trim() ?: ""

                val schedules = CalendarHelper().parseJson(json)
                if (schedules.isNotEmpty()) {
                    var count = 0
                    schedules.forEach {
                        if (CalendarHelper().addEventToCalendar(context, account, it)) count++
                    }
                    if (count > 0) "$count 件の予定をカレンダーに登録しました！" else "登録に失敗しました。"
                } else {
                    "予定情報をうまく読み取れませんでした。\n「明日10時に会議」のように言ってみてください。"
                }
            } else {
                // --- 相談モード ---
                val eventsText = CalendarHelper().fetchEvents(context, account)
                val prompt = """
                    あなたは親切な秘書です。
                    【カレンダー情報】
                    $eventsText
                    
                    【ユーザーの質問】
                    $text
                    
                    ユーザーの質問に対し、カレンダー情報を踏まえて答えてください。
                """.trimIndent()
                val res = chatModel.generateContent(prompt)
                res.text ?: "すみません、うまく答えられませんでした。"
            }

            // 思考中を消して返答を表示
            messages.remove(loadingMsg)
            messages.add(ChatMessage(text = responseText, isUser = false))
        } catch (e: Exception) {
            messages.remove(loadingMsg)
            e.printStackTrace()
            messages.add(ChatMessage(text = "エラーが発生しました: ${e.localizedMessage}", isUser = false))
        }
    }
}