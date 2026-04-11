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

    override fun getMainFragmentAdapter() = fragmentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rowsAdapter

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

        lifecycleScope.launch {
            try {
                val hubs = withContext(Dispatchers.IO) { plexClient.getHubs(serverUrl) }
                buildRows(hubs, serverUrl)
                fragmentAdapter.fragmentHost?.notifyDataReady(fragmentAdapter)
            } catch (_: Exception) {}
        }
    }

    private fun buildRows(hubs: List<Hub>, serverUrl: String) {
        rowsAdapter.clear()
        var rowIndex = 0L

        // Gather Continue Watching + On Deck into one group at the top
        val continueWatchingHubs = hubs.filter {
            it.hubIdentifier.contains("onDeck", ignoreCase = true) ||
            it.hubIdentifier.contains("continueWatching", ignoreCase = true) ||
            it.title.contains("Continue", ignoreCase = true) ||
            it.title.contains("On Deck", ignoreCase = true)
        }
        for (hub in continueWatchingHubs) {
            if (hub.items.isEmpty()) continue
            val widePresenter = WideCardPresenter(serverUrl, plexClient)
            val listAdapter = ArrayObjectAdapter(widePresenter)
            hub.items.forEach { listAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(rowIndex++, hub.title), listAdapter))
        }

        // Recently Added rows — poster cards
        val recentHubs = hubs.filter {
            it.hubIdentifier.contains("recentlyAdded", ignoreCase = true) ||
            it.title.contains("Recently Added", ignoreCase = true)
        }
        for (hub in recentHubs) {
            if (hub.items.isEmpty()) continue
            val cardPresenter = CardPresenter(serverUrl, plexClient)
            val listAdapter = ArrayObjectAdapter(cardPresenter)
            hub.items.forEach { listAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(rowIndex++, hub.title), listAdapter))
        }

        // Remaining hubs (recommendations, trending, etc.)
        val handledIds = buildSet {
            continueWatchingHubs.forEach { add(it.hubIdentifier) }
            recentHubs.forEach { add(it.hubIdentifier) }
        }
        for (hub in hubs) {
            if (hub.hubIdentifier in handledIds || hub.items.isEmpty()) continue
            val cardPresenter = CardPresenter(serverUrl, plexClient)
            val listAdapter = ArrayObjectAdapter(cardPresenter)
            hub.items.forEach { listAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(rowIndex++, hub.title), listAdapter))
        }
    }
}
