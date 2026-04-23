package com.plexclient.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.util.UUID

enum class UserRole { ADMIN, RESTRICTED }

data class UserProfile(
    val id: String,
    val name: String,
    val role: UserRole,
    val pin: String? = null,                  // 4-digit, plaintext for now (local-only, not a secret)
    val allowedLibraryKeys: List<String> = emptyList(),   // empty = all (admin default)
    val maxRating: String? = null,            // null = no cap. Values: "G","PG","PG-13","TV-Y","TV-G","TV-PG","TV-14","TV-MA", etc.
    val avatarColor: Int = 0xFFE5A00D.toInt() // accent gold default
) : Serializable {
    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when (parts.size) {
                0 -> "?"
                1 -> parts[0].take(1).uppercase()
                else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
            }
        }
}

class UserStore(context: Context) {

    private val prefs = context.getSharedPreferences("freeplexity_users", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<UserProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<UserProfile>>(json, object : TypeToken<List<UserProfile>>() {}.type)
        }.getOrNull() ?: emptyList()
    }

    fun save(profiles: List<UserProfile>) {
        prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
    }

    fun upsert(profile: UserProfile) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == profile.id }
        if (idx >= 0) all[idx] = profile else all.add(profile)
        save(all)
    }

    fun remove(id: String) {
        save(getAll().filter { it.id != id })
        if (activeUserId == id) activeUserId = null
    }

    var activeUserId: String?
        get() = prefs.getString(KEY_ACTIVE_ID, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_ACTIVE_ID) else putString(KEY_ACTIVE_ID, value)
        }.apply()

    val activeUser: UserProfile?
        get() = activeUserId?.let { id -> getAll().firstOrNull { it.id == id } }

    val isEmpty: Boolean
        get() = getAll().isEmpty()

    val hasAdmin: Boolean
        get() = getAll().any { it.role == UserRole.ADMIN }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Whether the active user is allowed to see the given library key.
     * Admins see everything. Restricted users see only libraries in their allowlist.
     * If there's no active user, we default to visible (safe during initial load).
     */
    fun canAccessLibrary(libraryKey: String): Boolean {
        val user = activeUser ?: return true
        if (user.role == UserRole.ADMIN) return true
        return libraryKey in user.allowedLibraryKeys
    }

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_user_id"

        fun newProfile(
            name: String,
            role: UserRole,
            pin: String? = null,
            allowedLibraryKeys: List<String> = emptyList(),
            maxRating: String? = null,
            avatarColor: Int = 0xFFE5A00D.toInt()
        ): UserProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            role = role,
            pin = pin,
            allowedLibraryKeys = allowedLibraryKeys,
            maxRating = maxRating,
            avatarColor = avatarColor
        )
    }
}
