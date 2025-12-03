package com.leonardospadoni.smartcloset.model

data class Cloth(
    val id: Int,
    val image_url: String, // URL server
    val category: String,
    val season: String
)