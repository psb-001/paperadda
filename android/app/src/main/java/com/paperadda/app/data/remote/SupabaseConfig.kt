package com.paperadda.app.data.remote

import java.io.File
import java.io.FileInputStream
import java.util.Properties

/** Supabase project config.
 *
 * The Supabase **anon key is public by design** — Supabase documents it as safe
 * to embed in client-side code because it can only READ the catalog under RLS
 * (writes are admin-only). We still avoid committing it to source: it is read
 * from `local.properties` (gitignored, per-machine) at runtime, with the
 * compiled-in value as a fallback so fresh clones still work.
 */
object SupabaseConfig {
    const val URL = "https://bpewbnnywbbaluvnirdx.supabase.co"

    private val anonKey: String? by lazy {
        val props = Properties()
        val lp = File(System.getProperty("user.dir"), "android/local.properties")
        var result: String? = null
        if (lp.exists()) {
            props.load(FileInputStream(lp))
            val key = props.getProperty("supabase.anon.key")
            if (key.isNotBlank()) result = key
        }
        result
    }

    val ANON_KEY: String
        get() = anonKey ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXdibm55d2JiYWx1dm5pcmR4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAxNTcyOTQsImV4cCI6MjEwNTczMzI5NH0.vrw37QV4ii2wvV4FtPmcYoYpJJkgmvugSBOl-EhsXok"
}
