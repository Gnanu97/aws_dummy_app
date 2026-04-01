package com.example.aws_dummy_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auth_log")
data class AuthLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sub: String,
    val email: String,
    val loginTime: String
)