package com.example.arptapp

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AdminDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val email = intent.getStringExtra("USER_EMAIL").orEmpty()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(Color.rgb(30, 30, 30))
            addView(TextView(context).apply {
                text = "관리자 대시보드\n$email"
                textSize = 24f
                setTextColor(Color.WHITE)
            })
        })
    }
}