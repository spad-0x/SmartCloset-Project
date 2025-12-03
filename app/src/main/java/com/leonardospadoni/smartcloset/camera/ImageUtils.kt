package com.leonardospadoni.smartcloset.camera

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

object ImageUtils {

    suspend fun removeBackground(inputBitmap: Bitmap): Bitmap {
        // 1. Configura ML Kit per cercare oggetti (vestiti)
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap() // Chiediamo direttamente l'immagine ritagliata
            .build()

        val segmenter = SubjectSegmentation.getClient(options)
        val image = InputImage.fromBitmap(inputBitmap, 0)

        return try {
            // 2. Elabora l'immagine (può richiedere qualche secondo)
            val result = segmenter.process(image).await()

            // 3. Prendi il risultato (Foreground)
            // Se non trova nulla, restituisce l'originale
            result.foregroundBitmap ?: inputBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            inputBitmap // In caso di errore, torna l'originale
        }
    }

    // Funzione helper per ruotare (copiata da prima)
    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}