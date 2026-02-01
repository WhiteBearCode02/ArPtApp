package com.example.arptapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// DB 접근 인터페이스
@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: UserEntity): Long

    @Insert
    suspend fun insertHealthProfile(profile: HealthProfile)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    // 4. ID로 사용자 정보 조회 (프로필 로드용)
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): UserEntity?

    // 5. 사용자 계정 정보 수정
    @Update
    suspend fun updateUser(user: UserEntity)

    // 6. 인바디 정보 수정
    @Update
    suspend fun updateHealthProfile(profile: HealthProfile)

    @Query("SELECT * FROM health_profiles WHERE userId = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestHealthProfile(userId: Int): HealthProfile?
}