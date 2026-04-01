// ValidateTokenResponse.kt
package com.example.aws_dummy_app.network

data class ValidateTokenResponse(
    val status: String,
    val message: String?// "DOOR_UNLOCKED", "INVALID_TOKEN", "TOKEN_EXPIRED", "TOKEN_ALREADY_USED"
)
