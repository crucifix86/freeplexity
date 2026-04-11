package com.plexclient.ui.playback

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import com.plexclient.PlexApp
import com.plexclient.api.models.MediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.withContext

class PlaybackFragment : VideoSupportFragment() {

    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore

    private var player: ExoPlayer? = null
    private var glue: PlexTransportControlGlue? = null
    private lateinit var item: MediaItem
    private var resumePlayback = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            reportProgress("playing")
            progressHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        item = requireActivity().intent.getSerializableExtra(PlaybackActivity.EXTRA_ITEM) as? MediaItem
            ?: run { requireActivity().finish(); return }
        resumePlayback = requireActivity().intent.getBooleanExtra(PlaybackActivity.EXTRA_RESUME, false)
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        reportProgress("stopped")
        releasePlayer()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializePlayer() {
        val context = requireContext()
        val prefs = context.getSharedPreferences("plex_settings", 0)

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableAudioTrackPlaybackParams(true)

        player = ExoPlayer.Builder(context, renderersFactory)
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(30_000)
            .setSeekBackIncrementMs(10_000)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage("en")
                    .build()
            }

        val playerAdapter = LeanbackPlayerAdapter(context, player!!, UPDATE_INTERVAL_MS.toInt())

        glue = PlexTransportControlGlue(
            context = requireActivity(),
            playerAdapter = playerAdapter,
            onSubtitles = { showSubtitlePicker() },
            onAudioTrack = { showAudioTrackPicker() }
        ).apply {
            host = VideoSupportFragmentGlueHost(this@PlaybackFragment)
            title = when (item.type) {
                "episode" -> "${item.grandparentTitle ?: ""} - ${item.displayTitle}"
                else -> item.title
            }
            subtitle = item.displaySubtitle ?: ""
            isSeekEnabled = true
            seekProvider = ExoSeekProvider(player!!)
        }

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> {
                        reportProgress("stopped")
                        markWatchedIfComplete()
                        playNextEpisode()
                    }
                    Player.STATE_READY -> {
                        startProgressReporting()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startProgressReporting()
                else { stopProgressReporting(); reportProgress("paused") }
            }
        })

        startPlayback()
    }

    private fun startPlayback() {
        val serverUrl = tokenStore.serverUrl ?: return
        val prefs = requireContext().getSharedPreferences("plex_settings", 0)
        val preferDirectPlay = prefs.getBoolean("direct_play", true)

        if (preferDirectPlay) {
            val directUrl = plexClient.getDirectPlayUrl(serverUrl, item)
            if (directUrl != null) {
                playUrl(directUrl, isTranscode = false)
                return
            }
        }

        val transcodeUrl = plexClient.getTranscodeUrl(serverUrl, item)
        playUrl(transcodeUrl, isTranscode = true)
    }

    private fun playUrl(url: String, isTranscode: Boolean) {
        val currentPlayer = player ?: return
        val mediaItem = ExoMediaItem.fromUri(url)

        currentPlayer.setMediaItem(mediaItem)
        currentPlayer.prepare()

        val offset = item.viewOffset
        if (resumePlayback && offset != null && offset > 0 && !isTranscode) {
            currentPlayer.seekTo(offset)
        }

        currentPlayer.playWhenReady = true
    }

    // -- Track selection dialogs --

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun showSubtitlePicker() {
        val currentPlayer = player ?: return
        val tracks = currentPlayer.currentTracks

        val subtitleTracks = mutableListOf<Pair<String, Tracks.Group?>>()
        subtitleTracks.add("Off" to null)

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Subtitle ${subtitleTracks.size}"
                    subtitleTracks.add(label to group)
                }
            }
        }

        if (subtitleTracks.size <= 1) {
            Toast.makeText(requireContext(), "No subtitles available", Toast.LENGTH_SHORT).show()
            return
        }

        val names = subtitleTracks.map { it.first }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Subtitles")
            .setItems(names) { _, which ->
                if (which == 0) {
                    // Disable subtitles
                    currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    val group = subtitleTracks[which].second ?: return@setItems
                    val trackIdx = which - 1 // Account for "Off" entry
                    // Find the correct track index within this group
                    var globalIdx = 0
                    for (g in tracks.groups) {
                        if (g.type == C.TRACK_TYPE_TEXT) {
                            for (i in 0 until g.length) {
                                if (globalIdx == trackIdx) {
                                    currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                                        .build()
                                    return@setItems
                                }
                                globalIdx++
                            }
                        }
                    }
                }
            }
            .show()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun showAudioTrackPicker() {
        val currentPlayer = player ?: return
        val tracks = currentPlayer.currentTracks

        val audioTracks = mutableListOf<Triple<String, Tracks.Group, Int>>()

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = buildString {
                        append(format.label ?: format.language?.uppercase() ?: "Track ${audioTracks.size + 1}")
                        format.sampleMimeType?.let { mime ->
                            val codec = when {
                                mime.contains("ac3") -> "AC3"
                                mime.contains("eac3") || mime.contains("e-ac3") -> "EAC3"
                                mime.contains("dts") -> "DTS"
                                mime.contains("truehd") -> "TrueHD"
                                mime.contains("aac") -> "AAC"
                                mime.contains("opus") -> "Opus"
                                mime.contains("flac") -> "FLAC"
                                else -> mime.substringAfterLast("/")
                            }
                            append(" ($codec)")
                        }
                        if (format.channelCount > 0) {
                            val ch = when (format.channelCount) {
                                1 -> "Mono"
                                2 -> "Stereo"
                                6 -> "5.1"
                                8 -> "7.1"
                                else -> "${format.channelCount}ch"
                            }
                            append(" $ch")
                        }
                    }
                    audioTracks.add(Triple(label, group, i))
                }
            }
        }

        if (audioTracks.isEmpty()) {
            Toast.makeText(requireContext(), "No audio tracks found", Toast.LENGTH_SHORT).show()
            return
        }

        val names = audioTracks.map { it.first }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Audio Track")
            .setItems(names) { _, which ->
                val (_, group, trackIdx) = audioTracks[which]
                currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
                    .build()
                Toast.makeText(requireContext(), names[which], Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // -- Next episode --

    private fun playNextEpisode() {
        if (item.type != "episode") {
            requireActivity().finish()
            return
        }

        val serverUrl = tokenStore.serverUrl ?: run { requireActivity().finish(); return }
        val seasonKey = item.parentRatingKey ?: run { requireActivity().finish(); return }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val episodes = withContext(Dispatchers.IO) {
                    plexClient.getChildren(serverUrl, seasonKey)
                }

                val currentIndex = episodes.indexOfFirst { it.ratingKey == item.ratingKey }
                val nextEpisode = if (currentIndex >= 0 && currentIndex < episodes.size - 1) {
                    episodes[currentIndex + 1]
                } else {
                    val showKey = item.grandparentRatingKey
                    if (showKey != null) {
                        val seasons = withContext(Dispatchers.IO) { plexClient.getChildren(serverUrl, showKey) }
                        val currentSeasonIndex = seasons.indexOfFirst { it.ratingKey == seasonKey }
                        if (currentSeasonIndex >= 0 && currentSeasonIndex < seasons.size - 1) {
                            val nextSeason = seasons[currentSeasonIndex + 1]
                            val nextSeasonEps = withContext(Dispatchers.IO) { plexClient.getChildren(serverUrl, nextSeason.ratingKey) }
                            nextSeasonEps.firstOrNull()
                        } else null
                    } else null
                }

                if (nextEpisode != null) {
                    val fullNext = withContext(Dispatchers.IO) {
                        plexClient.getMetadata(serverUrl, nextEpisode.ratingKey)
                    } ?: nextEpisode

                    item = fullNext
                    resumePlayback = false

                    glue?.title = "${fullNext.grandparentTitle ?: ""} - ${fullNext.displayTitle}"
                    glue?.subtitle = fullNext.displaySubtitle ?: ""

                    Toast.makeText(requireContext(), "Up next: ${fullNext.displayTitle}", Toast.LENGTH_SHORT).show()
                    startPlayback()
                } else {
                    requireActivity().finish()
                }
            } catch (_: Exception) {
                requireActivity().finish()
            }
        }
    }

    // -- Playback error handling --

    private fun handlePlaybackError(error: PlaybackException) {
        val serverUrl = tokenStore.serverUrl ?: return
        val directUrl = plexClient.getDirectPlayUrl(serverUrl, item)
        val currentUrl = player?.currentMediaItem?.localConfiguration?.uri?.toString()

        if (currentUrl == directUrl) {
            Toast.makeText(requireContext(), "Switching to transcode...", Toast.LENGTH_SHORT).show()
            val transcodeUrl = plexClient.getTranscodeUrl(serverUrl, item, startOffset = player?.currentPosition ?: 0)
            playUrl(transcodeUrl, isTranscode = true)
        } else {
            Toast.makeText(requireContext(), "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
            requireActivity().finish()
        }
    }

    // -- Progress reporting --

    private fun startProgressReporting() {
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS)
    }

    private fun stopProgressReporting() {
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun reportProgress(state: String) {
        val serverUrl = tokenStore.serverUrl ?: return
        val position = player?.currentPosition ?: return
        val duration = player?.duration ?: item.duration ?: 0
        CoroutineScope(Dispatchers.IO).launch {
            plexClient.reportProgress(serverUrl, item.ratingKey, position, duration, state)
        }
    }

    private fun markWatchedIfComplete() {
        val serverUrl = tokenStore.serverUrl ?: return
        val position = player?.currentPosition ?: 0
        val duration = player?.duration ?: 0
        if (duration > 0 && position > duration * 0.95) {
            CoroutineScope(Dispatchers.IO).launch {
                plexClient.markWatched(serverUrl, item.ratingKey)
            }
        }
    }

    private fun releasePlayer() {
        stopProgressReporting()
        player?.release()
        player = null
        glue = null
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val PROGRESS_INTERVAL_MS = 10_000L
    }
}

