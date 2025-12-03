package com.leonardospadoni.smartcloset.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh // Usiamo Refresh invece di Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api // IMPORTANTE
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar // IMPORTANTE
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.auth.FirebaseAuth
import com.leonardospadoni.smartcloset.api.RetrofitClient
import com.leonardospadoni.smartcloset.model.Cloth
import com.leonardospadoni.smartcloset.sensor.ShakeDetector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class) // <--- QUESTA RIGA RISOLVE L'ERRORE ROSSO
@Composable
fun OutfitEditorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val lifecycleOwner = LocalLifecycleOwner.current

    // Stato: Lista completa dei vestiti
    var allClothes by remember { mutableStateOf<List<Cloth>>(emptyList()) }

    // Stato: Vestiti attualmente selezionati sulla "tela"
    // Usiamo null se non è selezionato nulla
    var selectedTop by remember { mutableStateOf<Cloth?>(null) }
    var selectedBottom by remember { mutableStateOf<Cloth?>(null) }
    var selectedShoes by remember { mutableStateOf<Cloth?>(null) }

    // --- LOGICA SHAKE TO SHUFFLE ---
    val shakeDetector = remember { ShakeDetector(context) }

    // Funzione per scegliere outfit casuale
    fun shuffleOutfit() {
        if (allClothes.isNotEmpty()) {
            // Nota: Se avessimo le categorie corrette nel DB useremmo quelle.
            // Per ora prendiamo 3 capi a caso dalla lista totale.
            selectedTop = allClothes.randomOrNull()
            selectedBottom = allClothes.randomOrNull()
            selectedShoes = allClothes.randomOrNull()
            Toast.makeText(context, "🔀 Outfit Shuffled!", Toast.LENGTH_SHORT).show()
        }
    }

    // Gestione ciclo di vita del sensore (attiva solo quando la schermata è visibile)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shakeDetector.start {
                    shuffleOutfit() // Callback dello shake
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                shakeDetector.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            shakeDetector.stop()
        }
    }

    // Caricamento dati
    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.instance.getClothes(userId)
            if (response.isSuccessful) {
                allClothes = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Errore caricamento: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- UI ---
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Outfit Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // Se ArrowBack non viene trovato, usa Icons.Default.ArrowBack
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Tasto manuale per chi non vuole scuotere
                    IconButton(onClick = { shuffleOutfit() }) {
                        // Usiamo Refresh perché è un'icona standard sempre disponibile
                        Icon(Icons.Default.Refresh, contentDescription = "Shuffle")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Istruzioni
            Text(
                "Scuoti il telefono per un outfit random! \nO trascina i capi per sistemarli.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            // AREA CANVAS (La tela dell'armadio)
            Box(
                modifier = Modifier
                    .weight(1f) // Prende tutto lo spazio disponibile
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                    .background(Color(0xFFF5F5F5)) // Grigio chiarissimo sfondo
            ) {
                if (allClothes.isEmpty()) {
                    Text("Caricamento vestiti...", Modifier.align(Alignment.Center))
                } else if (selectedTop == null && selectedBottom == null) {
                    Text("Scuoti per iniziare!", Modifier.align(Alignment.Center))
                }

                // Disegna i vestiti selezionati (con posizioni iniziali sfalsate per sembrare un corpo)
                selectedTop?.let { DraggableCloth(it, initialOffsetX = 0f, initialOffsetY = -200f) }
                selectedBottom?.let { DraggableCloth(it, initialOffsetX = 0f, initialOffsetY = 100f) }
                selectedShoes?.let { DraggableCloth(it, initialOffsetX = 0f, initialOffsetY = 400f) }
            }

            // Pulsanti Categorie (Semplificati per ora: pescano un random di quella categoria se esistesse, o random generico)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CategoryButton("Top") { selectedTop = allClothes.randomOrNull() }
                CategoryButton("Pants") { selectedBottom = allClothes.randomOrNull() }
                CategoryButton("Shoes") { selectedShoes = allClothes.randomOrNull() }
            }
        }
    }
}

@Composable
fun CategoryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(80.dp) // Bottoni rotondi grandi
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}