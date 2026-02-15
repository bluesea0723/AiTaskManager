package com.example.aitaskmanager.logic

import com.example.aitaskmanager.BuildConfig
// CalendarHelperを使うために必要であればimportを確認してください
// (同じパッケージなら不要ですが、念のため)
import com.example.aitaskmanager.logic.CalendarHelper
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiHelper {
    // APIキーとモデル名
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val modelName = BuildConfig.GEMINI_MODEL_NAME

    // 相談用モデル
    private val chatModel = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey
    )

    // 登録用モデル
    private val registerModel = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        generationConfig = generationConfig { responseMimeType = "application/json" }
    )

    // 機能1: ユーザーの相談に答える
    suspend fun consult(userMessage: String, eventsText: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // ★追加: 今日がいつなのかを取得
                val today = CalendarHelper().getTodayDate()

                // ★変更: AIへの命令（プロンプト）に日付情報を入れる
                val prompt = """
                    【前提情報】
                    ・現在日時: $today (日本時間)
                    ・あなたの役割: ユーザーのスケジュール管理をサポートする優秀な秘書
                    
                    【カレンダー情報】
                    $eventsText
                    
                    【ユーザーの質問】
                    $userMessage
                    
                    上記カレンダー情報を踏まえて、日付や曜日を明確にしながら親切に回答してください。
                    「明日」や「来週」などの言葉があった場合は、現在日時を基準に判断してください。
                """.trimIndent()

                val response = chatModel.generateContent(prompt)
                response.text ?: "すみません、答えられませんでした。"
            } catch (e: Exception) {
                "エラーが発生しました: ${e.message}"
            }
        }
    }

    // 機能2: 予定登録のためのJSONを作成する
    suspend fun generateScheduleJson(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val today = CalendarHelper().getTodayDate()
                val prompt = """
                    ユーザー入力から予定を抽出し、以下のJSON配列形式で出力してください。
                    現在の日付は ($today) です。「明日」などはこの日付を基準に計算してください。
                    
                    出力フォーマット:
                    [
                      {
                        "title": "予定のタイトル",
                        "start": "yyyy-MM-ddTHH:mm:ss", 
                        "end": "yyyy-MM-ddTHH:mm:ss",
                        "description": "メモや詳細"
                      }
                    ]
                    
                    ユーザー入力: $userMessage
                """.trimIndent()

                val response = registerModel.generateContent(prompt)
                response.text?.replace("```json", "")?.replace("```", "")?.trim() ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}