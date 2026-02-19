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

    // 機能3: 目標達成のための長期プランを作成する
    suspend fun generateGoalPlan(goal: String, deadline: String, hoursPerDay: String, existingEvents: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val today = CalendarHelper().getTodayDate()
                val prompt = """
                    あなたはプロの学習プランナーです。以下の目標を達成するための現実的なスケジュールを立ててください。
                    
                    【目標】$goal
                    【期限】$deadline
                    【希望ペース】1日あたり $hoursPerDay
                    【現在の予定(空き時間を探す用)】
                    $existingEvents
                    
                    【指示】
                    ・今日($today)から期限($deadline)までの間で、既存の予定と被らない時間にタスクを入れてください。
                    ・「単語」「演習」「復習」など、具体的なタスク名にしてください。
                    ・出力は以下のJSON配列形式のみにしてください。会話は不要です。
                    
                    [
                      {
                        "title": "TOEIC単語(P1-10)",
                        "start": "yyyy-MM-ddTHH:mm:ss", 
                        "end": "yyyy-MM-ddTHH:mm:ss",
                        "description": "目標: $goal のための学習"
                      }
                    ]
                """.trimIndent()

                val response = registerModel.generateContent(prompt)
                var json = response.text ?: ""

                json = json.replace("```json", "").replace("```", "").trim()
                val firstIndex = json.indexOf("[")
                val lastIndex = json.lastIndexOf("]")

                if (firstIndex != -1 && lastIndex != -1 && firstIndex <= lastIndex) {
                    json = json.substring(firstIndex, lastIndex + 1)
                } else {
                    return@withContext ""
                }

                json
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    // 新機能: 目標達成に必要な学習量を分析する
    suspend fun analyzeGoal(goal: String, currentStatus: String, resources: String, deadline: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // ★追加: 今日がいつなのかを取得
                val today = CalendarHelper().getTodayDate()

                val prompt = """
                    あなたは学習コンサルタントです。
                    
                    【前提情報】
                    ・今日の日付: $today
                    
                    ユーザーの以下の目標について、必要な総学習時間と1日あたりの目安時間を分析して教えてください。
                    
                    【目標】$goal
                    【現状】$currentStatus
                    【手持ち教材】$resources
                    【期限】$deadline
                    
                    ★重要: ユーザーを待たせないため、出力は合計で「300文字以内」にまとめ、超簡潔に回答してください。
                    以下の形式でお願いします：
                    1. 達成難易度と目安となる総学習時間
                    2. 今日から期限までに間に合わせるための1日あたりの推奨学習時間
                    3. 具体的な学習アドバイス（短く）
                """.trimIndent()

                val response = chatModel.generateContent(prompt)
                response.text ?: "分析できませんでした。"
            } catch (e: Exception) {
                "エラー: ${e.message}"
            }
        }
    }

    // 新機能: 条件に基づいてスケジュール案（ドラフト）を作成する
    suspend fun generateDraftPlan(
        goal: String,
        deadline: String,
        analysis: String,
        userAvailability: String,
        existingEvents: String,
        feedback: String = "" // 修正指示がある場合
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val today = CalendarHelper().getTodayDate()
                val prompt = """
                    あなたはプロの学習スケジューラーです。
                    
                    【前提情報】今日の日付: $today
                    目標: $goal
                    期限: $deadline
                    
                    【AIによる分析結果】
                    $analysis
                    
                    【ユーザーの可処分時間・希望】
                    $userAvailability
                    
                    【既存の予定(これと被らないように)】
                    $existingEvents
                    
                    ${if (feedback.isNotEmpty()) "【ユーザーからの修正指示(最優先)】\n$feedback" else ""}
                    
                    ★重要: 処理速度を上げるため、今日から「最大で直近1週間分（または5件程度）」の学習計画のみを立てて、以下のJSON配列形式で出力してください。（期限までの全日程を作らなくて良いです）
                    
                    出力フォーマット:
                    [
                      {
                        "title": "学習内容",
                        "start": "yyyy-MM-ddTHH:mm:ss", 
                        "end": "yyyy-MM-ddTHH:mm:ss",
                        "description": "詳細"
                      }
                    ]
                """.trimIndent()

                val response = registerModel.generateContent(prompt)
                var json = response.text ?: ""

                json = json.replace("```json", "").replace("```", "").trim()
                val firstIndex = json.indexOf("[")
                val lastIndex = json.lastIndexOf("]")

                if (firstIndex != -1 && lastIndex != -1 && firstIndex <= lastIndex) {
                    json.substring(firstIndex, lastIndex + 1)
                } else {
                    "[]"
                }
            } catch (e: Exception) {
                "[]"
            }
        }
    }
}