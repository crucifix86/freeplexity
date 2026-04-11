package com.plexclient.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.plexclient.PlexApp

class SettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, SettingsFragment(), android.R.id.content)
        }
    }
}

class SettingsFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Settings",
            "PlexClient",
            "",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val tokenStore = PlexApp.instance.tokenStore

        // Server info
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

        // Playback
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
            .checked(getDirectPlayPref())
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_AUDIO_PASSTHROUGH)
            .title("Audio Passthrough")
            .description("Pass surround audio to receiver")
            .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
            .checked(getAudioPassthroughPref())
            .build())

        // Subtitles
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SUBTITLE_SIZE)
            .title("Subtitle Size")
            .description(getSubtitleSizeLabel())
            .build())

        // About
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CLEAR)
            .title("Sign Out")
            .description("Clear saved server and token")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_VERSION)
            .title("Version")
            .description("1.0-debug")
            .editable(false)
            .build())
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
                val enabled = action.isChecked
                prefs.edit().putBoolean("direct_play", enabled).apply()
            }
            ACTION_AUDIO_PASSTHROUGH -> {
                val enabled = action.isChecked
                prefs.edit().putBoolean("audio_passthrough", enabled).apply()
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
            ACTION_CLEAR -> {
                PlexApp.instance.tokenStore.clear()
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
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

    private fun getDirectPlayPref(): Boolean {
        return requireContext().getSharedPreferences("plex_settings", 0)
            .getBoolean("direct_play", true)
    }

    private fun getAudioPassthroughPref(): Boolean {
        return requireContext().getSharedPreferences("plex_settings", 0)
            .getBoolean("audio_passthrough", true)
    }

    companion object {
        private const val ACTION_SERVER = 1L
        private const val ACTION_SERVER_URL = 2L
        private const val ACTION_QUALITY = 3L
        private const val ACTION_DIRECT_PLAY = 4L
        private const val ACTION_AUDIO_PASSTHROUGH = 5L
        private const val ACTION_SUBTITLE_SIZE = 6L
        private const val ACTION_CLEAR = 7L
        private const val ACTION_VERSION = 8L
    }
}
