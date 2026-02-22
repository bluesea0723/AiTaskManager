package com.example.aitaskmanager.ui.screens

import android.accounts.Account
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.calendar.CalendarScopes

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // SharedPreferencesから保存されたメールアドレスのリストを取得
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var accountEmails by remember {
        mutableStateOf(prefs.getStringSet("saved_accounts", emptySet())?.toList() ?: emptyList())
    }

    // メールアドレスから Account オブジェクトのリストを生成
    val signedInAccounts = accountEmails.map { Account(it, "com.google") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            // 取得したメールアドレスを保存
            account.email?.let { email ->
                if (!accountEmails.contains(email)) {
                    val newList = accountEmails + email
                    accountEmails = newList
                    prefs.edit().putStringSet("saved_accounts", newList.toSet()).apply()
                }
            }
        } catch (e: ApiException) {
            e.printStackTrace()
        }
    }

    // ★修正: 変数宣言に `: () -> Unit` を追加し、戻り値の型を明示的に指定します
    val onLoginClick: () -> Unit = {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(CalendarScopes.CALENDAR))
            .build()
        val client = GoogleSignIn.getClient(context, gso)

        // 別のアカウントを追加できるように、一度内部的にサインアウトしてからログイン画面を開く
        client.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("チャット") },
                    selected = navController.currentDestination?.route == "chat",
                    onClick = { navController.navigate("chat") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Flag, contentDescription = "Goal") },
                    label = { Text("目標") },
                    selected = navController.currentDestination?.route == "goal",
                    onClick = { navController.navigate("goal") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Today, contentDescription = "Today") },
                    label = { Text("今日") },
                    selected = navController.currentDestination?.route == "daily",
                    onClick = { navController.navigate("daily") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Month") },
                    label = { Text("今月") },
                    selected = navController.currentDestination?.route == "monthly",
                    onClick = { navController.navigate("monthly") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("chat") {
                Box(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
                    ChatScreen(
                        accounts = signedInAccounts,
                        onLoginClick = onLoginClick,
                        bottomPadding = innerPadding.calculateBottomPadding()
                    )
                }
            }
            composable("goal") {
                Box(modifier = Modifier.padding(innerPadding)) {
                    GoalScreen(accounts = signedInAccounts)
                }
            }
            composable("daily") {
                Box(modifier = Modifier.padding(innerPadding)) {
                    DailyScreen(accounts = signedInAccounts)
                }
            }
            composable("monthly") {
                Box(modifier = Modifier.padding(innerPadding)) {
                    MonthlyScreen(accounts = signedInAccounts)
                }
            }
        }
    }
}