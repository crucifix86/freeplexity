package com.plexclient.api

import com.google.gson.JsonParser
import com.plexclient.api.models.PinResponse
import com.plexclient.data.TokenStore
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlexAuth(private val tokenStore: TokenStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun requestPin(): PinResponse {
        val body = FormBody.Builder()
            .add("strong", "true")
            .add("X-Plex-Product", "PlexClient")
            .add("X-Plex-Client-Identifier", tokenStore.clientId)
            .add("X-Plex-Platform", "Android")
            .add("X-Plex-Platform-Version", "14")
            .add("X-Plex-Device", "Android TV")
            .add("X-Plex-Device-Name", "PlexClient")
            .build()

        val request = Request.Builder()
            .url("https://plex.tv/api/v2/pins")
            .post(body)
            .addHeader("Accept", "application/json")
            .build()

        val response = http.newCall(request).execute()
        val json = JsonParser.parseString(response.body?.string()).asJsonObject

        return PinResponse(
            id = json.get("id").asInt,
            code = json.get("code").asString,
            authToken = json.get("authToken")?.asString?.takeIf { it.isNotEmpty() }
        )
    }

    fun checkPin(pinId: Int): String? {
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/pins/$pinId")
            .addHeader("Accept", "application/json")
            .addHeader("X-Plex-Client-Identifier", tokenStore.clientId)
            .build()

        val response = http.newCall(request).execute()
        val json = JsonParser.parseString(response.body?.string()).asJsonObject

        val token = json.get("authToken")?.asString
        return if (token != null && token.isNotEmpty() && token != "null") token else null
    }
}
