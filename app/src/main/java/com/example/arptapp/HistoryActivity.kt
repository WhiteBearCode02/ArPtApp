package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.arptapp.data.remote.AuthSessionStore
import com.example.arptapp.databinding.ActivityHistoryBinding
import com.example.arptapp.presentation.report.ReportNavigation
import com.example.arptapp.presentation.history.WorkoutHistoryViewModel
import kotlinx.coroutines.launch

/** 현재 계정의 기록을 최신순으로 표시하고 공용 보고서 계약으로 상세 화면을 연다. */
class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val historyViewModel: WorkoutHistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.btnOpenStatistics.setOnClickListener {
            startActivity(Intent(this, WorkoutStatisticsActivity::class.java))
        }
        binding.btnSyncHistory.setOnClickListener { historyViewModel.refresh() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    historyViewModel.state.collect { state ->
                        val current = state.account == AuthSessionStore.workoutAccount.value
                        val records = if (current) state.records else emptyList()
                        binding.rvHistory.adapter = ExerciseAdapter(records, historyViewModel::open)
                        binding.tvEmptyMessage.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvHistorySync.text = if (current) state.message else "로그인이 필요합니다."
                        binding.btnSyncHistory.isEnabled = current && state.account != null && !state.syncing
                    }
                }
                launch {
                    historyViewModel.report.collect { report ->
                        if (report != null && report.ownerUserId == AuthSessionStore.current?.userId) {
                            historyViewModel.reportOpened()
                            startActivity(ReportNavigation.intent(this@HistoryActivity, report))
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        binding.rvHistory.adapter = null // no previous-account frame on returning from another screen
        super.onStop()
    }
}
