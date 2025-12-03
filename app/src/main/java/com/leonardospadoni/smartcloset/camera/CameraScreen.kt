package com.leonardospadoni.smartcloset.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    // Stato per i permessi
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher per richiedere il permesso
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    // Chiedi il permesso appena si apre la schermata (se non c'è già)
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        // SE ABBIAMO IL PERMESSO -> MOSTRA LA CAMERA VERA
        CameraContent(context, lifecycleOwner, onBack)
    } else {
        // SE NON ABBIAMO IL PERMESSO -> MOSTRA SCHERMATA DI RICHIESTA
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Serve il permesso della fotocamera per continuare.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Concedi Permesso")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) {
                Text("Torna Indietro")
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
    var cameraError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            if (!cameraError) {
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
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("Camera", "Camera initialization failed", e)
                                cameraError = true
                            }
                        }, executor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Errore inizializzazione Camera")
                }
            }

            // TASTO BACK
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ComposeColor.White
                )
            }

            // CONTROLLI
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!cameraError) {
                    Button(
                        onClick = {
                            takePhotoAndUpload(context, imageCapture) { uploading ->
                                isUploading = uploading
                            }
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text("Scatta Foto")
                    }
                }
                // Debug button rimasto per sicurezza
                Button(
                    onClick = { uploadPlaceholderImage(context) { isUploading = it } },
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Red)
                ) {
                    Text("Debug Upload")
                }
            }
        }
    }
}

// ... (Le funzioni helper takePhotoAndUpload, uploadPlaceholderImage, sendToServer, ecc. rimangono UGUALI a prima) ...
// Incollale qui sotto dal file precedente se non le hai salvate, o lasciale se stai modificando il file esistente.
// Per completezza, ecco le funzioni helper minime:

fun takePhotoAndUpload(context: Context, imageCapture: ImageCapture?, onUploadStateChange: (Boolean) -> Unit) {
    val imageCapture = imageCapture ?: return
    onUploadStateChange(true)
    imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = imageProxyToBitmap(image)
            image.close()
            sendToServer(context, bitmapToBase64(bitmap), onUploadStateChange)
        }
        override fun onError(exception: ImageCaptureException) {
            onUploadStateChange(false)
            Log.e("Camera", "Error: ${exception.message}")
        }
    })
}

fun uploadPlaceholderImage(context: Context, onUploadStateChange: (Boolean) -> Unit) {
    onUploadStateChange(true)
    val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.RED)
    sendToServer(context, bitmapToBase64(bitmap), onUploadStateChange)
}

fun sendToServer(context: Context, base64String: String, onUploadStateChange: (Boolean) -> Unit) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            val request = ClothRequest(user_id = userId, image_base64 = base64String)
            val response = RetrofitClient.instance.uploadCloth(request)
            withContext(Dispatchers.Main) {
                onUploadStateChange(false)
                if (response.isSuccessful) Toast.makeText(context, "Salvato!", Toast.LENGTH_LONG).show()
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

fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
}