/**
 * Custom transport control glue with subtitle/audio track actions.
 */
class PlexTransportControlGlue(
    context: android.app.Activity,
    playerAdapter: LeanbackPlayerAdapter,
    private val onSubtitles: () -> Unit,
    private val onAudioTrack: () -> Unit
) : PlaybackTransportControlGlue<LeanbackPlayerAdapter>(context, playerAdapter) {

    private val rewindAction = PlaybackControlsRow.RewindAction(context)
    private val ffAction = PlaybackControlsRow.FastForwardAction(context)
    private val subtitleAction = Action(
        ACTION_SUBTITLES, "Subtitles",
        null,
        androidx.core.content.ContextCompat.getDrawable(context, com.plexclient.R.drawable.ic_subtitles)
    )
    private val audioAction = Action(
        ACTION_AUDIO, "Audio",
        null,
        androidx.core.content.ContextCompat.getDrawable(context, com.plexclient.R.drawable.ic_audio)
    )

    override fun onCreatePrimaryActions(primaryActionsAdapter: ArrayObjectAdapter) {
        primaryActionsAdapter.add(rewindAction)
        super.onCreatePrimaryActions(primaryActionsAdapter)
        primaryActionsAdapter.add(ffAction)
    }

    override fun onCreateSecondaryActions(secondaryActionsAdapter: ArrayObjectAdapter) {
        super.onCreateSecondaryActions(secondaryActionsAdapter)
        secondaryActionsAdapter.add(subtitleAction)
        secondaryActionsAdapter.add(audioAction)
    }

    override fun onActionClicked(action: Action) {
        when (action) {
            rewindAction -> {
                playerAdapter.currentPosition.let {
                    playerAdapter.seekTo(maxOf(0, it - 10_000))
                }
            }
            ffAction -> {
                playerAdapter.currentPosition.let {
                    playerAdapter.seekTo(it + 30_000)
                }
            }
            subtitleAction -> onSubtitles()
            audioAction -> onAudioTrack()
            else -> super.onActionClicked(action)
        }
    }

    companion object {
        private const val ACTION_SUBTITLES = 1001L
        private const val ACTION_AUDIO = 1002L
    }
}

/**
 * Seek provider that uses ExoPlayer's duration for the seek bar.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoSeekProvider(private val player: ExoPlayer) :
    androidx.leanback.widget.PlaybackSeekDataProvider() {

    override fun getSeekPositions(): LongArray {
        val duration = player.duration
        if (duration <= 0) return LongArray(0)

        // Generate seek positions every 10 seconds
        val interval = 10_000L
        val count = (duration / interval).toInt() + 1
        return LongArray(count) { it * interval }
    }
}
