package com.plexclient.ui.presenters

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.plexclient.api.models.MediaItem

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(vh: ViewHolder, item: Any) {
        val media = item as MediaItem

        // Title
        vh.title.text = when (media.type) {
            "episode" -> {
                val ep = if (media.parentIndex != null && media.index != null) {
                    "S${media.parentIndex.toString().padStart(2, '0')}E${media.index.toString().padStart(2, '0')} · "
                } else ""
                "$ep${media.title}"
            }
            else -> media.title
        }
        vh.title.maxLines = 2

        // Subtitle: metadata line like Plex shows it
        vh.subtitle.text = buildString {
            when (media.type) {
                "movie" -> {
                    media.year?.let { append(it) }
                    media.duration?.let {
                        if (isNotEmpty()) append("  ·  ")
                        val mins = it / 60000
                        val h = mins / 60; val m = mins % 60
                        if (h > 0) append("${h}h ${m}m") else append("${m}m")
                    }
                    media.media.firstOrNull()?.let { mi ->
                        mi.videoResolution?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append(it.uppercase())
                        }
                        mi.videoCodec?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append(it.uppercase())
                        }
                        mi.audioCodec?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append(it.uppercase())
                        }
                    }
                }
                "episode" -> {
                    media.grandparentTitle?.let { append(it) }
                    media.duration?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append("${it / 60000}m")
                    }
                    media.media.firstOrNull()?.let { mi ->
                        mi.videoResolution?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append(it.uppercase())
                        }
                    }
                }
                "show" -> {
                    media.year?.let { append(it) }
                    media.leafCount?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append("$it episodes")
                    }
                }
                "season" -> {
                    media.parentTitle?.let { append(it) }
                    media.leafCount?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append("$it episodes")
                    }
                }
            }
        }

        // Body: plot summary
        vh.body.text = media.summary
        vh.body.maxLines = 6
    }
}
