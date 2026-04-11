package com.plexclient.ui.main

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    private var sortedItems: List<MediaItem> = emptyList()
    private var letterIndex: Map<Char, Int> = emptyMap() // letter -> position in grid
    private var alphabetStrip: LinearLayout? = null
    private var alphabetVisible = false
    private var columns = 7

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

        // Add alphabet strip overlay
        addAlphabetStrip(view)

        loadLibrary()
    }

    private fun setupGrid() {
        val serverUrl = tokenStore.serverUrl ?: return

        columns = when (libraryType) {
            "movie", "show" -> 7
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

                sortedItems = items.sortedBy { sortTitle(it.title) }

                // Build letter -> position index
                val index = mutableMapOf<Char, Int>()
                for ((i, item) in sortedItems.withIndex()) {
                    val letter = sortTitle(item.title).firstOrNull()?.uppercaseChar() ?: '#'
                    val key = if (letter in 'A'..'Z') letter else '#'
                    if (key !in index) {
                        index[key] = i
                    }
                }
                letterIndex = index

                gridAdapter.clear()
                sortedItems.forEach { gridAdapter.add(it) }

                fragmentAdapter.fragmentHost?.notifyDataReady(fragmentAdapter)

                // Update the alphabet strip with available letters
                updateAlphabetStrip()
            } catch (_: Exception) {}
        }
    }

    private fun addAlphabetStrip(rootView: View) {
        // Find the root FrameLayout and overlay the alphabet strip on the right
        val parent = rootView as? ViewGroup ?: return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (32 * density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (8 * density).toInt()
                marginEnd = (4 * density).toInt()
            }
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
        }

        alphabetStrip = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }

        scrollView.addView(alphabetStrip)

        // Wrap the existing content in a FrameLayout if needed
        if (parent is FrameLayout) {
            parent.addView(scrollView)
        } else {
            // Wrap
            val wrapper = FrameLayout(context).apply {
                layoutParams = parent.layoutParams
            }
            val index = (parent.parent as? ViewGroup)?.indexOfChild(parent) ?: -1
            val grandParent = parent.parent as? ViewGroup
            grandParent?.removeView(parent)
            wrapper.addView(parent)
            wrapper.addView(scrollView)
            grandParent?.addView(wrapper, index)
        }

        // Store ref to the scroll view for show/hide
        scrollView.tag = "alphabet_scroll"
    }

    private fun updateAlphabetStrip() {
        val strip = alphabetStrip ?: return
        val context = context ?: return
        val density = context.resources.displayMetrics.density

        strip.removeAllViews()

        val letters = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for (ch in letters) {
            val hasContent = ch in letterIndex
            val tv = TextView(context).apply {
                text = ch.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    (28 * density).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                if (hasContent) {
                    setTextColor(0xFFE5A00D.toInt())
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setBackgroundResource(com.plexclient.R.drawable.top_bar_icon_bg)

                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) {
                            setTextColor(0xFFFFFFFF.toInt())
                            scaleX = 1.3f
                            scaleY = 1.3f
                        } else {
                            setTextColor(0xFFE5A00D.toInt())
                            scaleX = 1.0f
                            scaleY = 1.0f
                        }
                    }

                    setOnClickListener { jumpToLetter(ch) }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN &&
                            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                            jumpToLetter(ch)
                            true
                        } else false
                    }
                } else {
                    setTextColor(0xFF444444.toInt())
                }
            }
            strip.addView(tv)
        }

        // Show the strip
        val scrollView = strip.parent as? View
        scrollView?.visibility = View.VISIBLE
    }

    private fun jumpToLetter(letter: Char) {
        val position = letterIndex[letter] ?: return
        // Select the item at that position in the grid
        setSelectedPosition(position)
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
