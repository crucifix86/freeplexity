package com.plexclient.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.plexclient.PlexApp
import com.plexclient.api.models.MediaItem
import com.plexclient.ui.playback.PlaybackActivity
import com.plexclient.ui.presenters.CardPresenter
import com.plexclient.ui.presenters.DetailsDescriptionPresenter
import com.plexclient.ui.presenters.WideCardPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailsFragment : DetailsSupportFragment() {

    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var item: MediaItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        item = requireActivity().intent.getSerializableExtra(DetailsActivity.EXTRA_ITEM) as? MediaItem
            ?: run { requireActivity().finish(); return }

        setupAdapter()
        loadDetails()
    }

    private fun setupAdapter() {
        val presenterSelector = ClassPresenterSelector()

        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter()).apply {
            backgroundColor = 0xFF1F1F1F.toInt()
            actionsBackgroundColor = 0xFF181818.toInt()

            // Start in half-height state so content below is visible immediately
            initialState = FullWidthDetailsOverviewRowPresenter.STATE_HALF

            setOnActionClickedListener { action ->
                when (action.id) {
                    ACTION_PLAY -> playItem(item)
                    ACTION_RESUME -> playItem(item, resume = true)
                    ACTION_MARK_WATCHED -> markWatched()
                    ACTION_MARK_UNWATCHED -> markUnwatched()
                }
            }
        }

        presenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter().apply {
            shadowEnabled = false
        })

        rowsAdapter = ArrayObjectAdapter(presenterSelector)
        adapter = rowsAdapter

        setOnItemViewClickedListener { _, clickedItem, _, _ ->
            if (clickedItem is MediaItem) {
                // Episodes: play directly
                if (clickedItem.isPlayable) {
                    startActivity(Intent(requireActivity(), PlaybackActivity::class.java).apply {
                        putExtra(PlaybackActivity.EXTRA_ITEM, clickedItem)
                        putExtra(PlaybackActivity.EXTRA_RESUME, clickedItem.hasProgress)
                    })
                } else {
                    // Seasons: drill in
                    startActivity(Intent(requireActivity(), DetailsActivity::class.java).apply {
                        putExtra(DetailsActivity.EXTRA_ITEM, clickedItem)
                    })
                }
            }
        }
    }

    private fun loadDetails() {
        val serverUrl = tokenStore.serverUrl ?: return

        lifecycleScope.launch {
            try {
                val fullItem = withContext(Dispatchers.IO) {
                    plexClient.getMetadata(serverUrl, item.ratingKey)
                } ?: item

                item = fullItem
                buildDetailsRow(fullItem)

                when (fullItem.type) {
                    "show" -> loadChildren(serverUrl, fullItem, "Seasons")
                    "season" -> loadChildren(serverUrl, fullItem, "Episodes")
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildDetailsRow(item: MediaItem) {
        val row = DetailsOverviewRow(item)

        // Actions — keep it minimal
        val actions = SparseArrayObjectAdapter()
        if (item.isPlayable) {
            if (item.hasProgress) {
                val offset = item.viewOffset ?: 0
                actions.set(ACTION_RESUME.toInt(), Action(ACTION_RESUME, "Resume", formatTime(offset)))
                actions.set(ACTION_PLAY.toInt(), Action(ACTION_PLAY, "Play from Start"))
            } else {
                actions.set(ACTION_PLAY.toInt(), Action(ACTION_PLAY, "Play"))
            }
            if (item.viewCount != null && item.viewCount > 0) {
                actions.set(ACTION_MARK_UNWATCHED.toInt(), Action(ACTION_MARK_UNWATCHED, "Mark Unwatched"))
            } else {
                actions.set(ACTION_MARK_WATCHED.toInt(), Action(ACTION_MARK_WATCHED, "Mark Watched"))
            }
        }
        row.actionsAdapter = actions

        // Smaller poster to keep the row compact
        val serverUrl = tokenStore.serverUrl ?: return
        val posterUrl = plexClient.getImageUrl(serverUrl, item.bestThumb, 200, 300)
        if (posterUrl != null) {
            Glide.with(requireContext())
                .asBitmap()
                .load(posterUrl)
                .apply(RequestOptions().override(200, 300))
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        row.setImageBitmap(requireContext(), resource)
                        rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                    }
                })
        }

        rowsAdapter.add(row)
    }

    private fun loadChildren(serverUrl: String, parent: MediaItem, label: String) {
        lifecycleScope.launch {
            try {
                val children = withContext(Dispatchers.IO) {
                    plexClient.getChildren(serverUrl, parent.ratingKey)
                }
                if (children.isEmpty()) return@launch

                val isEpisodes = children.firstOrNull()?.type == "episode"

                if (isEpisodes) {
                    // Episodes: single row of wide cards
                    val presenter = WideCardPresenter(serverUrl, plexClient)
                    val listAdapter = ArrayObjectAdapter(presenter)
                    children.forEach { listAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(1L, label), listAdapter))
                } else {
                    // Seasons: one row per season with its episodes
                    // First add all seasons as a compact row
                    val seasonPresenter = CardPresenter(serverUrl, plexClient)
                    val seasonAdapter = ArrayObjectAdapter(seasonPresenter)
                    children.forEach { seasonAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(1L, label), seasonAdapter))

                    // Then load episodes for the first season automatically
                    val firstSeason = children.firstOrNull()
                    if (firstSeason != null) {
                        val episodes = withContext(Dispatchers.IO) {
                            plexClient.getChildren(serverUrl, firstSeason.ratingKey)
                        }
                        if (episodes.isNotEmpty()) {
                            val epPresenter = WideCardPresenter(serverUrl, plexClient)
                            val epAdapter = ArrayObjectAdapter(epPresenter)
                            episodes.forEach { epAdapter.add(it) }
                            rowsAdapter.add(ListRow(
                                HeaderItem(2L, firstSeason.title),
                                epAdapter
                            ))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun playItem(item: MediaItem, resume: Boolean = false) {
        startActivity(Intent(requireActivity(), PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_ITEM, item)
            putExtra(PlaybackActivity.EXTRA_RESUME, resume)
        })
    }

    private fun markWatched() {
        val serverUrl = tokenStore.serverUrl ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { plexClient.markWatched(serverUrl, item.ratingKey) }
            Toast.makeText(requireContext(), "Marked as watched", Toast.LENGTH_SHORT).show()
        }
    }

    private fun markUnwatched() {
        val serverUrl = tokenStore.serverUrl ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { plexClient.markUnwatched(serverUrl, item.ratingKey) }
            Toast.makeText(requireContext(), "Marked as unwatched", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_RESUME = 2L
        private const val ACTION_MARK_WATCHED = 3L
        private const val ACTION_MARK_UNWATCHED = 4L
    }
}
