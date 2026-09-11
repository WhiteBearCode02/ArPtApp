package com.example.arptapp.data.remote

import com.example.arptapp.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    val client by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
            "Supabase 설정이 없습니다. local.properties를 확인해 주세요."
        }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Auth) {
                scheme = "arptapp"
                host = "auth"
            }
            install(Postgrest)
        }
    }
}
