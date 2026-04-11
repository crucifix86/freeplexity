package com.plexclient.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.plexclient.api.models.*
import com.plexclient.data.TokenStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlexClient(private val tokenStore: TokenStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val plexHeaders: Map<String, String>
        get() = mapOf(
            "Accept" to "application/json",
            "X-Plex-Client-Identifier" to tokenStore.clientId,
            "X-Plex-Product" to "PlexClient",
            "X-Plex-Version" to "1.0",
            "X-Plex-Platform" to "Android",
            "X-Plex-Platform-Version" to "14",
            "X-Plex-Device" to "Android TV",
            "X-Plex-Device-Name" to "PlexClient"
        )

    // -- Raw HTTP -----------------------------------------------------------

    private fun get(url: String, extraParams: Map<String, String> = emptyMap()): JsonObject {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            tokenStore.authToken?.let { addQueryParameter("X-Plex-Token", it) }
            extraParams.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val request = Request.Builder().url(httpUrl).apply {
            plexHeaders.forEach { (k, v) -> addHeader(k, v) }
        }.build()

        val response = http.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        return JsonParser.parseString(body).asJsonObject
    }

    private fun getContainer(url: String, params: Map<String, String> = emptyMap()): JsonObject {
        val json = get(url, params)
        return json.getAsJsonObject("MediaContainer")
    }

    // -- Server discovery ---------------------------------------------------

    fun getServers(): List<PlexServer> {
        val token = tokenStore.authToken ?: return emptyList()
        val url = "https://plex.tv/api/v2/resources"
        val httpUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("X-Plex-Token", token)
            .addQueryParameter("X-Plex-Client-Identifier", tokenStore.clientId)
            .build()

        val request = Request.Builder().url(httpUrl).apply {
            addHeader("Accept", "application/json")
        }.build()

        val response = http.newCall(request).execute()
        val body = response.body?.string() ?: "[]"
        val arr = JsonParser.parseString(body).asJsonArray

        val servers = mutableListOf<PlexServer>()
        for (element in arr) {
            val obj = element.asJsonObject
            if (!obj.get("provides")?.asString.orEmpty().contains("server")) continue

            val name = obj.get("name")?.asString ?: continue
            val connections = obj.getAsJsonArray("connections") ?: continue

            // Prefer local connections
            var bestUri: String? = null
            var bestLocal = false
            for (conn in connections) {
                val c = conn.asJsonObject
                val local = c.get("local")?.asBoolean ?: false
                val uri = c.get("uri")?.asString ?: continue
                if (bestUri == null || (local && !bestLocal)) {
                    bestUri = uri
                    bestLocal = local
                }
            }

            if (bestUri != null) {
                val parsed = bestUri.toHttpUrl()
                servers.add(PlexServer(
                    name = name,
                    address = parsed.host,
                    port = parsed.port,
                    scheme = parsed.scheme
                ))
            }
        }
        return servers
    }

    // -- Hubs (home screen) -------------------------------------------------

    fun getHubs(serverUrl: String): List<Hub> {
        val container = getContainer("$serverUrl/hubs")
        val hubArray = container.getAsJsonArray("Hub") ?: return emptyList()

        return hubArray.mapNotNull { hubEl ->
            val hubObj = hubEl.asJsonObject
            val title = hubObj.get("title")?.asString ?: return@mapNotNull null
            val type = hubObj.get("type")?.asString ?: ""
            val identifier = hubObj.get("hubIdentifier")?.asString ?: ""
            val metadataArr = hubObj.getAsJsonArray("Metadata")
            val items = metadataArr?.map { parseMediaItem(it.asJsonObject) } ?: emptyList()
            if (items.isEmpty()) return@mapNotNull null
            Hub(title, type, identifier, items)
        }
    }

    // -- Libraries ----------------------------------------------------------

    fun getLibraries(serverUrl: String): List<Library> {
        val container = getContainer("$serverUrl/library/sections")
        val dirArray = container.getAsJsonArray("Directory") ?: return emptyList()

        return dirArray.map { el ->
            val obj = el.asJsonObject
            Library(
                key = obj.get("key").asString,
                title = obj.get("title").asString,
                type = obj.get("type")?.asString ?: "unknown"
            )
        }
    }

    fun getLibraryItems(serverUrl: String, libraryKey: String): List<MediaItem> {
        val container = getContainer("$serverUrl/library/sections/$libraryKey/all")
        return parseMetadataArray(container)
    }

    fun getRecentlyAdded(serverUrl: String, count: Int = 50): List<MediaItem> {
        val container = getContainer(
            "$serverUrl/library/recentlyAdded",
            mapOf("X-Plex-Container-Start" to "0", "X-Plex-Container-Size" to count.toString())
        )
        return parseMetadataArray(container)
    }

    fun getOnDeck(serverUrl: String): List<MediaItem> {
        val container = getContainer("$serverUrl/library/onDeck")
        return parseMetadataArray(container)
    }

    // -- Metadata -----------------------------------------------------------

    fun getMetadata(serverUrl: String, ratingKey: String): MediaItem? {
        val container = getContainer("$serverUrl/library/metadata/$ratingKey")
        return parseMetadataArray(container).firstOrNull()
    }

    fun getChildren(serverUrl: String, ratingKey: String): List<MediaItem> {
        val container = getContainer("$serverUrl/library/metadata/$ratingKey/children")
        return parseMetadataArray(container)
    }

    // -- Search -------------------------------------------------------------

    fun search(serverUrl: String, query: String): List<MediaItem> {
        val container = getContainer(
            "$serverUrl/hubs/search",
            mapOf("query" to query, "limit" to "20")
        )
        val hubs = container.getAsJsonArray("Hub") ?: return emptyList()
        val results = mutableListOf<MediaItem>()
        for (hubEl in hubs) {
            val hubObj = hubEl.asJsonObject
            val metadata = hubObj.getAsJsonArray("Metadata") ?: continue
            results.addAll(metadata.map { parseMediaItem(it.asJsonObject) })
        }
        return results
    }

    // -- Playback URLs ------------------------------------------------------

    fun getDirectPlayUrl(serverUrl: String, item: MediaItem): String? {
        val partKey = item.media.firstOrNull()?.parts?.firstOrNull()?.key ?: return null
        val token = tokenStore.authToken ?: return null
        return "$serverUrl$partKey?X-Plex-Token=$token"
    }

    fun getTranscodeUrl(serverUrl: String, item: MediaItem, startOffset: Long = 0): String {
        val token = tokenStore.authToken ?: ""
        return "$serverUrl/video/:/transcode/universal/start.m3u8".toHttpUrl().newBuilder()
            .addQueryParameter("path", "/library/metadata/${item.ratingKey}")
            .addQueryParameter("mediaIndex", "0")
            .addQueryParameter("partIndex", "0")
            .addQueryParameter("protocol", "hls")
            .addQueryParameter("offset", (startOffset / 1000).toString())
            .addQueryParameter("fastSeek", "1")
            .addQueryParameter("directPlay", "0")
            .addQueryParameter("directStream", "1")
            .addQueryParameter("subtitleSize", "100")
            .addQueryParameter("audioBoost", "100")
            .addQueryParameter("autoAdjustQuality", "0")
            .addQueryParameter("maxVideoBitrate", "20000")
            .addQueryParameter("videoQuality", "100")
            .addQueryParameter("videoResolution", "1920x1080")
            .addQueryParameter("X-Plex-Token", token)
            .addQueryParameter("X-Plex-Client-Identifier", tokenStore.clientId)
            .addQueryParameter("X-Plex-Platform", "Android")
            .build()
            .toString()
    }

    // -- Playback state reporting -------------------------------------------

    fun reportProgress(serverUrl: String, ratingKey: String, timeMs: Long, duration: Long, state: String) {
        try {
            // Use /:/progress for updating the watch position on the server
            get("$serverUrl/:/progress", mapOf(
                "key" to ratingKey,
                "identifier" to "com.plexapp.plugins.library",
                "time" to timeMs.toString(),
                "state" to state
            ))

            // Also hit /:/timeline so other clients and the dashboard see it
            get("$serverUrl/:/timeline", mapOf(
                "ratingKey" to ratingKey,
                "key" to "/library/metadata/$ratingKey",
                "identifier" to "com.plexapp.plugins.library",
                "time" to timeMs.toString(),
                "duration" to duration.toString(),
                "state" to state,
                "X-Plex-Client-Identifier" to tokenStore.clientId
            ))
        } catch (_: Exception) {}
    }

    fun markWatched(serverUrl: String, ratingKey: String) {
        try {
            get("$serverUrl/:/scrobble", mapOf("identifier" to "com.plexapp.plugins.library", "key" to ratingKey))
        } catch (_: Exception) {}
    }

    fun markUnwatched(serverUrl: String, ratingKey: String) {
        try {
            get("$serverUrl/:/unscrobble", mapOf("identifier" to "com.plexapp.plugins.library", "key" to ratingKey))
        } catch (_: Exception) {}
    }

    // -- Image URLs ---------------------------------------------------------

    fun getImageUrl(serverUrl: String, path: String?, width: Int = 300, height: Int = 450): String? {
        if (path == null) return null
        val token = tokenStore.authToken ?: return null
        return "$serverUrl/photo/:/transcode".toHttpUrl().newBuilder()
            .addQueryParameter("url", path)
            .addQueryParameter("width", width.toString())
            .addQueryParameter("height", height.toString())
            .addQueryParameter("minSize", "1")
            .addQueryParameter("X-Plex-Token", token)
            .build()
            .toString()
    }

    // -- Parsing ------------------------------------------------------------

    private fun parseMetadataArray(container: JsonObject): List<MediaItem> {
        val arr = container.getAsJsonArray("Metadata") ?: return emptyList()
        return arr.map { parseMediaItem(it.asJsonObject) }
    }

    private fun parseMediaItem(obj: JsonObject): MediaItem {
        return MediaItem(
            ratingKey = obj.get("ratingKey")?.asString ?: "",
            title = obj.get("title")?.asString ?: "Unknown",
            type = obj.get("type")?.asString ?: "unknown",
            summary = obj.get("summary")?.asString ?: "",
            thumb = obj.get("thumb")?.asString,
            art = obj.get("art")?.asString,
            year = obj.get("year")?.asInt,
            duration = obj.get("duration")?.asLong,
            viewOffset = obj.get("viewOffset")?.asLong,
            viewCount = obj.get("viewCount")?.asInt,
            addedAt = obj.get("addedAt")?.asLong,
            parentTitle = obj.get("parentTitle")?.asString,
            grandparentTitle = obj.get("grandparentTitle")?.asString,
            grandparentThumb = obj.get("grandparentThumb")?.asString,
            grandparentArt = obj.get("grandparentArt")?.asString,
            index = obj.get("index")?.asInt,
            parentIndex = obj.get("parentIndex")?.asInt,
            leafCount = obj.get("leafCount")?.asInt,
            parentRatingKey = obj.get("parentRatingKey")?.asString,
            grandparentRatingKey = obj.get("grandparentRatingKey")?.asString,
            media = parseMedia(obj),
            roles = parseRoles(obj),
            directors = parseCrew(obj, "Director"),
            writers = parseCrew(obj, "Writer")
        )
    }

    private fun parseRoles(obj: JsonObject): List<CastMember> {
        val arr = obj.getAsJsonArray("Role") ?: return emptyList()
        return arr.map { el ->
            val r = el.asJsonObject
            CastMember(
                name = r.get("tag")?.asString ?: "",
                role = r.get("role")?.asString,
                thumb = r.get("thumb")?.asString
            )
        }
    }

    private fun parseCrew(obj: JsonObject, key: String): List<CrewMember> {
        val arr = obj.getAsJsonArray(key) ?: return emptyList()
        return arr.map { el ->
            val c = el.asJsonObject
            CrewMember(
                name = c.get("tag")?.asString ?: "",
                thumb = c.get("thumb")?.asString
            )
        }
    }

    private fun parseMedia(obj: JsonObject): List<MediaInfo> {
        val mediaArr = obj.getAsJsonArray("Media") ?: return emptyList()
        return mediaArr.map { mediaEl ->
            val m = mediaEl.asJsonObject
            MediaInfo(
                videoCodec = m.get("videoCodec")?.asString,
                audioCodec = m.get("audioCodec")?.asString,
                container = m.get("container")?.asString,
                width = m.get("width")?.asInt,
                height = m.get("height")?.asInt,
                bitrate = m.get("bitrate")?.asInt,
                videoResolution = m.get("videoResolution")?.asString,
                parts = parseParts(m)
            )
        }
    }

    private fun parseParts(mediaObj: JsonObject): List<MediaPart> {
        val partArr = mediaObj.getAsJsonArray("Part") ?: return emptyList()
        return partArr.map { partEl ->
            val p = partEl.asJsonObject
            MediaPart(
                key = p.get("key")?.asString ?: "",
                duration = p.get("duration")?.asLong,
                file = p.get("file")?.asString,
                streams = parseStreams(p)
            )
        }
    }

    private fun parseStreams(partObj: JsonObject): List<MediaStream> {
        val streamArr = partObj.getAsJsonArray("Stream") ?: return emptyList()
        return streamArr.map { streamEl ->
            val s = streamEl.asJsonObject
            MediaStream(
                id = s.get("id")?.asInt ?: 0,
                streamType = s.get("streamType")?.asInt ?: 0,
                codec = s.get("codec")?.asString,
                displayTitle = s.get("displayTitle")?.asString,
                language = s.get("language")?.asString,
                selected = s.get("selected")?.asBoolean ?: false,
                default = s.get("default")?.asBoolean ?: false
            )
        }
    }
}
