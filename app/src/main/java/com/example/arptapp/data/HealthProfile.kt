package com.example.arptapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// 인바디 정보
@Entity(
    tableName = "health_profiles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HealthProfile(
    @PrimaryKey(autoGenerate = true) val profileId: Int = 0,
    val userId: Int,
    val date: String,
    val weight: Float,
    val skeletalMuscleMass: Float,
    val bodyFatMass: Float,
    val bodyFatPercentage: Float
)