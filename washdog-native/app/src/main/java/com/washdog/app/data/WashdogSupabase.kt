package com.washdog.app.data

import android.annotation.SuppressLint
import android.content.Context
import com.washdog.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object WashdogSupabase {

    /** Contexto de aplicación, fijado en [com.washdog.app.WashdogApplication.onCreate]. */
    @SuppressLint("StaticFieldLeak") // es applicationContext, no fuga memoria
    lateinit var appContext: Context

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                // Token cifrado en disco (rule #4). Si por algún motivo el contexto no
                // está disponible, se cae al SessionManager por defecto de supabase-kt.
                if (::appContext.isInitialized) {
                    sessionManager = EncryptedSessionManager(appContext)
                }
            }
            install(Postgrest)
        }
    }
}
