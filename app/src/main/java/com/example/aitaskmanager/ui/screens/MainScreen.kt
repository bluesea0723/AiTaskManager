package com.example.aitaskmanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // ★ここを変更！ アプリ起動時に「前回のログイン情報」を取得して初期値にする
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
                // タブ1: チャット
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("チャット") },
                    selected = navController.currentDestination?.route == "chat",
                    onClick = { navController.navigate("chat") }
                )
                // タブ2: 今日の予定 (時系列)
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Today, contentDescription = "Today") },
                    label = { Text("今日") },
                    selected = navController.currentDestination?.route == "daily",
                    onClick = { navController.navigate("daily") }
                )
                // タブ3: 今月の予定 (カレンダー表)
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Month") },
                    label = { Text("今月") },
                    selected = navController.currentDestination?.route == "monthly",
                    onClick = { navController.navigate("monthly") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "chat") {
                composable("chat") {
                    ChatScreen(account = signedInAccount, onLoginClick = onLoginClick)
                }
                composable("daily") {
                    DailyScreen(account = signedInAccount)
                }
                composable("monthly") {
                    MonthlyScreen(account = signedInAccount)
                }
            }
        }
    }
}