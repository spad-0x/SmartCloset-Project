package com.leonardospadoni.smartcloset.api

import com.leonardospadoni.smartcloset.model.ClothRequest
import com.leonardospadoni.smartcloset.model.UploadResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("clothes")
    suspend fun uploadCloth(@Body request: ClothRequest): Response<UploadResponse>
}