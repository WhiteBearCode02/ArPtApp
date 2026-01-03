package com.example.arptapp.data // 이 파일이 위치한 주소(패키지 경로)입니다.

// [Room Library: 데이터베이스 설계를 위한 도구들을 불러옵니다]
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [ArPtApp - 운동 기록 데이터 엔티티]
 * * 역할: 
 * - 이 클래스는 SQLite 데이터베이스 내의 'exercise_records'라는 이름의 표(Table)를 정의합니다.
 * - 사용자가 운동을 마칠 때마다 [날짜, 횟수, 시간, 칼로리] 데이터를 하나의 행(Row)으로 묶어 저장합니다.
 * - @Entity: 이 클래스가 DB의 테이블임을 AI 컴파일러에게 알려주는 표식입니다.
 */
@Entity(tableName = "exercise_records") 
data class ExerciseRecord(

    /**
     * [Primary Key: 데이터 식별 번호]
     * - 각 운동 기록이 섞이지 않도록 부여하는 고유한 번호입니다.
     * - autoGenerate = true: 우리가 번호를 직접 매기지 않아도, DB가 1번부터 차례대로 부여합니다.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * [Date: 운동을 수행한 날짜]
     * - 예: "2026-01-03 20:30"
     * - 나중에 사용자가 "내가 지난주 토요일에 몇 번 했지?"라고 물을 때 답변하기 위한 기초 데이터입니다.
     */
    val date: String,

    /**
     * [Total Count: 총 스쿼트 횟수]
     * - DashboardActivity에서 AI가 최종적으로 계산한 운동 개수입니다.
     */
    val totalCount: Int,

    /**
     * [Duration: 운동 소요 시간]
     * - 단위: 초(Long 타입).
     * - "몇 분 동안 운동했는가"를 계산하기 위해 저장하는 정밀한 시간 데이터입니다.
     */
    val duration: Long,

    /**
     * [Burned Calories: 소모 칼로리]
     * - 단위: kcal.
     * - 횟수와 시간 정보를 조합하여 산출된 에너지 소모량입니다.
     */
    val burnedCalories: Double
)