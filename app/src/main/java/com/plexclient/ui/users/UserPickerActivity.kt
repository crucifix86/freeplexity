package com.plexclient.ui.users

import android.app.AlertDialog
import android.content.Intent
import com.plexclient.ui.main.MainActivity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.plexclient.PlexApp
import com.plexclient.data.UserProfile
import com.plexclient.data.UserRole

class UserPickerActivity : FragmentActivity() {

    private val userStore get() = PlexApp.instance.userStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFF1F1F1F.toInt())
        render()
    }

    override fun onResume() {
        super.onResume()
        when {
            userStore.isEmpty -> startFirstRun()
            // First-run admin creation sets activeUserId — go straight into the app.
            userStore.activeUserId != null && intent.getBooleanExtra(EXTRA_FORCE_PICK, false).not() -> {
                goToMain()
            }
            else -> render()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun render() {
        if (userStore.isEmpty) { startFirstRun(); return }

        val density = resources.displayMetrics.density
        val dp: (Int) -> Int = { v -> (v * density).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(32), dp(48), dp(32))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(TextView(this).apply {
            text = "Who's watching?"
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        val tiles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val profiles = userStore.getAll()
        for (profile in profiles) {
            tiles.addView(buildTile(profile, dp) { attemptSelect(profile) })
        }

        root.addView(tiles)
        setContentView(root)

        // Focus the first tile
        tiles.getChildAt(0)?.requestFocus()
    }

    private fun buildTile(profile: UserProfile, dp: (Int) -> Int, onClick: () -> Unit): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(12); marginEnd = dp(12)
            }
            isFocusable = true; isFocusableInTouchMode = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onClick() }
        }

        // Circle with initials
        val avatar = TextView(this).apply {
            text = profile.initials
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 32f
            val size = dp(96)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(profile.avatarColor)
            }
        }
        tile.addView(avatar)

        tile.addView(TextView(this).apply {
            text = profile.name
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            maxLines = 1
        })

        tile.addView(TextView(this).apply {
            text = when (profile.role) {
                UserRole.ADMIN -> "Admin"
                UserRole.RESTRICTED -> "Restricted"
            } + if (profile.pin != null) " · PIN" else ""
            setTextColor(0xFF999999.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
        })

        // Focus scale animation
        tile.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.1f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
        }

        return tile
    }

    private fun attemptSelect(profile: UserProfile) {
        val pin = profile.pin
        if (pin == null) {
            setActiveAndFinish(profile)
        } else {
            promptPin("Enter PIN for ${profile.name}") { entered ->
                if (entered == pin) setActiveAndFinish(profile)
            }
        }
    }

    private fun setActiveAndFinish(profile: UserProfile) {
        userStore.activeUserId = profile.id
        goToMain()
    }

    private fun startFirstRun() {
        // First user must be admin.
        startActivity(Intent(this, UserEditActivity::class.java).apply {
            putExtra(UserEditActivity.EXTRA_FORCE_ADMIN, true)
        })
    }

    private fun promptPin(title: String, onEntered: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
            maxLines = 1
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onEntered(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        // Pass this from Settings > Switch User to re-show the picker even if a user is active.
        const val EXTRA_FORCE_PICK = "force_pick"
    }
}
