// network/RegisterRequest.kt
package com.example.aws_dummy_app.network

data class RegisterRequest(
    val name: String,
    val phone: String,
    val email: String,
    val mac: String,          // for device validation (not stored in users table)
    val random_id: String,    // for device validation (not stored in users table)
    val ssid: String,         // ← renamed from wifi_ssid to match RDS column
    val wifi_password: String
)
