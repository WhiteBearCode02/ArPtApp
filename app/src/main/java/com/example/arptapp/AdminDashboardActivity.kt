package com.example.arptapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AdminDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val email = intent.getStringExtra("USER_EMAIL").orEmpty()
        setContentView(R.layout.activity_admin_dashboard)
        findViewById<TextView>(R.id.tvAdminEmail).text = email
    }
}
