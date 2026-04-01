package com.example.aws_dummy_app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AuthLogDao {
    @Insert
    suspend fun insert(log: AuthLogEntity)

    @Query("SELECT * FROM auth_log ORDER BY id DESC")
    suspend fun getAll(): List<AuthLogEntity>

    @Query("DELETE FROM auth_log")       // ← ADD this
    suspend fun deleteAll()
}