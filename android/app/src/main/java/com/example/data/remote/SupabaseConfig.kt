package com.example.data.remote

/** Supabase project config. The anon key is public by design:
 *  it can only READ the catalog (writes are admin-only via RLS). */
object SupabaseConfig {
    const val URL = "https://bpewbnnywbbaluvnirdx.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXdibm55d2JiYWx1dm5pcmR4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAxNTcyOTQsImV4cCI6MjEwNTczMzI5NH0.vrw37QV4ii2wvV4FtPmcYoYpJJkgmvugSBOl-EhsXok"
}
