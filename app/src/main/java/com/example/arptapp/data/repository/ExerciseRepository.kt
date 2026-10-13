package com.example.arptapp.data.repository

import android.content.Context
import com.example.arptapp.data.model.StandardPose
import com.google.gson.Gson
import java.io.InputStreamReader

/** 표준 자세 asset의 읽기 전용 어댑터. 운동 판정/점수 계산은 domain analyzer에서 수행합니다. */
class ExerciseRepository(private val context: Context) {
    
    private val gson = Gson()
    
    /**
     * Assets에서 표준 자세 데이터 로드
     */
    fun loadStandardPose(exerciseType: String): StandardPose? {
        return try {
            val fileName = "${exerciseType.lowercase()}.json"
            InputStreamReader(context.assets.open("standard_poses/$fileName")).use { reader ->
                gson.fromJson(reader, StandardPose::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 모든 운동 종목 목록 반환
     */
    fun getAvailableExercises(): List<String> {
        return try {
            context.assets.list("standard_poses")
                ?.map { it.removeSuffix(".json").uppercase() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
