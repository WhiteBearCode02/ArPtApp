package com.example.arptapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.databinding.ItemExerciseRecordBinding

/**
 * DB의 운동 기록 리스트를 UI 리스트 항목으로 변환해주는 어댑터입니다.
 */
class ExerciseAdapter(private val records: List<ExerciseRecord>) :
    RecyclerView.Adapter<ExerciseAdapter.RecordViewHolder>() {

    inner class RecordViewHolder(val binding: ItemExerciseRecordBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemExerciseRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        with(holder.binding) {
            tvRecordDate.text = record.date
            tvRecordCount.text = "${record.totalCount}회"
            tvRecordCalories.text = "${String.format("%.1f", record.burnedCalories)} kcal"
        }
    }

    override fun getItemCount(): Int = records.size
}