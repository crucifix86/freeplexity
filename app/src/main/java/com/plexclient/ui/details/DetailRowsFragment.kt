package com.plexclient.ui.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.api.models.MediaItem
import com.plexclient.ui.playback.PlaybackActivity
import com.plexclient.ui.presenters.CardPresenter
import com.plexclient.ui.presenters.CastPresenter
import com.plexclient.ui.presenters.WideCardPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailRowsFragment : RowsSupportFragment() {

    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter().apply {
        shadowEnabled = false
        selectEffectEnabled = false
    })

    private var currentSeasonKey: String? = null
    private var episodeLoadJob: Job? = null
    private var currentItem: MediaItem? = null

    // Track row positions so we can swap episodes without losing cast/crew
    private var seasonsRowIndex = -1
    private var episodesRowIndex = -1
    private var castRowIndex = -1
    private var crewRowIndex = -1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rowsAdapter

        setOnItemViewClickedListener { _, clickedItem, _, _ ->
            if (clickedItem is MediaItem) {
                if (clickedItem.isPlayable) {
                    startActivity(Intent(requireActivity(), PlaybackActivity::class.java).apply {
                        putExtra(PlaybackActivity.EXTRA_ITEM, clickedItem)
                        putExtra(PlaybackActivity.EXTRA_RESUME, clickedItem.hasProgress)
                    })
                } else {
                    startActivity(Intent(requireActivity(), DetailsActivity::class.java).apply {
                        putExtra(DetailsActivity.EXTRA_ITEM, clickedItem)
                    })
                }
            }
        }

        setOnItemViewSelectedListener { _, item, _, row ->
            if (item is MediaItem && item.type == "season") {
                val headerName = (row as? ListRow)?.headerItem?.name ?: ""
                if (headerName == "Seasons" && item.ratingKey != currentSeasonKey) {
                    currentSeasonKey = item.ratingKey
                    loadEpisodesForSeason(item)
                }
            }
        }
    }

    fun loadContent(item: MediaItem) {
        currentItem = item
        val serverUrl = tokenStore.serverUrl ?: return

        when (item.type) {
            "show" -> loadShowContent(serverUrl, item)
            "season" -> loadSeasonContent(serverUrl, item)
            "movie", "episode" -> loadMediaContent(item)
        }
    }

    // -- Show: seasons row + episodes + cast/crew --

    private fun loadShowContent(serverUrl: String, show: MediaItem) {
        lifecycleScope.launch {
            try {
                val seasons = withContext(Dispatchers.IO) {
                    plexClient.getChildren(serverUrl, show.ratingKey)
                }

                rowsAdapter.clear()

                if (seasons.isNotEmpty()) {
                    // Seasons row
                    val seasonPresenter = CardPresenter(serverUrl, plexClient)
                    val seasonAdapter = ArrayObjectAdapter(seasonPresenter)
                    seasons.forEach { seasonAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(0L, "Seasons"), seasonAdapter))

                    // First season episodes
                    val firstSeason = seasons.first()
                    currentSeasonKey = firstSeason.ratingKey
                    val episodes = withContext(Dispatchers.IO) {
                        plexClient.getChildren(serverUrl, firstSeason.ratingKey)
                    }
                    if (episodes.isNotEmpty()) {
                        val epPresenter = WideCardPresenter(serverUrl, plexClient)
                        val epAdapter = ArrayObjectAdapter(epPresenter)
                        episodes.forEach { epAdapter.add(it) }
                        rowsAdapter.add(ListRow(HeaderItem(1L, firstSeason.title), epAdapter))
                    }
                }

                // Cast & crew from the show metadata
                addCastCrewRows(show, serverUrl)

            } catch (_: Exception) {}
        }
    }

    private fun loadEpisodesForSeason(season: MediaItem) {
        val serverUrl = tokenStore.serverUrl ?: return

        episodeLoadJob?.cancel()
        episodeLoadJob = lifecycleScope.launch {
            try {
                val episodes = withContext(Dispatchers.IO) {
                    plexClient.getChildren(serverUrl, season.ratingKey)
                }

                // Remove old episodes row (index 1) and replace it
                if (rowsAdapter.size() > 1) {
                    // Find and remove the episodes row (it's always right after seasons)
                    val oldRow = rowsAdapter.get(1)
                    if (oldRow is ListRow && oldRow.headerItem.id == 1L) {
                        rowsAdapter.removeItems(1, 1)
                    }
                }

                if (episodes.isNotEmpty()) {
                    val epPresenter = WideCardPresenter(serverUrl, plexClient)
                    val epAdapter = ArrayObjectAdapter(epPresenter)
                    episodes.forEach { epAdapter.add(it) }
                    // Insert at position 1 (after seasons)
                    rowsAdapter.add(1, ListRow(HeaderItem(1L, season.title), epAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    // -- Season: just episodes + cast/crew --

    private fun loadSeasonContent(serverUrl: String, season: MediaItem) {
        lifecycleScope.launch {
            try {
                val episodes = withContext(Dispatchers.IO) {
                    plexClient.getChildren(serverUrl, season.ratingKey)
                }

                rowsAdapter.clear()

                if (episodes.isNotEmpty()) {
                    val epPresenter = WideCardPresenter(serverUrl, plexClient)
                    val epAdapter = ArrayObjectAdapter(epPresenter)
                    episodes.forEach { epAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(0L, "Episodes"), epAdapter))
                }

                // Try to get cast from the parent show
                val showKey = season.parentRatingKey
                if (showKey != null) {
                    val show = withContext(Dispatchers.IO) {
                        plexClient.getMetadata(serverUrl, showKey)
                    }
                    if (show != null) {
                        addCastCrewRows(show, serverUrl)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // -- Movie/Episode: cast & crew only --

    private fun loadMediaContent(item: MediaItem) {
        val serverUrl = tokenStore.serverUrl ?: return
        rowsAdapter.clear()
        addCastCrewRows(item, serverUrl)
    }

    // -- Cast & Crew rows --

    private fun addCastCrewRows(item: MediaItem, serverUrl: String) {
        // Cast row
        if (item.roles.isNotEmpty()) {
            val castPresenter = CastPresenter(serverUrl, plexClient)
            val castAdapter = ArrayObjectAdapter(castPresenter)
            item.roles.forEach { castAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(10L, "Cast"), castAdapter))
        }

        // Directors + Writers combined as "Crew"
        val crew = mutableListOf<Any>()
        item.directors.forEach { crew.add(it) }
        item.writers.forEach { crew.add(it) }

        if (crew.isNotEmpty()) {
            val crewPresenter = CastPresenter(serverUrl, plexClient)
            val crewAdapter = ArrayObjectAdapter(crewPresenter)
            crew.forEach { crewAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(11L, "Crew"), crewAdapter))
        }
    }
}
