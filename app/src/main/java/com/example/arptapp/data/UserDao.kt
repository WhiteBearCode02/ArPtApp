package com.example.arptapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

// DB 접근 인터페이스
@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: UserEntity): Long

    @Insert
    suspend fun insertHealthProfile(profile: HealthProfile)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?
}