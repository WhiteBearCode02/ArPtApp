package com.example.arptapp.data

// [Imports: Room 데이터베이스 구축을 위한 필수 라이브러리]
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * [ArPtApp - 데이터베이스 관리 총괄 클래스]
 * * 역할:
 * - 앱 전체에서 사용하는 데이터베이스의 중심점 역할을 합니다.
 * - 데이터베이스 인스턴스를 생성하고, 정의한 DAO(ExerciseDao)를 외부에 제공합니다.
 * - @Database: 이 클래스가 데이터베이스임을 선언하고, 포함될 엔티티와 버전을 명시합니다.
 */
@Database(entities = [ExerciseRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // [DAO 연결] 외부에서 이 함수를 통해 운동 기록 데이터에 접근할 수 있게 합니다.
    abstract fun exerciseDao(): ExerciseDao

    /**
     * [싱글톤 패턴 구현 부분]
     * - 데이터베이스 객체는 생성 비용이 매우 크기 때문에, 앱 전체에서 단 하나만 존재해야 합니다.
     * - companion object를 사용하여 정적(static) 인스턴스를 관리합니다.
     */
    companion object {
        // @Volatile: 메인 메모리에 실시간으로 반영되도록 하여 여러 스레드에서의 동기화 문제를 방지합니다.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * [데이터베이스 인스턴스 가져오기 함수]
         * - 인스턴스가 이미 있으면 기존 것을 반환하고, 없으면 새로 생성합니다.
         */
        fun getDatabase(context: Context): AppDatabase {
            // synchronized: 여러 곳에서 동시에 생성 요청이 와도 하나만 만들어지도록 잠금(Lock)을 겁니다.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "arpt_database" // 스마트폰 내부에 저장될 실제 DB 파일 이름입니다.
                )
                // [Migration 전략] 버전이 올라갔을 때 기존 데이터를 어떻게 처리할지 정합니다. 
                // 지금은 초기 단계이므로 기존 데이터를 지우고 새로 만드는 방식을 채택합니다.
                .fallbackToDestructiveMigration() 
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}