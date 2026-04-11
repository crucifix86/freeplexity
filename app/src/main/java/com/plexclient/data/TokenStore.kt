package com.plexclient.data

import android.content.Context
import java.util.UUID

class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("plex_auth", Context.MODE_PRIVATE)

    var authToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var serverName: String?
        get() = prefs.getString(KEY_SERVER_NAME, null)
        set(value) = prefs.edit().putString(KEY_SERVER_NAME, value).apply()

    val clientId: String
        get() {
            var id = prefs.getString(KEY_CLIENT_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_CLIENT_ID, id).apply()
            }
            return id
        }

    val isAuthenticated: Boolean
        get() = authToken != null && serverUrl != null

    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_SERVER_URL)
            .remove(KEY_SERVER_NAME)
            .apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_NAME = "server_name"
        private const val KEY_CLIENT_ID = "client_id"
    }
}
