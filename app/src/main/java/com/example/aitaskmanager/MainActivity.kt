package com.example.aitaskmanager // ★自分のパッケージ名に合わせてください

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.launch

// ★ Step 1 で設定した BuildConfig を使うため、APIキーは直接書きません
// もし BuildConfig がまだエラーなら、一時的に直接 "AIza..." を書いてもOKです

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // --- 状態管理 ---
    var inputText by remember { mutableStateOf("") }
    var chatLog by remember { mutableStateOf("ここに会話が表示されます") }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    // APIキー（local.properties設定済み前提。だめなら直接書く）
    val apiKey = BuildConfig.GEMINI_API_KEY

    // --- AIモデルを2つ用意 ---

    // 1. 登録用モデル（JSONモード）
    val registerModel = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = apiKey,
        generationConfig = generationConfig { responseMimeType = "application/json" }
    )

    // 2. 相談・確認用モデル（通常モード）★新登場！
    val chatModel = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = apiKey
        // JSON設定をしない＝普通の日本語を返せる
    )

    // --- Googleログイン設定 ---
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            signedInAccount = task.getResult(ApiException::class.java)
            chatLog = "ログインしました！「今日の予定を教えて」と聞いてみてください。"
        } catch (e: ApiException) {
            chatLog = "ログイン失敗: ${e.statusCode}"
        }
    }

    // --- 画面レイアウト ---
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // ログインボタン
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
                modifier = Modifier.fillMaxWidth()
            ) { Text("Googleカレンダーと連携") }
        } else {
            Text("ログイン中: ${signedInAccount?.displayName}", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        //振り返りコーチ
        Button(
            onClick = {
                scope.launch {
                    if (signedInAccount == null) {
                        chatLog = "先にログインしてください"
                        return@launch
                    }
                    chatLog = "今日の活動を分析中..."

                    // 1. カレンダーから今日の予定を取得
                    val eventsText = CalendarHelper().fetchEvents(context, signedInAccount!!)

                    // 2. AIに「コーチ」として振る舞ってもらうプロンプト
                    val prompt = """
                        あなたはユーザーの親身な専属コーチです。
                        以下の「今日のスケジュール」と、ユーザーが入力した「一言感想」を元に、
                        今日一日の振り返りフィードバックを作成してください。

                        【ルール】
                        ・まずはユーザーの頑張りを具体的に褒めてください。
                        ・スケジュールが過密だった場合は、休息を促してください。
                        ・予定が少なかった場合は、リラックスできたことを肯定してください。
                        ・最後に、明日に向けたポジティブな一言アドバイスをください。
                        ・口調は優しく、励ますようにしてください。

                        【今日のスケジュール】
                        $eventsText

                        【ユーザーの一言感想】
                        $inputText
                    """.trimIndent()

                    try {
                        // 相談用モデル（chatModel）を使用
                        val response = chatModel.generateContent(prompt)
                        chatLog = response.text ?: "返答なし"
                    } catch (e: Exception) {
                        chatLog = "エラー: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary) // 色を変えて目立たせる
        ) {
            Text("今日の振り返り（コーチング）")
        }

        // 入力エリア
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("例: 今日の予定は？ / 明日10時に会議") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ★ボタンを横並びに配置
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 左のボタン：予定を確認（相談）
            Button(
                onClick = {
                    scope.launch {
                        if (signedInAccount == null) {
                            chatLog = "先にログインしてください"
                            return@launch
                        }
                        chatLog = "カレンダーを確認中..."

                        // 1. カレンダーからデータを取る（今のCalendarHelperは今日分のみ）
                        val eventsText = CalendarHelper().fetchEvents(context, signedInAccount!!)

                        // 2. AIに渡して要約させる
                        val prompt = """
                            あなたは優秀な秘書です。以下のカレンダー情報を元に、ユーザーの質問に答えてください。
                            予定がない場合は、励ましの言葉をかけてください。
                            
                            【カレンダー情報】
                            $eventsText
                            
                            【ユーザーの質問】
                            $inputText
                        """.trimIndent()

                        try {
                            val response = chatModel.generateContent(prompt)
                            chatLog = response.text ?: "返答なし"
                        } catch (e: Exception) {
                            chatLog = "エラー: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("予定を確認") }

            // 右のボタン：予定を登録（以前の機能）
            Button(
                onClick = {
                    scope.launch {
                        chatLog = "登録データを生成中..."
                        try {
                            val prompt = """
                                ユーザー入力から予定を抽出しJSON出力。日付未指定は今日基準。
                                [{"title": "...", "start": "...", "end": "...", "description": "..."}]
                                入力: $inputText
                            """.trimIndent()

                            val response = registerModel.generateContent(prompt) // JSONモデルを使用
                            val json = response.text ?: ""

                            // JSON解析と登録
                            val schedules = CalendarHelper().parseJson(json)
                            if (schedules.isNotEmpty()) {
                                var count = 0
                                schedules.forEach {
                                    if (CalendarHelper().addEventToCalendar(context, signedInAccount!!, it)) count++
                                }
                                chatLog = "$count 件の予定を登録しました！\n確認のため「予定を確認」ボタンを押してみてください。"
                            } else {
                                chatLog = "予定情報を抽出できませんでした。"
                            }
                        } catch (e: Exception) {
                            chatLog = "エラー: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) { Text("予定を登録") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 結果表示
        Text("AIからの返答:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = chatLog,
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
        )
    }
}