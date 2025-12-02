package com.leonardospadoni.smartcloset.model

data class ClothRequest(
    val user_id: String,
    val image_base64: String, // La foto trasformata in testo
    val category: String = "Uncategorized", // Default per ora
    val season: String = "All"
)

// Classe per la risposta del server
data class UploadResponse(
    val message: String,
    val url: String?
)