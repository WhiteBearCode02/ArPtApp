package com.example.arptapp.data.remote

import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.core.coroutines.suspendResult
import com.example.arptapp.data.repository.BodyProfileSource
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 프로필 조회용 모델. 비밀번호·토큰은 이 모델이나 user_profiles에 포함하지 않습니다. */
data class CloudBodyProfile(
    val nickname: String,
    val heightCm: Float?,
    val weightKg: Float?,
    val skeletalMuscleMassKg: Float?,
    val bodyFatPercentage: Float?
)

/**
 * user_profiles PostgREST 어댑터. userId 필터와 서버 RLS를 함께 사용합니다.
 * 신체 이력 생성은 기존 DB trigger의 책임이며 여기에서 별도 insert/재시도를 하지 않습니다.
 */
class UserProfileRepository : BodyProfileSource {
    private val client get() = SupabaseClientProvider.client

    /** After email confirmation, save signup measurements under the authenticated account's RLS. */
    suspend fun saveSignUpProfile(userId: String, profile: SignUpProfile): Result<Unit> = suspendResult {
        check(AuthSessionStore.current?.userId == userId) { "로그인 계정이 일치하지 않습니다." }
        client.from("user_profiles").upsert(SignUpProfileRow(userId, profile.name, profile.weightKg,
            profile.skeletalMuscleMassKg, profile.bodyFatMassKg, profile.bodyFatPercentage)) {
            onConflict = "user_id"
        }
        Unit
    }

    @Serializable private data class SignUpProfileRow(
        @SerialName("user_id") val userId: String,
        val nickname: String,
        @SerialName("weight_kg") val weight: Double?,
        @SerialName("skeletal_muscle_mass_kg") val muscle: Double?,
        @SerialName("body_fat_mass_kg") val fatMass: Double?,
        @SerialName("body_fat_percentage") val fatPercent: Double?
    )

    override suspend fun getProfile(userId: String): Result<CloudBodyProfile?> = suspendResult {
        client.from("user_profiles")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<UserProfileRow>()
            .singleOrNull()
            ?.toDomain()
    }

    override suspend fun saveBodyProfile(userId: String, settings: UserSettings): Result<Unit> = suspendResult {
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

/** 빈 이름/미입력 수치 때문에 기존 기기의 신체정보를 지우지 않는 병합 정책입니다. */
fun UserSettings.withCloudBodyProfile(profile: CloudBodyProfile): UserSettings = copy(
    nickname = profile.nickname.ifBlank { nickname },
    heightCm = profile.heightCm ?: heightCm,
    weightKg = profile.weightKg ?: weightKg,
    skeletalMuscleMassKg = profile.skeletalMuscleMassKg ?: skeletalMuscleMassKg,
    bodyFatPercentage = profile.bodyFatPercentage ?: bodyFatPercentage
)
