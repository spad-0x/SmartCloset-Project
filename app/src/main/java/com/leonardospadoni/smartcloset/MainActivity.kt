package com.leonardospadoni.smartcloset

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.leonardospadoni.smartcloset.auth.LoginScreen
import com.leonardospadoni.smartcloset.camera.CameraScreen
import com.leonardospadoni.smartcloset.ui.HomeScreen
import com.leonardospadoni.smartcloset.ui.OutfitEditorScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NON serve più chiedere i permessi Camera qui.
        // Lo fa direttamente CameraScreen.kt

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

enum class Screen {
    LOGIN, HOME, CAMERA, EDITOR
}

@Composable
fun AppNavigation() {
    val auth = FirebaseAuth.getInstance()
    var currentScreen by remember { mutableStateOf(if(auth.currentUser != null) Screen.HOME else Screen.LOGIN) }

    when(currentScreen) {
        Screen.LOGIN -> {
            LoginScreen(onLoginSuccess = { currentScreen = Screen.HOME })
        }
        Screen.HOME -> {
            HomeScreen(
                onFabClick = { currentScreen = Screen.CAMERA },
                onLogout = {
                    auth.signOut()
                    currentScreen = Screen.LOGIN
                },
                onOpenEditor = { currentScreen = Screen.EDITOR } // <--- 2. COLLEGA IL BOTTONE ALLA ROTTA
            )
        }
        Screen.CAMERA -> {
            CameraScreen(onBack = { currentScreen = Screen.HOME })
        }
        Screen.EDITOR -> {
            // 3. AGGIUNGI LA SCHERMATA EDITOR
            // (Assicurati che importi com.leonardospadoni.smartcloset.ui.OutfitEditorScreen)
            OutfitEditorScreen(onBack = { currentScreen = Screen.HOME })
        }
    }
}