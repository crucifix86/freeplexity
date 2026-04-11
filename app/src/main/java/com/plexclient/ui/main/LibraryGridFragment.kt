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
    private var letterIndex: Map<Char, Int> = emptyMap()
    private var columns = 7

    private var alphabetStrip: LinearLayout? = null
    private var alphabetScroll: ScrollView? = null
    private var stripShowing = false
    private var lastGridPosition = 0
    private var currentGridPosition = 0

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

        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is MediaItem) {
                val idx = sortedItems.indexOf(item)
                if (idx >= 0) currentGridPosition = idx
            }
        }

        loadLibrary()
    }

    private fun setupGrid() {
        columns = when (libraryType) {
            "movie", "show" -> 7
            else -> 5
        }

        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        gridPresenter.numberOfColumns = columns
        setGridPresenter(gridPresenter)

        val serverUrl = tokenStore.serverUrl ?: return
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

                val index = mutableMapOf<Char, Int>()
                for ((i, item) in sortedItems.withIndex()) {
                    val letter = sortTitle(item.title).firstOrNull()?.uppercaseChar() ?: '#'
                    val key = if (letter in 'A'..'Z') letter else '#'
                    if (key !in index) index[key] = i
                }
                letterIndex = index

                gridAdapter.clear()
                sortedItems.forEach { gridAdapter.add(it) }
                fragmentAdapter.fragmentHost?.notifyDataReady(fragmentAdapter)

                // Now that grid is loaded, attach key listener for alphabet trigger
                view?.let { attachKeyInterceptor(it) }
            } catch (_: Exception) {}
        }
    }

    private fun attachKeyInterceptor(rootView: View) {
        // Intercept key events on the root view to catch RIGHT at grid edge
        rootView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            if (stripShowing) {
                // Strip is active — handle navigation within it
                return@setOnKeyListener handleStripKey(keyCode)
            }

            // RIGHT press — check if we should show the alphabet
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // If on the rightmost column, show alphabet
                if (currentGridPosition >= 0 && (currentGridPosition + 1) % columns == 0) {
                    showAlphabetStrip(rootView)
                    return@setOnKeyListener true
                }
            }

            // Also allow a dedicated button (e.g., menu) to open the strip
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                showAlphabetStrip(rootView)
                return@setOnKeyListener true
            }

            false
        }
    }

    private fun showAlphabetStrip(rootView: View) {
        if (stripShowing) return
        stripShowing = true
        lastGridPosition = currentGridPosition

        val context = context ?: return
        val density = context.resources.displayMetrics.density

        // Remove old strip if any
        hideAlphabetStrip()

        val parent = rootView as? ViewGroup ?: rootView.parent as? ViewGroup ?: return

        alphabetScroll = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (40 * density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            ).apply {
                topMargin = (4 * density).toInt()
                bottomMargin = (4 * density).toInt()
            }
            setBackgroundColor(0xDD1A1A2E.toInt())
            isVerticalScrollBarEnabled = false
            elevation = 16 * density
        }

        alphabetStrip = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        val letters = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var firstFocusable: View? = null

        for (ch in letters) {
            val hasContent = ch in letterIndex
            val tv = TextView(context).apply {
                text = ch.toString()
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    (36 * density).toInt(),
                    (22 * density).toInt()
                )

                if (hasContent) {
                    setTextColor(0xFFE5A00D.toInt())
                    isFocusable = true
                    isFocusableInTouchMode = true

                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            (v as TextView).setTextColor(0xFFFFFFFF.toInt())
                            v.setBackgroundColor(0x44E5A00D.toInt())
                        } else {
                            (v as TextView).setTextColor(0xFFE5A00D.toInt())
                            v.setBackgroundColor(0x00000000)
                        }
                    }
                } else {
                    setTextColor(0xFF444444.toInt())
                }
            }
            alphabetStrip!!.addView(tv)
            if (hasContent && firstFocusable == null) firstFocusable = tv
        }

        alphabetScroll!!.addView(alphabetStrip)

        // Find or create a FrameLayout wrapper
        if (parent is FrameLayout) {
            parent.addView(alphabetScroll)
        } else {
            // Add directly to the root
            (rootView.rootView as? ViewGroup)?.addView(alphabetScroll)
        }

        // Focus the first available letter
        firstFocusable?.requestFocus()
    }

    private fun handleStripKey(keyCode: Int): Boolean {
        val strip = alphabetStrip ?: return false

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> {
                // Exit strip, return to grid
                hideAlphabetStrip()
                stripShowing = false
                setSelectedPosition(lastGridPosition)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Find which letter is focused
                for (i in 0 until strip.childCount) {
                    val child = strip.getChildAt(i) as? TextView ?: continue
                    if (child.isFocused) {
                        val letter = child.text.firstOrNull() ?: continue
                        val position = letterIndex[letter]
                        if (position != null) {
                            hideAlphabetStrip()
                            stripShowing = false
                            setSelectedPosition(position)
                        }
                        return true
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Let default focus navigation handle up/down within the strip
                return false
            }
        }
        return false
    }

    private fun hideAlphabetStrip() {
        alphabetScroll?.let { scroll ->
            (scroll.parent as? ViewGroup)?.removeView(scroll)
        }
        alphabetScroll = null
        alphabetStrip = null
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
