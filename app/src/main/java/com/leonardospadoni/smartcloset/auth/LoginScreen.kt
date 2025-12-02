package com.leonardospadoni.smartcloset.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SmartCloset Login", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if(email.isNotEmpty() && password.isNotEmpty()) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { onLoginSuccess() }
                        .addOnFailureListener {
                            // Se fallisce il login, proviamo a registrare l'utente automaticamente
                            // (Solo per semplificare lo sviluppo, in produzione si fanno due schermate diverse)
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { onLoginSuccess() }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entra / Registrati")
        }
    }
}