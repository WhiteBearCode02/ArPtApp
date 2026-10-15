package com.example.arptapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.arptapp.data.remote.SupabaseAuthRepository
import java.security.MessageDigest

/** Rotation retains only the in-memory repository/capability and link digest, never saved tokens. */
class PasswordRecoveryViewModel : ViewModel() {
    val repository = SupabaseAuthRepository()
    private var verifiedLinkDigest: String? = null
    private fun digest(raw: String?) = raw?.let {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    }
    fun markVerified(raw: String?) { verifiedLinkDigest = digest(raw) }
    fun isVerified(raw: String?) = verifiedLinkDigest != null && verifiedLinkDigest == digest(raw) &&
        repository.hasPasswordRecoveryAuthorization()
    fun invalidate() { verifiedLinkDigest = null }
}
