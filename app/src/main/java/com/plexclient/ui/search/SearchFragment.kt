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
import com.plexclient.ui.presenters.CardPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is MediaItem) {
                val intent = Intent(requireActivity(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.EXTRA_ITEM, item)
                }
                startActivity(intent)
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String?): Boolean {
        // Debounce search input
        searchHandler.removeCallbacksAndMessages(null)
        pendingQuery = newQuery
        searchHandler.postDelayed({
            if (pendingQuery == newQuery && !newQuery.isNullOrBlank()) {
                performSearch(newQuery)
            }
        }, 400)
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

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    plexClient.search(serverUrl, query)
                }

                rowsAdapter.clear()

                // Group results by type
                val grouped = results.groupBy { it.type }
                var rowIndex = 0L

                for ((type, items) in grouped) {
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
            } catch (e: Exception) {
                // Search failed, clear results
                rowsAdapter.clear()
            }
        }
    }
}
