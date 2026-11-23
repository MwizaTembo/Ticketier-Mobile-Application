package com.example.ticketier.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * The ONE connection to Supabase, shared by the whole app.
 * `lazy` = it is created the first time something uses it, not when the app starts.
 * Auth handles sign-in and remembers the session. Postgrest reads and writes the tables.
 */
object SupabaseProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Functions)
            install(Postgrest)
            install(Storage)   // for event pictures


        }
    }
}