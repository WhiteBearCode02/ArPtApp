package com.example.arptapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

/**
 * [ArPtApp - 히스토리 화면 액티비티]
 * 역할: Room DB로부터 운동 기록 리스트를 가져와 RecyclerView에 전시합니다.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. RecyclerView 설정: 리스트 모양(수직형)을 결정합니다.
        binding.rvHistory.layoutManager = LinearLayoutManager(this)

        // 2. [DB 연동] 비동기적으로 모든 운동 기록을 불러옵니다.
        loadHistoryRecords()
    }

    private fun loadHistoryRecords() {
        /**
         * [Coroutines] DB 조회 작업은 무거우므로 백그라운드에서 수행합니다.
         */
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            // DAO를 통해 최신순(id DESC)으로 모든 기록 수신
            val recordList = db.exerciseDao().getAllRecords()

            // 3. 어댑터 연결: 수신된 리스트 데이터를 리사이클러뷰에 주입합니다.
            binding.rvHistory.adapter = ExerciseAdapter(recordList)
        }
    }
}