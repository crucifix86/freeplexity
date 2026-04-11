package com.plexclient.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.plexclient.PlexApp
import com.plexclient.api.PlexAuth
import com.plexclient.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TokenStep : GuidedStepSupportFragment() {

    private var serverUrl: String = ""
    private val auth by lazy { PlexAuth(PlexApp.instance.tokenStore) }
    private var polling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverUrl = arguments?.getString(ARG_SERVER_URL) ?: ""
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Sign In With Plex",
            "A code will appear below. On your phone or computer, go to:\n\n" +
            "plex.tv/link\n\n" +
            "and enter the code. This is a one-time setup — FreePlexity stores " +
            "the token locally and never contacts Plex servers again after this.",
            "",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CODE)
            .title("Loading code...")
            .description("")
            .editable(false)
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_REFRESH)
            .title("Get New Code")
            .build())

        startPinFlow()
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id == ACTION_REFRESH) {
            startPinFlow()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        polling = false
    }

    private fun startPinFlow() {
        polling = false

        lifecycleScope.launch {
            try {
                val pin = withContext(Dispatchers.IO) { auth.requestPin() }

                val codeAction = findActionById(ACTION_CODE)
                codeAction?.title = pin.code
                codeAction?.description = "Enter this code at plex.tv/link"
                notifyActionChanged(findActionPositionById(ACTION_CODE))

                // Poll for auth
                polling = true
                while (polling) {
                    delay(2000)
                    val token = withContext(Dispatchers.IO) { auth.checkPin(pin.id) }
                    if (token != null) {
                        polling = false
                        onAuthenticated(token)
                        return@launch
                    }
                }
            } catch (e: Exception) {
                val codeAction = findActionById(ACTION_CODE)
                codeAction?.title = "Error"
                codeAction?.description = e.message ?: "Failed to get code"
                notifyActionChanged(findActionPositionById(ACTION_CODE))
            }
        }
    }

    private fun onAuthenticated(token: String) {
        val tokenStore = PlexApp.instance.tokenStore
        tokenStore.serverUrl = serverUrl
        tokenStore.authToken = token
        tokenStore.serverName = serverUrl.removePrefix("http://").removeSuffix(":32400")

        Toast.makeText(requireContext(), "Signed in!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireActivity(), MainActivity::class.java))
        requireActivity().finish()
    }

    companion object {
        private const val ARG_SERVER_URL = "server_url"
        private const val ACTION_CODE = 1L
        private const val ACTION_REFRESH = 2L

        fun newInstance(serverUrl: String): TokenStep {
            return TokenStep().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_URL, serverUrl)
                }
            }
        }
    }
}
