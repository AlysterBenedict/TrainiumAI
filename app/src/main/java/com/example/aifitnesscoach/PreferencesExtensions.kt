package com.example.aifitnesscoach

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

/**
 * Returns a SharedPreferences instance whose filename is dynamically suffixed 
 * depending on whether the user is logged in via Google or as a local guest.
 * 
 * - Google Auth: ${name}_google_<uid>
 * - Local/Guest: ${name}_local
 * - Default: ${name}
 */
fun Context.getTrainiumPrefs(name: String): SharedPreferences {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val resolvedName = if (currentUser != null) {
        "${name}_google_${currentUser.uid}"
    } else {
        val globalPrefs = getSharedPreferences("global_prefs", Context.MODE_PRIVATE)
        val isLocal = globalPrefs.getBoolean("is_local_user", false)
        if (isLocal) {
            "${name}_local"
        } else {
            name
        }
    }
    return getSharedPreferences(resolvedName, Context.MODE_PRIVATE)
}
