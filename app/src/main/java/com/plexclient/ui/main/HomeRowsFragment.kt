package com.plexclient.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.api.models.Hub
import com.plexclient.api.models.MediaItem
import com.plexclient.ui.details.DetailsActivity
import com.plexclient.ui.playback.PlaybackActivity
import com.plexclient.ui.presenters.CardPresenter
import com.plexclient.ui.presenters.WideCardPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeRowsFragment : RowsSupportFragment(),
    BrowseSupportFragment.MainFragmentAdapterProvider {

    private val fragmentAdapter = BrowseSupportFragment.MainFragmentAdapter(this)
    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter().apply {
        shadowEnabled = false
        selectEffectEnabled = false
    })
    private var loadJob: Job? = null

    override fun getMainFragmentAdapter() = fragmentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rowsAdapter

        setOnItemViewSelectedListener { _, item, _, _ ->
            (activity as? MainActivity)?.setBackgroundForItem(item as? MediaItem)
        }

        setOnItemViewClickedListener { _, item, _, row ->
            if (item is MediaItem) {
                val rowHeader = (row as? ListRow)?.headerItem?.name ?: ""
                val isContinueRow = rowHeader.contains("Continue", ignoreCase = true) ||
                    rowHeader.contains("On Deck", ignoreCase = true)

                if (isContinueRow && item.isPlayable) {
                    // Continue Watching / On Deck: play immediately
                    startActivity(Intent(requireActivity(), PlaybackActivity::class.java).apply {
                        putExtra(PlaybackActivity.EXTRA_ITEM, item)
                        putExtra(PlaybackActivity.EXTRA_RESUME, item.hasProgress)
                    })
                } else {
                    startActivity(Intent(requireActivity(), DetailsActivity::class.java).apply {
                        putExtra(DetailsActivity.EXTRA_ITEM, item)
                    })
                }
            }
        }

        loadContent()
    }

    override fun onResume() {
        super.onResume()
        loadContent()
    }

    private fun loadContent() {
        val serverUrl = tokenStore.serverUrl ?: return

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val hubs = withContext(Dispatchers.IO) { plexClient.getHubs(serverUrl) }
                val libraries = withContext(Dispatchers.IO) { plexClient.getLibraries(serverUrl) }

                rowsAdapter.clear()
                var rowIndex = 0L

                // Continue Watching / On Deck — keep Plex's curated hubs (cross-library).
                // Filter items whose library the active user isn't allowed to access.
                val userStore = PlexApp.instance.userStore
                val continueHubs = hubs.filter {
                    it.hubIdentifier.contains("onDeck", ignoreCase = true) ||
                    it.hubIdentifier.contains("continueWatching", ignoreCase = true) ||
                    it.title.contains("Continue", ignoreCase = true) ||
                    it.title.contains("On Deck", ignoreCase = true)
                }
                for (hub in continueHubs) {
                    val allowedItems = hub.items.filter { item ->
                        item.librarySectionID?.let { userStore.canAccessLibrary(it) } ?: true
                    }
                    if (allowedItems.isEmpty()) continue
                    val presenter = WideCardPresenter(serverUrl, plexClient)
                    val listAdapter = ArrayObjectAdapter(presenter)
                    allowedItems.forEach { listAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(rowIndex++, hub.title), listAdapter))
                }

                // Recently Added — one row per library, in the order Plex lists them.
                for (library in libraries) {
                    if (library.type !in setOf("movie", "show", "artist")) continue
                    if (!userStore.canAccessLibrary(library.key)) continue
                    val paged = try {
                        withContext(Dispatchers.IO) {
                            plexClient.getRecentlyAddedForLibrary(serverUrl, library.key)
                        }
                    } catch (_: Exception) { continue }
                    if (paged.items.isEmpty()) continue
                    val presenter = CardPresenter(serverUrl, plexClient)
                    val listAdapter = ArrayObjectAdapter(presenter)
                    paged.items.forEach { listAdapter.add(it) }
                    rowsAdapter.add(
                        ListRow(HeaderItem(rowIndex++, "Recently Added — ${library.title}"), listAdapter)
                    )
                }

                fragmentAdapter.fragmentHost?.notifyDataReady(fragmentAdapter)
            } catch (_: Exception) {}
        }
    }
}
