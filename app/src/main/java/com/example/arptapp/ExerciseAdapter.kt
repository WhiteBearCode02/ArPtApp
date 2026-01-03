package com.example.arptapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.databinding.ItemExerciseRecordBinding

/**
 * [ArPtApp - 운동 기록 어댑터]
 * 역할: 데이터베이스의 ExerciseRecord 리스트를 RecyclerView의 UI 항목으로 변환합니다.
 */
class ExerciseAdapter(private val records: List<ExerciseRecord>) : 
    RecyclerView.Adapter<ExerciseAdapter.RecordViewHolder>() {

    // [ViewHolder: 개별 항목의 뷰를 보관하는 객체]
    inner class RecordViewHolder(val binding: ItemExerciseRecordBinding) : 
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        // XML 레이아웃을 가져와서 ViewHolder 객체를 생성합니다.
        val binding = ItemExerciseRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        // [Data Binding] 실제 데이터를 UI 텍스트뷰에 하나씩 대입합니다.
        val record = records[position]
        with(holder.binding) {
            tvDate.text = record.date
            tvCount.text = "${record.totalCount}회"
            tvCalories.text = "${record.burnedCalories} kcal"
        }
    }

    override fun getItemCount(): Int = records.size // 전체 아이템 개수를 반환합니다.
}