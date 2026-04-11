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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ServerAddressStep : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Welcome to FreePlexity",
            "Enter your Plex server address.\n\n" +
            "This is usually your server's IP followed by :32400\n" +
            "Example: 192.168.1.100:32400",
            "",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_ADDRESS)
            .title("Server Address")
            .description("e.g. 192.168.1.100:32400")
            .descriptionEditable(true)
            .descriptionEditInputType(
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            )
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CONNECT_NO_AUTH)
            .title("Connect Without Token")
            .description("For servers with local auth disabled")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CONNECT_WITH_AUTH)
            .title("Connect With Plex Account")
            .description("Sign in through plex.tv")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val addressAction = findActionById(ACTION_ADDRESS)
        val address = addressAction?.description?.toString()?.trim() ?: ""

        if (address.isBlank() || address == "e.g. 192.168.1.100:32400") {
            Toast.makeText(requireContext(), "Enter a server address first", Toast.LENGTH_SHORT).show()
            return
        }

        val serverUrl = if (address.startsWith("http")) address else "http://$address"

        when (action.id) {
            ACTION_CONNECT_NO_AUTH -> connectWithoutToken(serverUrl)
            ACTION_CONNECT_WITH_AUTH -> {
                GuidedStepSupportFragment.add(
                    parentFragmentManager,
                    TokenStep.newInstance(serverUrl)
                )
            }
        }
    }

    private fun connectWithoutToken(serverUrl: String) {
        val action = findActionById(ACTION_CONNECT_NO_AUTH)
        action?.title = "Connecting..."
        notifyActionChanged(findActionPositionById(ACTION_CONNECT_NO_AUTH))

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val http = OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder()
                        .url("$serverUrl/library/sections")
                        .addHeader("Accept", "application/json")
                        .build()
                    http.newCall(req).execute().code
                } catch (e: Exception) { -1 }
            }

            action?.title = "Connect Without Token"
            notifyActionChanged(findActionPositionById(ACTION_CONNECT_NO_AUTH))

            when (result) {
                200 -> {
                    val tokenStore = PlexApp.instance.tokenStore
                    tokenStore.serverUrl = serverUrl
                    tokenStore.serverName = serverUrl.removePrefix("http://").removeSuffix(":32400")
                    // No token needed
                    startActivity(Intent(requireActivity(), MainActivity::class.java))
                    requireActivity().finish()
                }
                401 -> {
                    Toast.makeText(requireContext(),
                        "Server requires authentication.\n\n" +
                        "To disable: In Plex go to Settings > Network > Show Advanced, " +
                        "then add your network (e.g. 192.168.1.0/24) to " +
                        "\"List of IP addresses and networks that are allowed without auth\"\n\n" +
                        "Or use \"Connect With Plex Account\" instead.",
                        Toast.LENGTH_LONG).show()
                }
                -1 -> {
                    Toast.makeText(requireContext(),
                        "Can't reach $serverUrl\nCheck the address and make sure it's on your network.",
                        Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(requireContext(), "Server returned HTTP $result", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val ACTION_ADDRESS = 1L
        private const val ACTION_CONNECT_NO_AUTH = 2L
        private const val ACTION_CONNECT_WITH_AUTH = 3L
    }
}
