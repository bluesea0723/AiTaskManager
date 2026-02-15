package com.example.aitaskmanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
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

    // ★ログイン状態をここで一元管理する
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    // ログイン処理
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

    // ログイン関数（子画面から呼べるようにする）
    val onLoginClick = {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(CalendarScopes.CALENDAR))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    // 画面の骨組み (Scaffold)
    Scaffold(
        bottomBar = {
            NavigationBar {
                // タブ1: チャット (Home)
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("チャット") },
                    selected = navController.currentDestination?.route == "chat",
                    onClick = { navController.navigate("chat") }
                )
                // タブ2: カレンダー (Monthly)
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                    label = { Text("今月の予定") },
                    selected = navController.currentDestination?.route == "monthly",
                    onClick = { navController.navigate("monthly") }
                )
            }
        }
    ) { innerPadding ->
        // 画面切り替えエリア
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "chat") {
                composable("chat") {
                    // チャット画面にアカウントとログイン機能を渡す
                    ChatScreen(
                        account = signedInAccount,
                        onLoginClick = onLoginClick
                    )
                }
                composable("monthly") {
                    // カレンダー画面にアカウントを渡す
                    MonthlyScreen(account = signedInAccount)
                }
            }
        }
    }
}