package com.leonardospadoni.smartcloset.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.leonardospadoni.smartcloset.api.RetrofitClient
import com.leonardospadoni.smartcloset.model.ClothRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Gestione Permessi (Codice uguale a prima)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        CameraContent(context, lifecycleOwner, onBack)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Richiedi Permesso Camera")
            }
        }
    }
}

@Composable
fun CameraContent(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onBack: () -> Unit
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isUploading by remember { mutableStateOf(false) }

    // --- NUOVO: GESTIONE CATEGORIE ---
    val categories = listOf("Top" to "Maglia/Felpa", "Bottom" to "Pantaloni/Gonna", "Shoes" to "Scarpe")
    var selectedCategoryKey by remember { mutableStateOf(categories[0].first) }
    var expanded by remember { mutableStateOf(false) } // Per il menu a tendina

    Box(modifier = Modifier.fillMaxSize()) {
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // 1. PREVIEW CAMERA
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor: Executor = ContextCompat.getMainExecutor(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder().build()
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                        } catch (e: Exception) { Log.e("Camera", "Error", e) }
                    }, executor)
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. TASTO BACK
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ComposeColor.White)
            }

            // 3. UI CONTROLLI (In basso)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(ComposeColor.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // SELETTORE CATEGORIA
                Box {
                    Button(onClick = { expanded = true }, colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.White)) {
                        Text(text = "Categoria: ${categories.find { it.first == selectedCategoryKey }?.second}", color = ComposeColor.Black)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ComposeColor.Black)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedCategoryKey = key
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // BOTTONE SCATTA
                Button(
                    onClick = {
                        takePhotoAndUpload(context, imageCapture, selectedCategoryKey) { uploading ->
                            isUploading = uploading
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scatta e Salva")
                }
            }
        }
    }
}

// Funzioni aggiornate per accettare la categoria e ruotare l'immagine
fun takePhotoAndUpload(
    context: Context,
    imageCapture: ImageCapture?,
    category: String,
    onUploadStateChange: (Boolean) -> Unit
) {
    val imageCapture = imageCapture ?: return
    onUploadStateChange(true) // Mostra caricamento

    imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val rotation = image.imageInfo.rotationDegrees
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            image.close()

            // Eseguiamo il processamento pesante in una Coroutine
            // (Ruota -> Rimuovi Sfondo -> Invia)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
                try {
                    // 1. Ruota
                    val rotatedBitmap = ImageUtils.rotateBitmap(originalBitmap, rotation)

                    // 2. RIMUOVI SFONDO (Magia ML Kit)
                    // Nota: Stiamo chiamando la funzione che abbiamo creato nel file ImageUtils
                    val cleanBitmap = ImageUtils.removeBackground(rotatedBitmap)

                    // 3. Converti e Invia
                    val base64String = bitmapToBase64(cleanBitmap)
                    sendToServer(context, base64String, category, onUploadStateChange)

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onUploadStateChange(false)
                        Toast.makeText(context, "Errore elaborazione immagine", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        override fun onError(exception: ImageCaptureException) {
            onUploadStateChange(false)
            Log.e("Camera", "Error", exception)
        }
    })
}

fun sendToServer(
    context: Context,
    base64String: String,
    category: String, // <--- NUOVO PARAMETRO
    onUploadStateChange: (Boolean) -> Unit
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            // Ora passiamo la categoria reale invece di "Uncategorized"
            val request = ClothRequest(
                user_id = userId,
                image_base64 = base64String,
                category = category
            )
            val response = RetrofitClient.instance.uploadCloth(request)
            withContext(Dispatchers.Main) {
                onUploadStateChange(false)
                if (response.isSuccessful) Toast.makeText(context, "Salvato come $category!", Toast.LENGTH_LONG).show()
                else Toast.makeText(context, "Errore: ${response.code()}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onUploadStateChange(false)
                Toast.makeText(context, "Errore Rete: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Funzione per ruotare la Bitmap in base ai gradi specificati
fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = android.graphics.Matrix()
    matrix.postRotate(rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// (Le funzioni imageProxyToBitmap e bitmapToBase64 rimangono invariate)
fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 70, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
}