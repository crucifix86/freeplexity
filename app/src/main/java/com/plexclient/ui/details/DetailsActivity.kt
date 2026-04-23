package com.plexclient.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import androidx.leanback.app.BackgroundManager
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.plexclient.PlexApp
import com.plexclient.R
import com.plexclient.api.models.MediaItem
import com.plexclient.ui.playback.PlaybackActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailsActivity : FragmentActivity() {

    private val plexClient get() = PlexApp.instance.plexClient
    private val tokenStore get() = PlexApp.instance.tokenStore
    private lateinit var item: MediaItem
    private var themePlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        BackgroundManager.getInstance(this).apply {
            attach(window)
            color = 0xFF1F1F1F.toInt()
        }

        @Suppress("DEPRECATION")
        item = intent.getSerializableExtra(EXTRA_ITEM) as? MediaItem
            ?: run { finish(); return }

        loadBackdrop(item)
        loadFullDetails()
    }

    private fun loadBackdrop(item: MediaItem) {
        val art = item.bestArt ?: return
        val serverUrl = tokenStore.serverUrl ?: return
        val dm = resources.displayMetrics
        val url = plexClient.getImageUrl(serverUrl, art, dm.widthPixels, dm.heightPixels) ?: return
        Glide.with(this)
            .asBitmap()
            .load(url)
            .apply(RequestOptions().centerCrop())
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bmp: Bitmap, transition: Transition<in Bitmap>?) {
                    val base = BitmapDrawable(resources, bmp)
                    val dim = ColorDrawable(0xB0000000.toInt())
                    BackgroundManager.getInstance(this@DetailsActivity).drawable =
                        LayerDrawable(arrayOf(base, dim))
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }

    private fun loadFullDetails() {
        lifecycleScope.launch {
            try {
                val fullItem = withContext(Dispatchers.IO) {
                    plexClient.getMetadata(tokenStore.serverUrl ?: return@withContext null, item.ratingKey)
                } ?: item
                item = fullItem
                bindUI(fullItem)
                startThemeMusic(fullItem)

                // Tell the rows fragment what to load
                val rowsFragment = supportFragmentManager
                    .findFragmentById(R.id.detail_rows_fragment) as? DetailRowsFragment
                rowsFragment?.loadContent(fullItem)

            } catch (e: Exception) {
                bindUI(item)
            }
        }
    }

    private fun bindUI(item: MediaItem) {
        val serverUrl = tokenStore.serverUrl ?: return

        // Poster
        val poster = findViewById<ImageView>(R.id.detail_poster)
        val posterUrl = plexClient.getImageUrl(serverUrl, item.bestThumb, 240, 360)
        if (posterUrl != null) {
            Glide.with(this)
                .load(posterUrl)
                .apply(RequestOptions().transform(CenterCrop(), RoundedCorners(8)))
                .into(poster)
        }

        // Title
        val titleView = findViewById<TextView>(R.id.detail_title)
        titleView.text = when (item.type) {
            "episode" -> {
                val ep = if (item.parentIndex != null && item.index != null) {
                    "S${item.parentIndex.toString().padStart(2, '0')}E${item.index.toString().padStart(2, '0')} · "
                } else ""
                "$ep${item.title}"
            }
            else -> item.title
        }

        // Meta line
        val metaView = findViewById<TextView>(R.id.detail_meta)
        metaView.text = buildMetaString(item)

        // Summary
        val summaryView = findViewById<TextView>(R.id.detail_summary)
        summaryView.text = item.summary

        // Action buttons
        val actionsContainer = findViewById<LinearLayout>(R.id.detail_actions)
        actionsContainer.removeAllViews()

        if (item.isPlayable) {
            if (item.hasProgress) {
                addButton(actionsContainer, "▶  Resume") {
                    playItem(item, resume = true)
                }
                addButton(actionsContainer, "Play from Start") {
                    playItem(item, resume = false)
                }
            } else {
                addButton(actionsContainer, "▶  Play") {
                    playItem(item, resume = false)
                }
            }
        }

        addButton(actionsContainer,
            if (item.viewCount != null && item.viewCount > 0) "Mark Unwatched" else "Mark Watched"
        ) {
            toggleWatched(item)
        }

        // Focus the first button
        if (actionsContainer.childCount > 0) {
            actionsContainer.getChildAt(0).requestFocus()
        }
    }

    private fun addButton(container: LinearLayout, text: String, onClick: () -> Unit) {
        val density = resources.displayMetrics.density
        val btn = TextView(this).apply {
            this.text = text
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 14f
            setBackgroundResource(R.drawable.detail_button_bg)
            setPadding(
                (16 * density).toInt(), (8 * density).toInt(),
                (16 * density).toInt(), (8 * density).toInt()
            )
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                    onClick()
                    true
                } else false
            }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (8 * density).toInt() }
        container.addView(btn, lp)
    }

    private fun playItem(item: MediaItem, resume: Boolean) {
        startActivity(Intent(this, PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_ITEM, item)
            putExtra(PlaybackActivity.EXTRA_RESUME, resume)
        })
    }

    private fun toggleWatched(item: MediaItem) {
        val serverUrl = tokenStore.serverUrl ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (item.viewCount != null && item.viewCount > 0) {
                    plexClient.markUnwatched(serverUrl, item.ratingKey)
                } else {
                    plexClient.markWatched(serverUrl, item.ratingKey)
                }
            }
        }
    }

    private fun buildMetaString(item: MediaItem): String = buildString {
        when (item.type) {
            "movie" -> {
                item.year?.let { append(it) }
                item.duration?.let {
                    if (isNotEmpty()) append("  ·  ")
                    val mins = it / 60000
                    val h = mins / 60; val m = mins % 60
                    if (h > 0) append("${h}h ${m}m") else append("${m}m")
                }
                item.media.firstOrNull()?.let { mi ->
                    mi.videoResolution?.let { if (isNotEmpty()) append("  ·  "); append(it.uppercase()) }
                    mi.videoCodec?.let { if (isNotEmpty()) append("  ·  "); append(it.uppercase()) }
                    mi.audioCodec?.let { if (isNotEmpty()) append("  ·  "); append(it.uppercase()) }
                }
            }
            "episode" -> {
                item.grandparentTitle?.let { append(it) }
                item.duration?.let { if (isNotEmpty()) append("  ·  "); append("${it / 60000}m") }
            }
            "show" -> {
                item.year?.let { append(it) }
                item.leafCount?.let { if (isNotEmpty()) append("  ·  "); append("$it episodes") }
            }
            "season" -> {
                item.parentTitle?.let { append(it) }
                item.leafCount?.let { if (isNotEmpty()) append("  ·  "); append("$it episodes") }
            }
        }
    }

    private fun startThemeMusic(item: MediaItem) {
        val themePath = item.bestTheme ?: return
        val serverUrl = tokenStore.serverUrl ?: return
        val token = tokenStore.authToken
        val url = if (token != null) "$serverUrl$themePath?X-Plex-Token=$token" else "$serverUrl$themePath"
        stopThemeMusic()
        themePlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener { mp ->
                mp.setVolume(0f, 0f)
                mp.isLooping = true
                mp.start()
                val handler = Handler(Looper.getMainLooper())
                val steps = 15
                val target = 0.4f
                for (i in 1..steps) {
                    val v = (i.toFloat() / steps) * target
                    handler.postDelayed({ runCatching { mp.setVolume(v, v) } }, (i * 100L))
                }
            }
            setOnErrorListener { _, _, _ -> stopThemeMusic(); true }
            try {
                setDataSource(url)
                prepareAsync()
            } catch (_: Exception) {
                stopThemeMusic()
            }
        }
    }

    private fun stopThemeMusic() {
        themePlayer?.apply {
            runCatching { if (isPlaying) stop() }
            runCatching { release() }
        }
        themePlayer = null
    }

    override fun onPause() {
        super.onPause()
        stopThemeMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopThemeMusic()
    }

    companion object {
        const val EXTRA_ITEM = "extra_item"
    }
}
