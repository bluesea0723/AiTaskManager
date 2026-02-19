package com.example.aitaskmanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.calendar.CalendarScopes
import androidx.compose.material.icons.filled.Flag // 目標アイコン用

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()

    var signedInAccount by remember {
        mutableStateOf(GoogleSignIn.getLastSignedInAccount(context))
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            signedInAccount = task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            e.printStackTrace()
        }
    }

    val onLoginClick = {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(CalendarScopes.CALENDAR))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(client.signInIntent)
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
                // ★追加: 目標タブ
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
        // ★ここを修正！ Boxで囲まず、各画面で余白を調整する
        NavHost(
            navController = navController,
            startDestination = "chat",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("chat") {
                // チャット画面: 上の余白は適用するが、下の余白は「値だけ」渡す
                Box(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
                    ChatScreen(
                        account = signedInAccount,
                        onLoginClick = onLoginClick,
                        bottomPadding = innerPadding.calculateBottomPadding() // 下の余白を渡す
                    )
                }
            }
            // ★追加: 目標画面
            composable("goal") {
                Box(modifier = Modifier.padding(innerPadding)) {
                    GoalScreen(account = signedInAccount)
                }
            }
            composable("daily") {
                // その他の画面: 上下左右の余白を普通に適用
                Box(modifier = Modifier.padding(innerPadding)) {
                    DailyScreen(account = signedInAccount)
                }
            }
            composable("monthly") {
                // その他の画面: 上下左右の余白を普通に適用
                Box(modifier = Modifier.padding(innerPadding)) {
                    MonthlyScreen(account = signedInAccount)
                }
            }
        }
    }
}