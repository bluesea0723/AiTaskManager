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
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.calendar.CalendarScopes

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // ★複数アカウントを保持するリストに変更
    val accounts = remember { mutableStateListOf<GoogleSignInAccount>() }

    // 起動時に前回ログインしていたアカウントがあれば追加
    LaunchedEffect(Unit) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && accounts.none { it.email == account.email }) {
            accounts.add(account)
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val newAccount = task.getResult(ApiException::class.java)
            if (newAccount != null && accounts.none { it.email == newAccount.email }) {
                accounts.add(newAccount)
            }
        } catch (e: ApiException) {
            e.printStackTrace()
        }
    }

    val onLoginClick: () -> Unit = {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(CalendarScopes.CALENDAR))
            .build()
        val client = GoogleSignIn.getClient(context, gso)

        // ★別のアカウントを追加できるように、一度サインアウトを挟んでからIntentを起動
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
                    ChatScreen(accounts = accounts, onLoginClick = onLoginClick, bottomPadding = innerPadding.calculateBottomPadding())
                }
            }
            composable("goal") {
                Box(modifier = Modifier.padding(innerPadding)) {
                    GoalScreen(accounts = accounts)
                }
            }
            composable("daily") {
                Box(modifier = Modifier.padding(innerPadding)) {
                    DailyScreen(accounts = accounts)
                }
            }
            composable("monthly") {
                Box(modifier = Modifier.padding(innerPadding)) {
                    MonthlyScreen(accounts = accounts)
                }
            }
        }
    }
}