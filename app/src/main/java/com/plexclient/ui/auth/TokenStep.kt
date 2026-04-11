package com.plexclient.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TokenStep : GuidedStepSupportFragment() {

    private var serverUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverUrl = arguments?.getString(ARG_SERVER_URL) ?: ""
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Plex Token Required",
            "Your server requires authentication. You need a Plex token.\n\n" +
            "How to get your token:\n\n" +
            "Option 1 — From Plex Web:\n" +
            "Open your Plex server in a browser, press F12 to open dev tools, " +
            "go to the Console tab, and type:\n" +
            "localStorage.getItem(\"myPlexAccessToken\")\n\n" +
            "Option 2 — From server config:\n" +
            "Find Preferences.xml in your Plex data folder and look for " +
            "PlexOnlineToken=\"your_token_here\"\n\n" +
            "Option 3 — Disable auth:\n" +
            "In Plex Settings > Network > show Advanced, uncheck " +
            "\"Require authentication for local network access\" " +
            "and restart, then go back and try without a token.",
            "Step 2 of 2",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_TOKEN)
            .title("Plex Token")
            .description("Paste your token here")
            .descriptionEditable(true)
            .descriptionEditInputType(android.text.InputType.TYPE_CLASS_TEXT)
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_VERIFY)
            .title("Verify & Connect")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_RETRY_NO_TOKEN)
            .title("Try Without Token")
            .description("If you disabled the auth requirement")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_VERIFY -> {
                val tokenAction = findActionById(ACTION_TOKEN)
                val token = tokenAction?.description?.toString()?.trim() ?: ""
                if (token.isBlank() || token == "Paste your token here") {
                    Toast.makeText(requireContext(), "Enter your Plex token", Toast.LENGTH_SHORT).show()
                    return
                }
                verifyToken(token)
            }
            ACTION_RETRY_NO_TOKEN -> {
                verifyToken(null)
            }
        }
    }

    private fun verifyToken(token: String?) {
        val verifyAction = findActionById(ACTION_VERIFY)
        verifyAction?.title = "Verifying..."
        notifyActionChanged(findActionPositionById(ACTION_VERIFY))

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val http = OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()
                    val urlBuilder = "$serverUrl/library/sections".toHttpUrl().newBuilder()
                    if (token != null) {
                        urlBuilder.addQueryParameter("X-Plex-Token", token)
                    }
                    val req = Request.Builder()
                        .url(urlBuilder.build())
                        .addHeader("Accept", "application/json")
                        .build()
                    val resp = http.newCall(req).execute()
                    resp.code == 200
                } catch (_: Exception) { false }
            }

            verifyAction?.title = "Verify & Connect"
            notifyActionChanged(findActionPositionById(ACTION_VERIFY))

            if (success) {
                val tokenStore = PlexApp.instance.tokenStore
                tokenStore.serverUrl = serverUrl
                tokenStore.authToken = token
                if (tokenStore.serverName == null) tokenStore.serverName = serverUrl

                Toast.makeText(requireContext(), "Connected!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireActivity(), MainActivity::class.java))
                requireActivity().finish()
            } else {
                val msg = if (token != null) "Token didn't work — check that it's correct"
                    else "Still requires authentication"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val ARG_SERVER_URL = "server_url"
        private const val ACTION_TOKEN = 1L
        private const val ACTION_VERIFY = 2L
        private const val ACTION_RETRY_NO_TOKEN = 3L

        fun newInstance(serverUrl: String): TokenStep {
            return TokenStep().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_URL, serverUrl)
                }
            }
        }
    }
}
