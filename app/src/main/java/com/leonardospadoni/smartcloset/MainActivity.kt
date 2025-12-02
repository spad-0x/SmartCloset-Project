package com.leonardospadoni.smartcloset

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.leonardospadoni.smartcloset.auth.LoginScreen
import com.leonardospadoni.smartcloset.camera.CameraScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    // Richiesta permessi fotocamera
    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permesso accordato
            } else {
                // Gestire diniego (opzionale)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Chiediamo subito il permesso
        cameraPermissionRequest.launch(Manifest.permission.CAMERA)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = FirebaseAuth.getInstance()
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        // Se loggato, mostra la fotocamera (per questa settimana)
        CameraScreen()
    }
}
