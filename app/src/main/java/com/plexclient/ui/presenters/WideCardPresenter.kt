package com.plexclient.ui.presenters

import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.plexclient.api.PlexClient
import com.plexclient.api.models.MediaItem

/**
 * Wide landscape card for "Continue Watching" / On Deck rows.
 * Shows a backdrop/thumbnail with progress bar and episode info.
 * Mimics Plex's wide card style.
 */
class WideCardPresenter(
    private val serverUrl: String,
    private val plexClient: PlexClient
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = WideCardView(parent.context)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val mediaItem = item as MediaItem
        val card = viewHolder.view as WideCardView

        // Title line: "Show Name" or "Movie Name"
        val topLine = when (mediaItem.type) {
            "episode" -> mediaItem.grandparentTitle ?: mediaItem.title
            else -> mediaItem.title
        }

        // Subtitle: "S01E03 · Episode Title" or "1h 23m remaining"
        val bottomLine = when (mediaItem.type) {
            "episode" -> {
                val epNum = if (mediaItem.parentIndex != null && mediaItem.index != null) {
                    "S${mediaItem.parentIndex.toString().padStart(2, '0')}E${mediaItem.index.toString().padStart(2, '0')}"
                } else ""
                if (epNum.isNotEmpty()) "$epNum · ${mediaItem.title}" else mediaItem.title
            }
            else -> {
                val remaining = mediaItem.duration?.let { dur ->
                    val offset = mediaItem.viewOffset ?: 0
                    val remainMs = dur - offset
                    val mins = remainMs / 60000
                    if (mins > 60) "${mins / 60}h ${mins % 60}m left"
                    else "${mins}m left"
                }
                remaining ?: mediaItem.year?.toString() ?: ""
            }
        }

        card.setTitle(topLine)
        card.setSubtitle(bottomLine)
        card.setProgress(mediaItem.progressPercent)

        // Use art/backdrop for wide cards, fall back to thumb
        val imagePath = mediaItem.bestArt ?: mediaItem.bestThumb
        val imageUrl = plexClient.getImageUrl(serverUrl, imagePath, width = CARD_WIDTH * 2, height = CARD_HEIGHT * 2)

        if (imageUrl != null) {
            Glide.with(card.context)
                .load(imageUrl)
                .apply(RequestOptions()
                    .transform(CenterCrop(), RoundedCorners(12))
                    .placeholder(ColorDrawable(0xFF2A2A2A.toInt()))
                    .error(ColorDrawable(0xFF2A2A2A.toInt()))
                )
                .into(card.imageView)
        } else {
            card.imageView.setImageDrawable(ColorDrawable(0xFF2A2A2A.toInt()))
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as WideCardView
        card.imageView.setImageDrawable(null)
    }

    companion object {
        const val CARD_WIDTH = 210
        const val CARD_HEIGHT = 118  // 16:9 ratio
    }
}

class WideCardView(context: android.content.Context) : FrameLayout(context) {

    val imageView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val progressBar: View
    private val progressTrack: View
    private val gradientOverlay: View

    private val density = context.resources.displayMetrics.density
    private val widthPx = (WideCardPresenter.CARD_WIDTH * density).toInt()
    private val heightPx = (WideCardPresenter.CARD_HEIGHT * density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Image container
        val imageContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
        }

        imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        imageContainer.addView(imageView)

        // Gradient at bottom of image for text readability
        gradientOverlay = View(context).apply {
            val gradientHeight = (heightPx * 0.4f).toInt()
            layoutParams = FrameLayout.LayoutParams(widthPx, gradientHeight, Gravity.BOTTOM)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        imageContainer.addView(gradientOverlay)

        // Progress track
        val progressHeight = (3 * density).toInt()
        progressTrack = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(widthPx, progressHeight, Gravity.BOTTOM)
            setBackgroundColor(0x44FFFFFF)
            visibility = View.GONE
        }
        imageContainer.addView(progressTrack)

        // Progress bar
        progressBar = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(0, progressHeight, Gravity.BOTTOM)
            setBackgroundColor(0xFFE5A00D.toInt())
            visibility = View.GONE
        }
        imageContainer.addView(progressBar)

        root.addView(imageContainer)

        // Info area
        val infoArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        titleView = TextView(context).apply {
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        infoArea.addView(titleView)

        subtitleView = TextView(context).apply {
            setTextColor(0xFF999999.toInt())
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        infoArea.addView(subtitleView)

        root.addView(infoArea)
        addView(root)

        // Focus animation
        onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.05f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            v.elevation = if (hasFocus) 8 * density else 0f
        }
    }

    fun setTitle(text: String) { titleView.text = text }
    fun setSubtitle(text: String) { subtitleView.text = text }

    fun setProgress(percent: Int) {
        if (percent > 0) {
            progressTrack.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            progressBar.layoutParams = (progressBar.layoutParams as FrameLayout.LayoutParams).apply {
                width = (widthPx * percent / 100f).toInt()
            }
        } else {
            progressTrack.visibility = View.GONE
            progressBar.visibility = View.GONE
        }
    }
}
