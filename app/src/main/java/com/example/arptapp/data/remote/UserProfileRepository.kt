package com.example.arptapp.data.remote

import com.example.arptapp.data.preferences.UserSettings
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class CloudBodyProfile(
    val nickname: String,
    val heightCm: Float?,
    val weightKg: Float?,
    val skeletalMuscleMassKg: Float?,
    val bodyFatPercentage: Float?
)

class UserProfileRepository {
    private val client get() = SupabaseClientProvider.client

    suspend fun getProfile(userId: String): Result<CloudBodyProfile?> = runCatching {
        client.from("user_profiles")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<UserProfileRow>()
            .singleOrNull()
            ?.toDomain()
    }

    suspend fun saveBodyProfile(userId: String, settings: UserSettings): Result<Unit> = runCatching {
        client.from("user_profiles").upsert(
            ProfileWriteRow(
                userId = userId,
                nickname = settings.nickname,
                heightCm = settings.heightCm,
                weightKg = settings.weightKg,
                skeletalMuscleMassKg = settings.skeletalMuscleMassKg,
                bodyFatPercentage = settings.bodyFatPercentage
            )
        ) {
            onConflict = "user_id"
        }
        Unit
    }

    @Serializable
    private data class UserProfileRow(
        @SerialName("user_id") val userId: String,
        val nickname: String = "",
        @SerialName("height_cm") val heightCm: Float? = null,
        @SerialName("weight_kg") val weightKg: Float? = null,
        @SerialName("skeletal_muscle_mass_kg") val skeletalMuscleMassKg: Float? = null,
        @SerialName("body_fat_percentage") val bodyFatPercentage: Float? = null
    ) {
        fun toDomain() = CloudBodyProfile(
            nickname = nickname,
            heightCm = heightCm,
            weightKg = weightKg,
            skeletalMuscleMassKg = skeletalMuscleMassKg,
            bodyFatPercentage = bodyFatPercentage
        )
    }

    @Serializable
    private data class ProfileWriteRow(
        @SerialName("user_id") val userId: String,
        val nickname: String,
        @SerialName("height_cm") val heightCm: Float?,
        @SerialName("weight_kg") val weightKg: Float?,
        @SerialName("skeletal_muscle_mass_kg") val skeletalMuscleMassKg: Float?,
        @SerialName("body_fat_percentage") val bodyFatPercentage: Float?
    )
}

fun UserSettings.withCloudBodyProfile(profile: CloudBodyProfile): UserSettings = copy(
    nickname = profile.nickname.ifBlank { nickname },
    heightCm = profile.heightCm ?: heightCm,
    weightKg = profile.weightKg ?: weightKg,
    skeletalMuscleMassKg = profile.skeletalMuscleMassKg ?: skeletalMuscleMassKg,
    bodyFatPercentage = profile.bodyFatPercentage ?: bodyFatPercentage
)
