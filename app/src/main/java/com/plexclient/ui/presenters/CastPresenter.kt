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
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.plexclient.api.PlexClient
import com.plexclient.api.models.CastMember
import com.plexclient.api.models.CrewMember

class CastPresenter(
    private val serverUrl: String,
    private val plexClient: PlexClient
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = CastCardView(parent.context)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val card = viewHolder.view as CastCardView

        when (item) {
            is CastMember -> {
                card.setName(item.name)
                card.setRole(item.role ?: "")
                loadPhoto(card, item.thumb)
            }
            is CrewMember -> {
                card.setName(item.name)
                card.setRole("")
                loadPhoto(card, item.thumb)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as CastCardView
        card.photoView.setImageDrawable(null)
    }

    private fun loadPhoto(card: CastCardView, thumb: String?) {
        val imageUrl = when {
            thumb == null -> null
            thumb.startsWith("http") -> thumb  // External URL, use directly
            else -> plexClient.getImageUrl(serverUrl, thumb, 120, 120)
        }

        if (imageUrl != null) {
            Glide.with(card.context)
                .load(imageUrl)
                .apply(RequestOptions().transform(CircleCrop())
                    .placeholder(ColorDrawable(0xFF3A3A3A.toInt()))
                    .error(ColorDrawable(0xFF3A3A3A.toInt())))
                .into(card.photoView)
        } else {
            card.showInitial()
        }
    }
}

class CastCardView(context: android.content.Context) : FrameLayout(context) {

    val photoView: ImageView
    private val nameView: TextView
    private val roleView: TextView
    private val initialView: TextView

    private val density = context.resources.displayMetrics.density
    private val photoSize = (72 * density).toInt()
    private val cardWidth = (100 * density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Photo container
        val photoContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(photoSize, photoSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        photoView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(photoSize, photoSize)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        photoContainer.addView(photoView)

        // Initial letter fallback (shown when no photo)
        initialView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(photoSize, photoSize)
            gravity = Gravity.CENTER
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 24f
            setBackgroundColor(0xFF3A3A3A.toInt())
            visibility = View.GONE
        }
        photoContainer.addView(initialView)

        root.addView(photoContainer)

        nameView = TextView(context).apply {
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 11f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        root.addView(nameView, LinearLayout.LayoutParams(
            cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        roleView = TextView(context).apply {
            setTextColor(0xFF777777.toInt())
            textSize = 9f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        root.addView(roleView, LinearLayout.LayoutParams(
            cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        addView(root)

        // Focus animation
        onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.1f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            v.elevation = if (hasFocus) 4 * density else 0f
        }
    }

    fun setName(name: String) {
        nameView.text = name
        initialView.text = name.firstOrNull()?.uppercase() ?: "?"
    }

    fun setRole(role: String) {
        roleView.text = role
        roleView.visibility = if (role.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun showInitial() {
        photoView.setImageDrawable(null)
        initialView.visibility = View.VISIBLE
    }
}
