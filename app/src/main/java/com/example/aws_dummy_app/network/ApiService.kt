package com.example.aws_dummy_app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("register-device")
    suspend fun registerDevice(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("generate")
    suspend fun generateToken(): Response<GenerateTokenResponse>

    // ✅ New — validate token (delivery agent side)
    @POST("validate")
    suspend fun validateToken(@Body request: ValidateTokenRequest): Response<ValidateTokenResponse>

    @POST("register-user")
    suspend fun registerUser(): Response<RegisterUserResponse>
}
