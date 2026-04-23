package com.plexclient.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.data.UserRole
import com.plexclient.ui.users.UserManagementActivity
import com.plexclient.ui.users.UserPickerActivity
import kotlinx.coroutines.launch

class SettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, SettingsFragment(), android.R.id.content)
        }
    }
}

class SettingsFragment : GuidedStepSupportFragment() {

    private var updateInfo: UpdateInfo? = null
    private val updater by lazy { AppUpdater(requireContext()) }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance("Settings", "FreePlexity", "", null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val tokenStore = PlexApp.instance.tokenStore

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SERVER)
            .title("Server")
            .description(tokenStore.serverName ?: "Not connected")
            .editable(false)
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SERVER_URL)
            .title("Server Address")
            .description(tokenStore.serverUrl ?: "Not set")
            .editable(false)
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_QUALITY)
            .title("Transcode Quality")
            .description(getQualityLabel())
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_DIRECT_PLAY)
            .title("Direct Play")
            .description("Try direct play before transcoding")
            .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
            .checked(getPref("direct_play", true))
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_AUDIO_PASSTHROUGH)
            .title("Audio Passthrough")
            .description("Pass surround audio to receiver")
            .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
            .checked(getPref("audio_passthrough", true))
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SUBTITLE_SIZE)
            .title("Subtitle Size")
            .description(getSubtitleSizeLabel())
            .build())

        // Update action — starts as "Check for Updates"
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_UPDATE)
            .title("Check for Updates")
            .description("Version ${getCurrentVersion()}")
            .build())

        val activeUser = PlexApp.instance.userStore.activeUser
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SWITCH_USER)
            .title("Switch User")
            .description(activeUser?.let { "Currently: ${it.name}" } ?: "No user active")
            .build())

        // Admin-only: manage users
        if (activeUser?.role == UserRole.ADMIN) {
            actions.add(GuidedAction.Builder(requireContext())
                .id(ACTION_MANAGE_USERS)
                .title("Manage Users")
                .description("Add, edit, or remove profiles")
                .build())
        }

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CLEAR)
            .title("Sign Out")
            .description("Clear saved server and token")
            .build())

        // Check for updates automatically
        checkForUpdate()
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val prefs = requireContext().getSharedPreferences("plex_settings", 0)

        when (action.id) {
            ACTION_QUALITY -> {
                val qualities = listOf("4000", "8000", "12000", "20000", "40000")
                val labels = listOf("4 Mbps (720p)", "8 Mbps (1080p)", "12 Mbps (1080p HQ)", "20 Mbps (1080p Max)", "40 Mbps (4K)")
                val current = prefs.getString("transcode_bitrate", "20000") ?: "20000"
                val idx = (qualities.indexOf(current) + 1) % qualities.size
                prefs.edit().putString("transcode_bitrate", qualities[idx]).apply()
                action.description = labels[idx]
                notifyActionChanged(findActionPositionById(ACTION_QUALITY))
            }
            ACTION_DIRECT_PLAY -> {
                prefs.edit().putBoolean("direct_play", action.isChecked).apply()
            }
            ACTION_AUDIO_PASSTHROUGH -> {
                prefs.edit().putBoolean("audio_passthrough", action.isChecked).apply()
            }
            ACTION_SUBTITLE_SIZE -> {
                val sizes = listOf("75", "100", "125", "150")
                val labels = listOf("Small", "Normal", "Large", "Extra Large")
                val current = prefs.getString("subtitle_size", "100") ?: "100"
                val idx = (sizes.indexOf(current) + 1) % sizes.size
                prefs.edit().putString("subtitle_size", sizes[idx]).apply()
                action.description = labels[idx]
                notifyActionChanged(findActionPositionById(ACTION_SUBTITLE_SIZE))
            }
            ACTION_UPDATE -> {
                val info = updateInfo
                if (info != null) {
                    downloadAndInstall(info, action)
                } else {
                    action.description = "Checking..."
                    notifyActionChanged(findActionPositionById(ACTION_UPDATE))
                    checkForUpdate()
                }
            }
            ACTION_SWITCH_USER -> {
                PlexApp.instance.userStore.activeUserId = null
                startActivity(Intent(requireContext(), UserPickerActivity::class.java).apply {
                    putExtra(UserPickerActivity.EXTRA_FORCE_PICK, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                requireActivity().finish()
            }
            ACTION_MANAGE_USERS -> {
                startActivity(Intent(requireContext(), UserManagementActivity::class.java))
            }
            ACTION_CLEAR -> {
                PlexApp.instance.tokenStore.clear()
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val info = updater.checkForUpdate()
            updateInfo = info

            val pos = findActionPositionById(ACTION_UPDATE)
            if (pos < 0) return@launch
            val action = findActionById(ACTION_UPDATE) ?: return@launch

            if (info != null) {
                action.title = "\uD83D\uDD34  Update Available — v${info.versionName}"
                action.description = info.changelog
            } else {
                action.title = "Up to Date"
                action.description = "Version ${getCurrentVersion()}"
            }
            notifyActionChanged(pos)
        }
    }

    private fun downloadAndInstall(info: UpdateInfo, action: GuidedAction) {
        action.title = "Downloading..."
        action.description = "0%"
        notifyActionChanged(findActionPositionById(ACTION_UPDATE))

        lifecycleScope.launch {
            var lastReportedPercent = 0
            val apkFile = updater.downloadUpdate(info.apkUrl) { percent ->
                // Only update UI every 10% to avoid hammering the RecyclerView
                if (percent - lastReportedPercent >= 10 || percent >= 100) {
                    lastReportedPercent = percent
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        action.description = "$percent%"
                        val pos = findActionPositionById(ACTION_UPDATE)
                        if (pos >= 0) notifyActionChanged(pos)
                    }
                }
            }

            if (apkFile != null) {
                action.title = "Installing..."
                action.description = "Opening installer"
                notifyActionChanged(findActionPositionById(ACTION_UPDATE))
                updater.installApk(apkFile)
            } else {
                action.title = "Update Failed"
                action.description = "Tap to retry"
                notifyActionChanged(findActionPositionById(ACTION_UPDATE))
                Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName ?: "?"
        } catch (_: Exception) { "?" }
    }

    private fun getQualityLabel(): String {
        val prefs = requireContext().getSharedPreferences("plex_settings", 0)
        return when (prefs.getString("transcode_bitrate", "20000")) {
            "4000" -> "4 Mbps (720p)"
            "8000" -> "8 Mbps (1080p)"
            "12000" -> "12 Mbps (1080p HQ)"
            "20000" -> "20 Mbps (1080p Max)"
            "40000" -> "40 Mbps (4K)"
            else -> "20 Mbps (1080p Max)"
        }
    }

    private fun getSubtitleSizeLabel(): String {
        val prefs = requireContext().getSharedPreferences("plex_settings", 0)
        return when (prefs.getString("subtitle_size", "100")) {
            "75" -> "Small"
            "100" -> "Normal"
            "125" -> "Large"
            "150" -> "Extra Large"
            else -> "Normal"
        }
    }

    private fun getPref(key: String, default: Boolean): Boolean {
        return requireContext().getSharedPreferences("plex_settings", 0).getBoolean(key, default)
    }

    companion object {
        private const val ACTION_SERVER = 1L
        private const val ACTION_SERVER_URL = 2L
        private const val ACTION_QUALITY = 3L
        private const val ACTION_DIRECT_PLAY = 4L
        private const val ACTION_AUDIO_PASSTHROUGH = 5L
        private const val ACTION_SUBTITLE_SIZE = 6L
        private const val ACTION_UPDATE = 7L
        private const val ACTION_CLEAR = 8L
        private const val ACTION_SWITCH_USER = 9L
        private const val ACTION_MANAGE_USERS = 10L
    }
}
