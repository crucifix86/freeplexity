package com.plexclient.ui.users

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.plexclient.PlexApp
import com.plexclient.data.UserProfile
import com.plexclient.data.UserRole

class UserManagementActivity : FragmentActivity() {

    private val userStore get() = PlexApp.instance.userStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFF1F1F1F.toInt())
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val density = resources.displayMetrics.density
        val dp: (Int) -> Int = { v -> (v * density).toInt() }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
        }

        root.addView(TextView(this).apply {
            text = "Manage Users"
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 24f
            setPadding(0, 0, 0, dp(16))
        })

        // Add user button
        root.addView(Button(this).apply {
            text = "+  Add User"
            setOnClickListener {
                startActivity(Intent(this@UserManagementActivity, UserEditActivity::class.java))
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) })

        // List existing
        for (profile in userStore.getAll()) {
            root.addView(buildProfileRow(profile, dp))
        }

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun buildProfileRow(profile: UserProfile, dp: (Int) -> Int): android.view.View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isFocusable = true; isFocusableInTouchMode = true
            setBackgroundColor(0xFF2A2A2A.toInt())
            setOnClickListener {
                startActivity(Intent(this@UserManagementActivity, UserEditActivity::class.java).apply {
                    putExtra(UserEditActivity.EXTRA_USER_ID, profile.id)
                })
            }
        }

        val avatar = TextView(this).apply {
            text = profile.initials
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 18f
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(profile.avatarColor)
            }
        }
        row.addView(avatar)

        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        text.addView(TextView(this).apply {
            this.text = profile.name
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 16f
        })
        text.addView(TextView(this).apply {
            this.text = buildString {
                append(if (profile.role == UserRole.ADMIN) "Admin" else "Restricted")
                if (profile.pin != null) append(" · PIN")
                if (profile.role == UserRole.RESTRICTED) {
                    if (profile.allowedLibraryKeys.isEmpty()) append(" · No libraries")
                    else append(" · ${profile.allowedLibraryKeys.size} libraries")
                }
            }
            setTextColor(0xFF999999.toInt())
            textSize = 12f
        })
        row.addView(text)

        val margin = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        row.layoutParams = margin
        return row
    }
}
