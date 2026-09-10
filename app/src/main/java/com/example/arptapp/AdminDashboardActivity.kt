package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.remote.AppSession
import com.example.arptapp.data.remote.AuthSessionStore
import com.example.arptapp.data.remote.SupabaseAuthRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class AdminDashboardActivity : AppCompatActivity() {
    private val authRepository = SupabaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        findViewById<MaterialButton>(R.id.btnOpenUserHome).apply {
            isEnabled = false
            setOnClickListener { openUserHome() }
        }
        findViewById<MaterialButton>(R.id.btnAdminLogout).apply {
            isEnabled = false
            setOnClickListener { signOut() }
        }

        verifyAdminSession()
    }

    private fun verifyAdminSession() {
        lifecycleScope.launch {
            authRepository.restoreSession()
                .onSuccess { session ->
                    if (session?.isAdmin == true) showAdminDashboard(session) else denyAccess()
                }
                .onFailure { denyAccess() }
        }
    }

    private fun showAdminDashboard(session: AppSession) {
        findViewById<TextView>(R.id.tvAdminEmail).text = session.email
        findViewById<MaterialButton>(R.id.btnOpenUserHome).isEnabled = true
        findViewById<MaterialButton>(R.id.btnAdminLogout).isEnabled = true
    }

    private fun openUserHome() {
        val session = AuthSessionStore.current ?: return
        startActivity(Intent(this, HomeActivity::class.java).apply {
            putExtra("USER_ID", session.userId)
            putExtra("USER_EMAIL", session.email)
            putExtra("IS_ADMIN", true)
        })
    }

    private fun signOut() {
        lifecycleScope.launch {
            authRepository.signOut()
            openLogin()
        }
    }

    private fun denyAccess() {
        Toast.makeText(this, "관리자 권한이 필요한 화면입니다.", Toast.LENGTH_SHORT).show()
        openLogin()
    }

    private fun openLogin() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
