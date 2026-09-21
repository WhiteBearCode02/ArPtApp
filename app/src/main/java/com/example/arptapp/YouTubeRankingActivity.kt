package com.example.arptapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.remote.RankedVideo
import com.example.arptapp.data.remote.RankingCategory
import com.example.arptapp.databinding.ActivityYoutubeRankingBinding
import com.example.arptapp.viewmodel.RankingUiState
import com.example.arptapp.viewmodel.YouTubeRankingViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class YouTubeRankingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityYoutubeRankingBinding
    private val viewModel: YouTubeRankingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYoutubeRankingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarRanking.setNavigationOnClickListener { finish() }
        binding.btnSquatRanking.setOnClickListener { viewModel.load(RankingCategory.SQUAT) }
        binding.btnShoulderRanking.setOnClickListener { viewModel.load(RankingCategory.SHOULDER_PRESS) }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                val loading = state is RankingUiState.Loading
                binding.progressRanking.visibility = if (loading) View.VISIBLE else View.GONE
                binding.btnSquatRanking.isEnabled = !loading
                binding.btnShoulderRanking.isEnabled = !loading
                when (state) {
                    RankingUiState.Idle -> binding.tvRankingStatus.text =
                        "운동을 선택하면 조회수 기준 영상 순위를 표시합니다."
                    is RankingUiState.Loading -> {
                        binding.tvRankingStatus.text = "${state.category.label} 영상을 조회하는 중입니다."
                        binding.listRankingVideos.removeAllViews()
                    }
                    is RankingUiState.Error -> {
                        binding.tvRankingStatus.text = state.message
                        binding.listRankingVideos.removeAllViews()
                    }
                    is RankingUiState.Content -> {
                        val ranking = state.ranking
                        val cacheLabel = when (ranking.cacheStatus) {
                            "HIT" -> "3시간 캐시"
                            "STALE" -> "마지막 저장 결과"
                            else -> "새로 조회한 결과"
                        }
                        binding.tvRankingStatus.text = buildString {
                            append("${ranking.category.label} · 조회수 내림차순 · $cacheLabel")
                            ranking.message?.let { append("\n$it") }
                        }
                        showVideos(ranking.videos)
                    }
                }
            }
        }
    }

    private fun showVideos(videos: List<RankedVideo>) {
        binding.listRankingVideos.removeAllViews()
        if (videos.isEmpty()) {
            binding.listRankingVideos.addView(label("표시할 공개 영상이 없습니다.", 15f))
            return
        }
        videos.forEachIndexed { index, video ->
            val card = MaterialCardView(this).apply {
                radius = resources.displayMetrics.density * 16
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.videoUrl)))
                }
            }
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                addView(label("${index + 1}. ${video.title}", 17f))
                addView(label(video.channelTitle, 13f))
                val views = video.viewCount?.let { NumberFormat.getNumberInstance(Locale.KOREA).format(it) }
                    ?: "정보 없음"
                addView(label("조회수 $views · 좋아요 ${video.likeCount ?: "정보 없음"}", 13f))
            }
            card.addView(column)
            binding.listRankingVideos.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * resources.displayMetrics.density).toInt() })
        }
    }

    private fun label(text: String, size: Float): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(getColor(R.color.text_primary))
    }
}
