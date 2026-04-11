package com.plexclient.ui.presenters

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.plexclient.api.PlexClient
import com.plexclient.api.models.MediaItem

class CardPresenter(
    private val serverUrl: String,
    private val plexClient: PlexClient
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val card = PosterCardView(context, CARD_WIDTH, CARD_HEIGHT)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val mediaItem = item as MediaItem
        val card = viewHolder.view as PosterCardView

        card.setTitle(mediaItem.displayTitle)
        card.setSubtitle(mediaItem.displaySubtitle ?: "")
        card.setProgress(mediaItem.progressPercent)
        card.setWatched(mediaItem.viewCount != null && mediaItem.viewCount > 0 && !mediaItem.hasProgress)

        val imageUrl = plexClient.getImageUrl(
            serverUrl, mediaItem.bestThumb,
            width = CARD_WIDTH * 2, height = CARD_HEIGHT * 2
        )

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
        val card = viewHolder.view as PosterCardView
        card.imageView.setImageDrawable(null)
    }

    companion object {
        const val CARD_WIDTH = 110
        const val CARD_HEIGHT = 165
    }
}

/**
 * Custom poster card (2:3 ratio) that looks like Plex cards.
 * Poster image with rounded corners, progress bar overlay, title/subtitle below.
 */
class PosterCardView(
    context: android.content.Context,
    private val cardWidth: Int,
    private val cardHeight: Int
) : FrameLayout(context) {

    val imageView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val progressBar: View
    private val progressTrack: View
    private val watchedOverlay: View
    private val watchedIcon: TextView

    private val density = context.resources.displayMetrics.density
    private val widthPx = (cardWidth * density).toInt()
    private val heightPx = (cardHeight * density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Image container with progress overlay
        val imageContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
        }

        imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        imageContainer.addView(imageView)

        // Watched overlay (semi-transparent)
        watchedOverlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            setBackgroundColor(0x88000000.toInt())
            visibility = View.GONE
        }
        imageContainer.addView(watchedOverlay)

        // Watched checkmark
        watchedIcon = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = "\u2713" // checkmark
            setTextColor(0xFFE5A00D.toInt())
            textSize = 28f
            visibility = View.GONE
        }
        imageContainer.addView(watchedIcon)

        // Progress track (full width, at bottom of image)
        val progressHeight = (3 * density).toInt()
        progressTrack = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(widthPx, progressHeight, Gravity.BOTTOM)
            setBackgroundColor(0x44FFFFFF)
            visibility = View.GONE
        }
        imageContainer.addView(progressTrack)

        // Progress bar (partial width, at bottom of image)
        progressBar = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(0, progressHeight, Gravity.BOTTOM)
            setBackgroundColor(0xFFE5A00D.toInt())
            visibility = View.GONE
        }
        imageContainer.addView(progressBar)

        root.addView(imageContainer)

        // Info area below image
        val infoArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        titleView = TextView(context).apply {
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        infoArea.addView(titleView)

        subtitleView = TextView(context).apply {
            setTextColor(0xFF999999.toInt())
            textSize = 9f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        infoArea.addView(subtitleView)

        root.addView(infoArea)
        addView(root)

        // Focus animation: scale up on focus like Plex
        onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            if (hasFocus) {
                v.elevation = 8 * density
            } else {
                v.elevation = 0f
            }
        }
    }

    fun setTitle(text: String) { titleView.text = text }
    fun setSubtitle(text: String) { subtitleView.text = text }

    fun setProgress(percent: Int) {
        if (percent > 0) {
            progressTrack.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            val barWidth = (widthPx * percent / 100f).toInt()
            progressBar.layoutParams = (progressBar.layoutParams as FrameLayout.LayoutParams).apply {
                width = barWidth
            }
            watchedOverlay.visibility = View.GONE
            watchedIcon.visibility = View.GONE
        } else {
            progressTrack.visibility = View.GONE
            progressBar.visibility = View.GONE
        }
    }

    fun setWatched(watched: Boolean) {
        if (watched) {
            watchedOverlay.visibility = View.VISIBLE
            watchedIcon.visibility = View.VISIBLE
        } else {
            watchedOverlay.visibility = View.GONE
            watchedIcon.visibility = View.GONE
        }
    }
}
