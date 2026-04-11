package com.plexclient.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.api.models.MediaItem
import com.plexclient.ui.details.DetailsActivity
import com.plexclient.ui.presenters.CardPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryGridFragment : VerticalGridSupportFragment(),
    BrowseSupportFragment.MainFragmentAdapterProvider {

    private val fragmentAdapter = BrowseSupportFragment.MainFragmentAdapter(this)
    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore

    private lateinit var libraryKey: String
    private lateinit var libraryTitle: String
    private lateinit var libraryType: String
    private var gridAdapter: ArrayObjectAdapter? = null

    override fun getMainFragmentAdapter() = fragmentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        libraryKey = arguments?.getString(ARG_KEY) ?: return
        libraryTitle = arguments?.getString(ARG_TITLE) ?: ""
        libraryType = arguments?.getString(ARG_TYPE) ?: "movie"

        setupGrid()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is MediaItem) {
                startActivity(Intent(requireActivity(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.EXTRA_ITEM, item)
                })
            }
        }

        loadLibrary()
    }

    private fun setupGrid() {
        val serverUrl = tokenStore.serverUrl ?: return

        // Number of columns based on content type
        val columns = when (libraryType) {
            "movie", "show" -> 7   // poster cards, 7 across
            else -> 5
        }

        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        gridPresenter.numberOfColumns = columns
        setGridPresenter(gridPresenter)

        val cardPresenter = CardPresenter(serverUrl, plexClient)
        gridAdapter = ArrayObjectAdapter(cardPresenter)
        adapter = gridAdapter
    }

    private fun loadLibrary() {
        val serverUrl = tokenStore.serverUrl ?: return
        val gridAdapter = gridAdapter ?: return

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    plexClient.getLibraryItems(serverUrl, libraryKey)
                }

                // Sort alphabetically, ignoring "The", "A", "An" prefixes
                val sorted = items.sortedBy { sortTitle(it.title) }

                gridAdapter.clear()
                sorted.forEach { gridAdapter.add(it) }

                fragmentAdapter.fragmentHost?.notifyDataReady(fragmentAdapter)
            } catch (_: Exception) {}
        }
    }

    private fun sortTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.startsWith("the ") -> lower.removePrefix("the ")
            lower.startsWith("a ") -> lower.removePrefix("a ")
            lower.startsWith("an ") -> lower.removePrefix("an ")
            else -> lower
        }
    }

    companion object {
        private const val ARG_KEY = "library_key"
        private const val ARG_TITLE = "library_title"
        private const val ARG_TYPE = "library_type"

        fun newInstance(key: String, title: String, type: String): LibraryGridFragment {
            return LibraryGridFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_KEY, key)
                    putString(ARG_TITLE, title)
                    putString(ARG_TYPE, type)
                }
            }
        }
    }
}
