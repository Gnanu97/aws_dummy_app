package com.example.aws_dummy_app.network

data class RegisterUserResponse(
    val message: String,
    val sub: String? = null,
    val email: String? = null
)