package com.leonardospadoni.smartcloset.api

import com.leonardospadoni.smartcloset.model.Cloth
import com.leonardospadoni.smartcloset.model.ClothRequest
import com.leonardospadoni.smartcloset.model.UploadResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @POST("clothes")
    suspend fun uploadCloth(@Body request: ClothRequest): Response<UploadResponse>

    // NUOVO: Scarica la lista
    @GET("clothes")
    suspend fun getClothes(@Query("user_id") userId: String): Response<List<Cloth>>

    @retrofit2.http.DELETE("clothes")
    suspend fun deleteCloth(
        @retrofit2.http.Query("image_url") imageUrl: String,
        @retrofit2.http.Query("user_id") userId: String
    ): retrofit2.Response<Void>
}