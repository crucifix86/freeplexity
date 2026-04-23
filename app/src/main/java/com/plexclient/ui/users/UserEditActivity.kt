package com.plexclient.ui.users

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.api.models.Library
import com.plexclient.data.UserRole
import com.plexclient.data.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserEditActivity : FragmentActivity() {

    private val userStore get() = PlexApp.instance.userStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFF1F1F1F.toInt())

        val forceAdmin = intent.getBooleanExtra(EXTRA_FORCE_ADMIN, false)
        val editingId = intent.getStringExtra(EXTRA_USER_ID)
        val existing = editingId?.let { id -> userStore.getAll().firstOrNull { it.id == id } }

        val density = resources.displayMetrics.density
        val dp: (Int) -> Int = { v -> (v * density).toInt() }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(this).apply {
            text = when {
                existing != null -> "Edit user"
                forceAdmin -> "Create admin account"
                else -> "Add user"
            }
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 24f
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(fieldLabel("Name", dp))
        val nameField = EditText(this).apply {
            setText(existing?.name ?: "")
            setTextColor(0xFFEEEEEE.toInt())
            setHintTextColor(0xFF666666.toInt())
            hint = "e.g. Doug, Kids"
            maxLines = 1
        }
        root.addView(nameField)

        // Role
        root.addView(fieldLabel("Role", dp))
        val roleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        val adminBox = CheckBox(this).apply {
            text = "Admin"
            setTextColor(0xFFEEEEEE.toInt())
            isChecked = forceAdmin || existing?.role == UserRole.ADMIN
            isEnabled = !forceAdmin
        }
        val restrictedBox = CheckBox(this).apply {
            text = "Restricted"
            setTextColor(0xFFEEEEEE.toInt())
            isChecked = !forceAdmin && existing?.role == UserRole.RESTRICTED
            isEnabled = !forceAdmin
        }
        // Mutually exclusive (not a RadioGroup only because it styles worse on TV)
        adminBox.setOnCheckedChangeListener { _, c -> if (c) restrictedBox.isChecked = false }
        restrictedBox.setOnCheckedChangeListener { _, c -> if (c) adminBox.isChecked = false }
        roleGroup.addView(adminBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(24) })
        roleGroup.addView(restrictedBox)
        root.addView(roleGroup)

        // PIN (optional)
        root.addView(fieldLabel("PIN (optional, 4 digits)", dp))
        val pinField = EditText(this).apply {
            setText(existing?.pin ?: "")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(0xFFEEEEEE.toInt())
            hint = "Leave blank for no PIN"
            setHintTextColor(0xFF666666.toInt())
            maxLines = 1
        }
        root.addView(pinField)

        // Library access (shown only for restricted users, hidden for admin)
        val librariesLabel = fieldLabel("Allowed libraries (restricted users only)", dp)
        val libraryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(librariesLabel)
        root.addView(libraryContainer)
        val libraryCheckboxes = mutableMapOf<String, CheckBox>()

        fun refreshLibraryVisibility() {
            val show = restrictedBox.isChecked && !forceAdmin
            librariesLabel.visibility = if (show) View.VISIBLE else View.GONE
            libraryContainer.visibility = if (show) View.VISIBLE else View.GONE
        }
        adminBox.setOnCheckedChangeListener { _, c ->
            if (c) restrictedBox.isChecked = false
            refreshLibraryVisibility()
        }
        restrictedBox.setOnCheckedChangeListener { _, c ->
            if (c) adminBox.isChecked = false
            refreshLibraryVisibility()
        }
        refreshLibraryVisibility()

        // Load libraries async
        lifecycleScope.launch {
            val serverUrl = PlexApp.instance.tokenStore.serverUrl
            val libs: List<Library> = if (serverUrl == null) emptyList() else try {
                withContext(Dispatchers.IO) { PlexApp.instance.plexClient.getLibraries(serverUrl) }
            } catch (_: Exception) { emptyList() }

            libraryContainer.removeAllViews()
            if (libs.isEmpty()) {
                libraryContainer.addView(TextView(this@UserEditActivity).apply {
                    text = "(no libraries available)"
                    setTextColor(0xFF888888.toInt())
                    textSize = 12f
                })
            } else {
                val existingAllowed = existing?.allowedLibraryKeys?.toSet() ?: emptySet()
                for (lib in libs) {
                    val cb = CheckBox(this@UserEditActivity).apply {
                        text = "${lib.title}  (${lib.type})"
                        setTextColor(0xFFEEEEEE.toInt())
                        isChecked = lib.key in existingAllowed
                    }
                    libraryCheckboxes[lib.key] = cb
                    libraryContainer.addView(cb)
                }
            }
        }

        // Buttons
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(24), 0, 0)
        }
        val saveBtn = Button(this).apply {
            text = if (existing != null) "Save" else "Create"
            setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this@UserEditActivity, "Name required", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                val pin = pinField.text.toString().trim().takeIf { it.isNotEmpty() }
                if (pin != null && pin.length != 4) {
                    Toast.makeText(this@UserEditActivity, "PIN must be 4 digits", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                val role = if (adminBox.isChecked || forceAdmin) UserRole.ADMIN else UserRole.RESTRICTED
                val allowed = if (role == UserRole.RESTRICTED)
                    libraryCheckboxes.filterValues { it.isChecked }.keys.toList()
                else emptyList()
                if (existing != null) {
                    userStore.upsert(existing.copy(
                        name = name, role = role, pin = pin, allowedLibraryKeys = allowed
                    ))
                } else {
                    val profile = UserStore.newProfile(
                        name = name, role = role, pin = pin, allowedLibraryKeys = allowed
                    )
                    userStore.upsert(profile)
                    // On first-run, set this newly-created admin as active so the app can proceed.
                    if (forceAdmin) userStore.activeUserId = profile.id
                }
                finish()
            }
        }
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setOnClickListener { finish() }
        }
        buttonRow.addView(cancelBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(12) })
        buttonRow.addView(saveBtn)
        root.addView(buttonRow)

        // Delete button for existing users, not shown if editing self or only admin
        if (existing != null) {
            val deleteBtn = Button(this).apply {
                text = "Delete user"
                setTextColor(0xFFFF6666.toInt())
                setOnClickListener {
                    val adminCount = userStore.getAll().count { it.role == UserRole.ADMIN }
                    if (existing.role == UserRole.ADMIN && adminCount <= 1) {
                        Toast.makeText(this@UserEditActivity, "Can't delete the only admin", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    userStore.remove(existing.id)
                    finish()
                }
            }
            root.addView(deleteBtn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) })
        }

        scroll.addView(root)
        setContentView(scroll)
        nameField.requestFocus()
    }

    private fun fieldLabel(text: String, dp: (Int) -> Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(0xFF999999.toInt())
        textSize = 13f
        setPadding(0, dp(12), 0, dp(4))
    }

    companion object {
        const val EXTRA_FORCE_ADMIN = "force_admin"
        const val EXTRA_USER_ID = "user_id"
    }
}
