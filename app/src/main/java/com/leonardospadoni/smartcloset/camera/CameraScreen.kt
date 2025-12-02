package com.leonardospadoni.smartcloset.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Per gestire lo scatto
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Stato caricamento e errori
    var isUploading by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // Anteprima Camera (Proviamo a mostrarla, se fallisce mostriamo errore ma non crashiamo)
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
                                cameraError = true // Attiva la modalità fallback UI
                            }
                        }, executor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // UI di Errore Camera (per emulatore)
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Fotocamera non disponibile (Emulatore?)")
                    Text("Usa il tasto Debug per testare l'upload")
                }
            }

            // CONTROLLI (Bottoni)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bottone Scatta (Reale)
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

                // Bottone Debug (Simulazione) - Sempre visibile o solo in caso di errore
                Button(
                    onClick = {
                        uploadPlaceholderImage(context) { uploading -> isUploading = uploading }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Red)
                ) {
                    Text("Simula Scatto (Debug Mode)")
                }
            }
        }
    }
}

// Funzione reale: Scatta dalla camera
fun takePhotoAndUpload(
    context: Context,
    imageCapture: ImageCapture?,
    onUploadStateChange: (Boolean) -> Unit
) {
    val imageCapture = imageCapture ?: return
    onUploadStateChange(true)

    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                val base64String = bitmapToBase64(bitmap)
                sendToServer(context, base64String, onUploadStateChange)
            }

            override fun onError(exception: ImageCaptureException) {
                onUploadStateChange(false)
                Log.e("Camera", "Photo capture failed: ${exception.message}", exception)
                Toast.makeText(context, "Errore Camera: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

// Funzione Debug: Crea un'immagine finta e la invia
fun uploadPlaceholderImage(context: Context, onUploadStateChange: (Boolean) -> Unit) {
    onUploadStateChange(true)

    // Crea una Bitmap colorata finta (es. 500x500 rosso)
    val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.RED) // Colora tutto di rosso

    val base64String = bitmapToBase64(bitmap)

    Toast.makeText(context, "Caricamento immagine di test...", Toast.LENGTH_SHORT).show()
    sendToServer(context, base64String, onUploadStateChange)
}

// Logica comune di invio al server
fun sendToServer(context: Context, base64String: String, onUploadStateChange: (Boolean) -> Unit) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"

    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            val request = ClothRequest(user_id = userId, image_base64 = base64String)
            val response = RetrofitClient.instance.uploadCloth(request)

            withContext(Dispatchers.Main) {
                onUploadStateChange(false)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Salvato con successo!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Errore Server: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onUploadStateChange(false)
                Toast.makeText(context, "Errore Rete: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Funzioni di utilità
fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}