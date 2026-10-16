package com.example.arptapp.data

// [Imports: Room 데이터베이스 구축을 위한 필수 라이브러리]
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * [AIRPTCoach - 데이터베이스 관리 총괄 클래스]
 * * 역할:
 * - 앱 전체에서 사용하는 데이터베이스의 중심점 역할을 합니다.
 * - 데이터베이스 인스턴스를 생성하고, 정의한 DAO(ExerciseDao)를 외부에 제공합니다.
 * - @Database: 이 클래스가 데이터베이스임을 선언하고, 포함될 엔티티와 버전을 명시합니다.
 */
@Database(
    entities = [
        ExerciseRecord::class
    ],
    version = 6,
    exportSchema = false
)
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
                // Explicit upgrade paths preserve existing workout rows. Unsupported old versions
                // fail closed instead of silently deleting a user's database.
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                
                INSTANCE = instance
                instance
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_records ADD COLUMN exerciseType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercise_records ADD COLUMN averageScore REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercise_records ADD COLUMN scoresJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE exercise_records ADD COLUMN feedbackMessage TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_records ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                // 계정과 비밀번호는 Supabase Auth만 관리합니다.
                db.execSQL("DROP TABLE IF EXISTS health_profiles")
                db.execSQL("DROP TABLE IF EXISTS users")
            }
        }

        /** Additive nullable field: legacy rows read as null without rewriting stored analyses. */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_records ADD COLUMN yoloDiagnosticsJson TEXT")
            }
        }
    }
}
