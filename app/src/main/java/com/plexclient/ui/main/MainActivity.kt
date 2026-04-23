package com.plexclient.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.plexclient.PlexApp
import com.plexclient.R
import com.plexclient.api.models.MediaItem
import com.plexclient.ui.search.SearchActivity
import com.plexclient.ui.settings.AppUpdater
import com.plexclient.ui.settings.SettingsActivity
import com.plexclient.ui.main.LibraryGridFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var lastFocusedInContent: View? = null
    private lateinit var backgroundManager: BackgroundManager
    private var pendingBgArt: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        backgroundManager = BackgroundManager.getInstance(this).apply {
            attach(window)
            // Neutral base so empty selection doesn't flash weirdly
            color = 0xFF1F1F1F.toInt()
        }

        val searchButton = findViewById<View>(R.id.search_button)
        val settingsButton = findViewById<View>(R.id.settings_button)

        searchButton.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val topBarKeyListener = View.OnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        v.performClick()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        lastFocusedInContent?.requestFocus()
                            ?: findViewById<View>(R.id.main_fragment)?.requestFocus()
                        true
                    }
                    else -> false
                }
            } else false
        }
        searchButton.setOnKeyListener(topBarKeyListener)
        settingsButton.setOnKeyListener(topBarKeyListener)

        // Start focused on browse content
        findViewById<View>(R.id.main_fragment)?.post {
            findViewById<View>(R.id.main_fragment)?.requestFocus()
        }

        // Check for updates in background
        checkForUpdate()
    }

    fun setBackgroundForItem(item: MediaItem?) {
        val art = item?.bestArt
        if (art == null) {
            if (pendingBgArt != null) {
                pendingBgArt = null
                runCatching { backgroundManager.clearDrawable() }
            }
            return
        }
        if (art == pendingBgArt) return
        pendingBgArt = art

        val serverUrl = PlexApp.instance.tokenStore.serverUrl ?: return
        val dm = resources.displayMetrics
        val url = PlexApp.instance.plexClient.getImageUrl(
            serverUrl, art, width = dm.widthPixels, height = dm.heightPixels
        ) ?: return

        Glide.with(this)
            .asBitmap()
            .load(url)
            .apply(RequestOptions().centerCrop())
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bmp: Bitmap, transition: Transition<in Bitmap>?) {
                    if (art != pendingBgArt) return
                    val base = BitmapDrawable(resources, bmp)
                    val dim = ColorDrawable(0xB0000000.toInt())
                    backgroundManager.drawable = LayerDrawable(arrayOf(base, dim))
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val update = AppUpdater(this@MainActivity).checkForUpdate()
            val badge = findViewById<View>(R.id.update_badge)
            badge?.visibility = if (update != null) View.VISIBLE else View.GONE
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Let the library grid alphabet strip handle keys when active
        if (event.action == KeyEvent.ACTION_DOWN) {
            val mainFragment = supportFragmentManager.findFragmentById(R.id.main_fragment)
            val gridFragment = mainFragment?.childFragmentManager?.fragments
                ?.filterIsInstance<LibraryGridFragment>()?.firstOrNull()
            if (gridFragment != null && gridFragment.handleKeyEvent(event.keyCode)) {
                return true
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            val focused = currentFocus ?: return super.dispatchKeyEvent(event)
            val topBar = findViewById<View>(R.id.top_bar)

            // Already in the top bar — let default handling work
            if (isDescendantOf(focused, topBar)) {
                return super.dispatchKeyEvent(event)
            }

            // Remember where we are so we can come back
            lastFocusedInContent = focused

            // Check if we're in the sidebar headers
            val headersDock = findViewByIdSafe(androidx.leanback.R.id.browse_headers_dock)
            if (headersDock != null && isDescendantOf(focused, headersDock)) {
                // Only go to search if we're on the FIRST header (Home)
                // Check if there's anywhere to go up within the header list
                val nextUp = focused.focusSearch(View.FOCUS_UP)
                if (nextUp == null || nextUp == focused || !isDescendantOf(nextUp, headersDock)) {
                    findViewById<View>(R.id.search_button)?.requestFocus()
                    return true
                }
                // Otherwise let normal sidebar navigation handle it
                return super.dispatchKeyEvent(event)
            }

            // In the content area — just let default handling work.
            // Only jump to search if focus truly has nowhere to go
            // (i.e., focusSearch returns null or self, meaning top of grid/rows)
            val nextUp = focused.focusSearch(View.FOCUS_UP)
            if (nextUp == null || nextUp == focused) {
                findViewById<View>(R.id.search_button)?.requestFocus()
                return true
            }

            // There's somewhere to go up — let default handle it
            return super.dispatchKeyEvent(event)
        }
        return super.dispatchKeyEvent(event)
    }

    private fun findViewByIdSafe(id: Int): View? {
        return try {
            val mainFrag = findViewById<View>(R.id.main_fragment)
            (mainFrag as? ViewGroup)?.findViewById(id)
                ?: window.decorView.findViewById(id)
        } catch (_: Exception) { null }
    }

    private fun isDescendantOf(view: View, ancestor: View?): Boolean {
        if (ancestor == null) return false
        var current: View? = view
        while (current != null) {
            if (current == ancestor) return true
            current = current.parent as? View
        }
        return false
    }
}
