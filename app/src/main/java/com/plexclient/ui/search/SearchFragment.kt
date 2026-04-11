package com.plexclient.ui.search

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.api.models.MediaItem
import com.plexclient.ui.details.DetailsActivity
import com.plexclient.ui.playback.PlaybackActivity
import com.plexclient.ui.presenters.CardPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is MediaItem) {
                startActivity(Intent(requireActivity(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.EXTRA_ITEM, item)
                })
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String?): Boolean {
        searchHandler.removeCallbacksAndMessages(null)
        if (newQuery.isNullOrBlank()) {
            rowsAdapter.clear()
            return true
        }
        // Short debounce for live updating
        searchHandler.postDelayed({
            performSearch(newQuery)
        }, 250)
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        searchHandler.removeCallbacksAndMessages(null)
        if (!query.isNullOrBlank()) {
            performSearch(query)
        }
        return true
    }

    private fun performSearch(query: String) {
        val serverUrl = tokenStore.serverUrl ?: return

        // Cancel previous search if still running
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    plexClient.search(serverUrl, query)
                }

                rowsAdapter.clear()

                if (results.isEmpty()) {
                    return@launch
                }

                // Group by type, show in a logical order
                val typeOrder = listOf("movie", "show", "episode", "season")
                val grouped = results.groupBy { it.type }

                var rowIndex = 0L
                for (type in typeOrder) {
                    val items = grouped[type] ?: continue
                    val headerTitle = when (type) {
                        "movie" -> "Movies"
                        "show" -> "TV Shows"
                        "episode" -> "Episodes"
                        "season" -> "Seasons"
                        else -> type.replaceFirstChar { it.uppercase() }
                    }

                    val cardPresenter = CardPresenter(serverUrl, plexClient)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    items.forEach { listRowAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(rowIndex++, headerTitle), listRowAdapter))
                }

                // Any remaining types not in our order
                for ((type, items) in grouped) {
                    if (type in typeOrder) continue
                    val cardPresenter = CardPresenter(serverUrl, plexClient)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    items.forEach { listRowAdapter.add(it) }
                    rowsAdapter.add(ListRow(
                        HeaderItem(rowIndex++, type.replaceFirstChar { it.uppercase() }),
                        listRowAdapter
                    ))
                }
            } catch (_: Exception) {
                rowsAdapter.clear()
            }
        }
    }
}
