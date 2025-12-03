package com.leonardospadoni.smartcloset.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.leonardospadoni.smartcloset.api.RetrofitClient
import com.leonardospadoni.smartcloset.api.WeatherClient
import com.leonardospadoni.smartcloset.model.Cloth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFabClick: () -> Unit,
    onLogout: () -> Unit, // Callback per il logout
    onOpenEditor: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Stati per i dati
    var clothes by remember { mutableStateOf<List<Cloth>>(emptyList()) }
    var weatherTemp by remember { mutableStateOf<Float?>(null) }
    var weatherDesc by remember { mutableStateOf("Caricamento meteo...") }
    var cityName by remember { mutableStateOf("Posizione...") }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun loadClothes() {
        scope.launch {
            try {
                val response = RetrofitClient.instance.getClothes(userId)
                if (response.isSuccessful) {
                    clothes = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Err Vestiti: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Cielo sereno ☀️"
            1, 2, 3 -> "Nuvoloso ☁️"
            45, 48 -> "Nebbia 🌫️"
            51, 53, 55, 61, 63, 65 -> "Pioggia ☔"
            71, 73, 75 -> "Neve ❄️"
            95, 96, 99 -> "Temporale ⛈️"
            else -> "Variabile"
        }
    }

    fun loadWeather(lat: Double, lon: Double) {
        scope.launch {
            try {
                val response = WeatherClient.instance.getCurrentWeather(lat, lon)
                if (response.isSuccessful) {
                    val w = response.body()
                    if (w != null) {
                        weatherTemp = w.current.temperature_2m
                        weatherDesc = getWeatherDescription(w.current.weather_code)
                    }
                }

                // Geocoding per nome città
                withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lon, 1)
                        if (!addresses.isNullOrEmpty()) {
                            // Proviamo locality (Città) o subAdminArea (Provincia)
                            val city = addresses[0].locality ?: addresses[0].subAdminArea
                            cityName = city ?: "Posizione sconosciuta"
                        }
                    } catch (e: Exception) {
                        cityName = "Errore Posizione"
                    }
                }
            } catch (e: Exception) {
                weatherDesc = "Err Meteo"
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) loadWeather(location.latitude, location.longitude)
            }
        } else {
            weatherDesc = "No GPS Permission"
        }
    }

    LaunchedEffect(Unit) {
        loadClothes()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) loadWeather(location.latitude, location.longitude)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onFabClick) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            // 1. HEADER (Titolo + Refresh + Logout)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SmartCloset", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))

                // Refresh
                IconButton(onClick = { loadClothes() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Ricarica")
                }

                // LOGOUT BUTTON
                IconButton(onClick = onLogout) {
                    // Usa Icons.AutoMirrored.Filled.ExitToApp se hai aggiunto la dipendenza "material-icons-extended"
                    // Altrimenti usa Icons.Default.Close o Icons.Default.AccountCircle
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Esci")
                }
            }

            Button(
                onClick = onOpenEditor,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("✨ Crea Outfit (Editor) ✨")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. BANNER METEO CON CITTÀ
            WeatherBanner(temp = weatherTemp, description = weatherDesc, city = cityName)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Il tuo Armadio", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // 3. GRIGLIA
            if (clothes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun vestito salvato.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clothes) { cloth ->
                        ClothItem(cloth)
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherBanner(temp: Float?, description: String, city: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Mostra la città qui
            Text(text = "📍 $city", style = MaterialTheme.typography.labelLarge)

            Spacer(modifier = Modifier.height(4.dp))

            if (temp != null) {
                Text(text = "${temp.toInt()}°C", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(8.dp))

                val advice = when {
                    temp < 10 -> "Molto freddo! Cappotto e sciarpa 🧣"
                    temp < 15 -> "Freschetto. Giacca o maglione pesante 🧥"
                    temp < 22 -> "Si sta bene. Felpa leggera o maniche lunghe 👕"
                    else -> "Fa caldo! T-shirt e occhiali da sole 😎"
                }
                Text(text = advice, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            } else {
                Text(text = description)
            }
        }
    }
}

@Composable
fun ClothItem(cloth: Cloth) {
    Card(shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            AsyncImage(
                model = cloth.image_url,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                contentScale = ContentScale.Crop
            )
            // Se la categoria è vuota o null, mettiamo "Altro"
            Text(text = cloth.category.ifEmpty { "Capo" }, modifier = Modifier.padding(8.dp))
        }
    }
}