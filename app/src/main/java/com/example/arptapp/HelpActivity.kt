package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val openedFromSignUp = intent.getBooleanExtra(EXTRA_FROM_SIGN_UP, false)
        binding.btnHelpCreateAccount.visibility = if (openedFromSignUp) View.GONE else View.VISIBLE
        binding.btnHelpClose.text = getString(
            if (openedFromSignUp) R.string.help_back_to_signup else R.string.help_close
        )

        binding.toolbarHelp.setNavigationOnClickListener { finish() }
        binding.btnHelpClose.setOnClickListener { finish() }
        binding.btnHelpCreateAccount.setOnClickListener {
            startActivity(Intent(this, JoinActivity::class.java))
            finish()
        }
    }

    companion object {
        const val EXTRA_FROM_SIGN_UP = "extra_from_sign_up"
    }
}
