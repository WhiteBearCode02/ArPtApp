package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.arptapp.data.remote.AuthSessionStore
import com.example.arptapp.databinding.ActivityResultBinding
import com.example.arptapp.presentation.report.ReportNavigation
import com.example.arptapp.presentation.report.WorkoutResultViewModel
import com.example.arptapp.presentation.report.WorkoutSaveState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** 결과 표시와 사용자 동작만 담당한다. 전달 계약/계산/저장 수명은 보고서 모듈이 소유한다. */
class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private val resultViewModel: WorkoutResultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val report = resultViewModel.resolveReport(ReportNavigation.read(intent))
        // 저장을 시작한 계정에 귀속한다. 나중에 계정이 바뀌어도 다른 계정으로 재귀속하지 않는다.
        val ownerId = AuthSessionStore.current?.userId.orEmpty()
        displayExerciseSummary(report.totalCount)
        resultViewModel.save(report, ownerId)

        binding.btnViewReport.setOnClickListener {
            startActivity(ReportNavigation.intent(this, report))
        }
        binding.btnBackToMain.setOnClickListener { returnToHome() }

        onBackPressedDispatcher.addCallback(this) {
            if (resultViewModel.saveState.value == WorkoutSaveState.Saving) {
                Snackbar.make(binding.root, "운동 기록을 저장하고 있습니다.", Snackbar.LENGTH_SHORT).show()
            } else finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                resultViewModel.saveState.collect { state ->
                    val saving = state == WorkoutSaveState.Saving
                    // DB 저장 도중 화면을 닫아 ViewModel이 취소되는 일반적인 누락을 방지한다.
                    binding.btnBackToMain.isEnabled = !saving
                    binding.btnViewReport.isEnabled = !saving
                    if (state is WorkoutSaveState.Error) {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                            .setAction("다시 시도") { resultViewModel.save(report, ownerId) }
                            .show()
                    }
                }
            }
        }
    }

    private fun displayExerciseSummary(count: Int) {
        binding.tvFinalCount.text = "${count}회"
        binding.tvResultTitle.text = when {
            count >= 20 -> "와우! 완벽한 루틴이었어요! 🔥"
            count >= 10 -> "충분히 잘하고 계세요! 👍"
            else -> "시작이 반이에요! 내일은 더 많이 해봐요. 😊"
        }
    }

    private fun returnToHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
