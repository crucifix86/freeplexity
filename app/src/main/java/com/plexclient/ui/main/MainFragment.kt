package com.plexclient.ui.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : BrowseSupportFragment() {

    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore
    private val pageAdapter = ArrayObjectAdapter(ListRowPresenter())

    private val libraryMap = mutableMapOf<Long, Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = ""
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = 0xFF1F1F1F.toInt()
        searchAffordanceColor = 0x00000000.toInt()

        adapter = pageAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Kill the entire title/search orb area and reclaim the vertical space
        killTitleArea(view)

        mainFragmentRegistry.registerFragment(
            PageRow::class.java,
            PageFragmentFactory()
        )

        loadNavigation()
    }

    private fun killTitleArea(root: View) {
        // Hide the title group (search orb + title text)
        try {
            val titleGroup = root.findViewById<View>(androidx.leanback.R.id.browse_title_group)
            titleGroup?.visibility = View.GONE
            (titleGroup?.layoutParams as? ViewGroup.MarginLayoutParams)?.height = 0
        } catch (_: Exception) {}

        // Remove top margin/padding from the browse container dock
        try {
            val dock = root.findViewById<View>(androidx.leanback.R.id.browse_container_dock)
            if (dock != null) {
                (dock.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0
                dock.setPadding(dock.paddingLeft, 0, dock.paddingRight, dock.paddingBottom)
            }
        } catch (_: Exception) {}

        // Also kill padding on the headers dock
        try {
            val headersDock = root.findViewById<View>(androidx.leanback.R.id.browse_headers_dock)
            if (headersDock != null) {
                (headersDock.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0
                headersDock.setPadding(headersDock.paddingLeft, 0, headersDock.paddingRight, headersDock.paddingBottom)
            }
        } catch (_: Exception) {}

        // Kill padding on the outer browse frame
        try {
            val frame = root.findViewById<View>(androidx.leanback.R.id.browse_frame)
            if (frame != null) {
                frame.setPadding(frame.paddingLeft, 0, frame.paddingRight, frame.paddingBottom)
            }
        } catch (_: Exception) {}

        // Also try the root view itself
        root.setPadding(root.paddingLeft, 0, root.paddingRight, root.paddingBottom)
    }

    private fun loadNavigation() {
        val serverUrl = tokenStore.serverUrl ?: return

        lifecycleScope.launch {
            try {
                val libraries = withContext(Dispatchers.IO) {
                    plexClient.getLibraries(serverUrl)
                }

                pageAdapter.clear()
                libraryMap.clear()

                pageAdapter.add(PageRow(HeaderItem(HOME_ID, "Home")))

                val userStore = PlexApp.instance.userStore
                var libId = 100L
                for (lib in libraries) {
                    if (!userStore.canAccessLibrary(lib.key)) continue
                    libraryMap[libId] = Pair(lib.key, lib.type)
                    pageAdapter.add(PageRow(HeaderItem(libId, lib.title)))
                    libId++
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    inner class PageFragmentFactory : BrowseSupportFragment.FragmentFactory<Fragment>() {
        override fun createFragment(row: Any): Fragment {
            val pageRow = row as PageRow
            val id = pageRow.headerItem.id

            return if (id == HOME_ID) {
                HomeRowsFragment()
            } else {
                val (libKey, libType) = libraryMap[id] ?: return HomeRowsFragment()
                LibraryGridFragment.newInstance(libKey, pageRow.headerItem.name, libType)
            }
        }
    }

    companion object {
        const val HOME_ID = 0L
    }
}
