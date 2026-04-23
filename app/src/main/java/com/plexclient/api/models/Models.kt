package com.plexclient.api.models

import java.io.Serializable

data class PlexServer(
    val name: String,
    val address: String,
    val port: Int,
    val scheme: String = "http"
) : Serializable {
    val url: String get() = "$scheme://$address:$port"
}

data class MediaItem(
    val ratingKey: String,
    val title: String,
    val type: String,           // movie, show, season, episode
    val summary: String = "",
    val thumb: String? = null,
    val art: String? = null,
    val year: Int? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,   // resume position in ms
    val viewCount: Int? = null,
    val addedAt: Long? = null,
    val parentTitle: String? = null,
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    val index: Int? = null,         // episode or season number
    val parentIndex: Int? = null,   // season number for episodes
    val leafCount: Int? = null,     // episode count for shows/seasons
    val media: List<MediaInfo> = emptyList(),
    val parentRatingKey: String? = null,
    val grandparentRatingKey: String? = null,
    val roles: List<CastMember> = emptyList(),
    val directors: List<CrewMember> = emptyList(),
    val writers: List<CrewMember> = emptyList(),
    val theme: String? = null,
    val parentTheme: String? = null,
    val grandparentTheme: String? = null
) : Serializable {

    val bestTheme: String?
        get() = theme ?: parentTheme ?: grandparentTheme

    val displayTitle: String
        get() = when (type) {
            "episode" -> {
                val prefix = if (parentIndex != null && index != null) {
                    "S${parentIndex.toString().padStart(2, '0')}E${index.toString().padStart(2, '0')} "
                } else ""
                "$prefix$title"
            }
            "season" -> title
            else -> title
        }

    val displaySubtitle: String?
        get() = when (type) {
            "episode" -> grandparentTitle
            "season" -> leafCount?.let { "$it episode${if (it == 1) "" else "s"}" } ?: parentTitle
            "movie" -> year?.toString()
            "show" -> leafCount?.let { "$it episode${if (it == 1) "" else "s"}" } ?: year?.toString()
            else -> null
        }

    val isPlayable: Boolean
        get() = type == "movie" || type == "episode"

    val hasProgress: Boolean
        get() = viewOffset != null && viewOffset > 0

    val progressPercent: Int
        get() {
            val offset = viewOffset ?: return 0
            val total = duration ?: return 0
            if (total == 0L) return 0
            return ((offset * 100) / total).toInt().coerceIn(0, 100)
        }

    val bestThumb: String?
        get() = thumb ?: grandparentThumb

    val bestArt: String?
        get() = art ?: grandparentArt
}

data class MediaInfo(
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val container: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val videoResolution: String? = null,
    val parts: List<MediaPart> = emptyList()
) : Serializable

data class MediaPart(
    val key: String,
    val duration: Long? = null,
    val file: String? = null,
    val streams: List<MediaStream> = emptyList()
) : Serializable

data class MediaStream(
    val id: Int,
    val streamType: Int,    // 1=video, 2=audio, 3=subtitle
    val codec: String? = null,
    val displayTitle: String? = null,
    val language: String? = null,
    val selected: Boolean = false,
    val default: Boolean = false
) : Serializable

data class Library(
    val key: String,
    val title: String,
    val type: String    // movie, show, artist, photo
) : Serializable

data class Hub(
    val title: String,
    val type: String,
    val hubIdentifier: String,
    val items: List<MediaItem>
) : Serializable

data class PinResponse(
    val id: Int,
    val code: String,
    val authToken: String? = null
)

data class CastMember(
    val name: String,
    val role: String? = null,
    val thumb: String? = null
) : Serializable

data class CrewMember(
    val name: String,
    val thumb: String? = null
) : Serializable
