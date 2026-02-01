package com.example.arptapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// 사용자 기본 계정
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val email: String,
    val password: String,
    val nickname: String,
    val joinDate: String
)