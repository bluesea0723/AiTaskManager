package com.example.aitaskmanager // ★自分のパッケージ名に合わせてください

import android.os.Bundle
import android.widget.Toast
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
    var responseJson by remember { mutableStateOf("") } // AIの生JSON
    var parsedSchedules by remember { mutableStateOf<List<AiSchedule>>(emptyList()) } // 解析後のリスト
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) } // ログイン中のユーザー
    var statusMessage by remember { mutableStateOf("まずはGoogleでログインしてください") }

    // --- Geminiの設定 ---
    val generativeModel = GenerativeModel(
        modelName = "gemini-3-flash-preview", // ★動くモデル名にしてください
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig { responseMimeType = "application/json" }
    )

    // --- Googleログインの準備 ---
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            statusMessage = "ログインしました: ${account.displayName}"
        } catch (e: ApiException) {
            statusMessage = "ログイン失敗: ${e.statusCode}"
        }
    }

    // --- UIのレイアウト ---
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // 1. ログインボタン（未ログイン時のみ表示）
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
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Googleカレンダーと連携（ログイン）")
            }
        } else {
            Text("ログイン中: ${signedInAccount?.email}", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 入力エリア
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("例：明日10時から会議") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. AI送信ボタン
        Button(
            onClick = {
                scope.launch {
                    statusMessage = "AIが思考中..."
                    try {
                        val prompt = """
                            ユーザーの入力から予定を抽出しJSONで出力。
                            日付未指定は今日(2026-02-14)基準。
                            フォーマット: [{"title": "...", "start": "YYYY-MM-DDTHH:mm:00", "end": "...", "description": "..."}]
                            入力: $inputText
                        """.trimIndent()

                        val response = generativeModel.generateContent(prompt)
                        responseJson = response.text ?: ""

                        // JSONを解析してリストにする
                        parsedSchedules = CalendarHelper().parseJson(responseJson)

                        statusMessage = if (parsedSchedules.isNotEmpty()) {
                            "AI解析完了！登録ボタンを押してください"
                        } else {
                            "予定が見つかりませんでした"
                        }
                    } catch (e: Exception) {
                        statusMessage = "AIエラー: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = signedInAccount != null // ログインしないと押せない
        ) {
            Text("AIに相談")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. カレンダー登録ボタン（AI解析後に表示）
        if (parsedSchedules.isNotEmpty()) {
            Button(
                onClick = {
                    scope.launch {
                        statusMessage = "カレンダーに書き込み中..."
                        val helper = CalendarHelper()
                        var successCount = 0

                        parsedSchedules.forEach { schedule ->
                            val isSuccess = helper.addEventToCalendar(context, signedInAccount!!, schedule)
                            if (isSuccess) successCount++
                        }

                        statusMessage = if (successCount > 0) {
                            "カレンダーに $successCount 件登録しました！"
                        } else {
                            "登録に失敗しました"
                        }
                        // 完了したらリセット
                        if (successCount > 0) parsedSchedules = emptyList()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("カレンダーに登録 (${parsedSchedules.size}件)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ステータス表示
        Text(text = statusMessage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(8.dp))

        // デバッグ用（AIの生JSON表示）
        Text("Raw JSON:", style = MaterialTheme.typography.labelSmall)
        Text(
            text = responseJson,
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            style = MaterialTheme.typography.bodySmall
        )
    }
}