package com.example.ticketier.data.remote

/**
 * The two values that connect the app to YOUR Supabase project.
 * Both are safe to ship inside the app: the publishable key only lets the app do what your
 * database rules allow. NEVER put a key starting with sb_secret_ (or the database password) here.
 */
object SupabaseConfig {
    // Dashboard > Connect (or Project Settings > General). Looks like https://abcdefghijklmnop.supabase.co
    const val URL = "https://odhtcflfizkfabscbzxj.supabase.co"

    const val PUBLISHABLE_KEY = "sb_publishable_ffqpISMPMbSibWce5-d6AA_HjRAT49A"

    /** False until you have pasted a real URL above. */
    val isConfigured: Boolean
        get() = URL.startsWith("https://") && URL.contains(".supabase.co")
}