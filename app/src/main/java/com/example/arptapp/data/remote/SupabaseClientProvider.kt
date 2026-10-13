package com.example.arptapp.data.remote

import com.example.arptapp.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Auth/PostgREST가 공유하는 지연 초기화 클라이언트. 화면 진입만으로 별도 클라이언트를 생성하지 않습니다.
 * BuildConfig에는 공개용 publishable/anon 키만 넣어야 하며 service_role/secret 키는 앱에 포함하면 안 됩니다.
 */
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
