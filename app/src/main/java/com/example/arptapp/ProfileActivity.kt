package com.example.arptapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.data.UserEntity
import com.example.arptapp.data.HealthProfile
import com.example.arptapp.databinding.ActivityProfileBinding
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. View Binding 초기화 (이 줄에서 에러가 난다면 activity_profile.xml 이름을 확인하세요)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. 세션 정보 로드
        val sharedPref = getSharedPreferences("ArPtAppPrefs", MODE_PRIVATE)
        currentUserId = sharedPref.getInt("logged_in_user_id", -1)

        if (currentUserId != -1) {
            loadUserData()
        } else {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 3. 수정 완료 버튼 클릭 리스너
        binding.btnUpdateProfile.setOnClickListener {
            performUpdate()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ProfileActivity)
            val user = db.userDao().getUserById(currentUserId)
            val profile = db.userDao().getLatestHealthProfile(currentUserId)

            user?.let { binding.etProfileNickname.setText(it.nickname) }
            profile?.let { binding.etProfileWeight.setText(it.weight.toString()) }
        }
    }

    private fun performUpdate() {
        val newNickname = binding.etProfileNickname.text.toString().trim()
        val newWeightString = binding.etProfileWeight.text.toString().trim()

        if (newNickname.isEmpty() || newWeightString.isEmpty()) {
            Toast.makeText(this, "모든 필드를 채워주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ProfileActivity)
            val user = db.userDao().getUserById(currentUserId)
            val profile = db.userDao().getLatestHealthProfile(currentUserId)

            if (user != null && profile != null) {
                // 기존 객체를 복사하여 값만 업데이트 (데이터 무결성 유지)
                val updatedUser = user.copy(nickname = newNickname)
                val updatedProfile = profile.copy(weight = newWeightString.toFloat())

                db.userDao().updateUser(updatedUser)
                db.userDao().updateHealthProfile(updatedProfile)

                Toast.makeText(this@ProfileActivity, "프로필이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}