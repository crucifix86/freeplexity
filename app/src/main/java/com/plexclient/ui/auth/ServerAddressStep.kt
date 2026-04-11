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
            "Enter your Plex server address. This is usually your server's IP followed by :32400.\n\nExample: 192.168.1.100:32400",
            "Step 1 of 2",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_ADDRESS)
            .title("Server Address")
            .description("Enter IP:port (e.g. 192.168.1.100:32400)")
            .descriptionEditable(true)
            .descriptionEditInputType(
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            )
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CONNECT)
            .title("Connect")
            .description("")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id == ACTION_CONNECT) {
            val addressAction = findActionById(ACTION_ADDRESS)
            val address = addressAction?.description?.toString()?.trim() ?: ""

            if (address.isBlank()) {
                Toast.makeText(requireContext(), "Enter a server address", Toast.LENGTH_SHORT).show()
                return
            }

            testConnection(address)
        }
    }

    private fun testConnection(address: String) {
        val connectAction = findActionById(ACTION_CONNECT)
        connectAction?.title = "Connecting..."
        notifyActionChanged(findActionPositionById(ACTION_CONNECT))

        // Normalize address
        val serverUrl = if (address.startsWith("http")) address
            else "http://$address"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val http = OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder()
                        .url("$serverUrl/identity")
                        .addHeader("Accept", "application/json")
                        .build()
                    val resp = http.newCall(req).execute()
                    val code = resp.code
                    val body = resp.body?.string() ?: ""
                    Pair(code, body)
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Connection failed")
                }
            }

            connectAction?.title = "Connect"
            notifyActionChanged(findActionPositionById(ACTION_CONNECT))

            when (result.first) {
                200 -> {
                    // Server accessible without auth — save and go
                    val tokenStore = PlexApp.instance.tokenStore
                    tokenStore.serverUrl = serverUrl

                    // Try to get server name from identity response
                    try {
                        val json = com.google.gson.JsonParser.parseString(result.second).asJsonObject
                        val container = json.getAsJsonObject("MediaContainer")
                        tokenStore.serverName = container?.get("machineIdentifier")?.asString ?: address
                    } catch (_: Exception) {
                        tokenStore.serverName = address
                    }

                    // Test if we can actually list libraries (need auth?)
                    val needsAuth = withContext(Dispatchers.IO) {
                        try {
                            val http = OkHttpClient.Builder()
                                .connectTimeout(5, TimeUnit.SECONDS).build()
                            val req = Request.Builder()
                                .url("$serverUrl/library/sections")
                                .addHeader("Accept", "application/json")
                                .build()
                            val resp = http.newCall(req).execute()
                            resp.code == 401
                        } catch (_: Exception) { true }
                    }

                    if (needsAuth) {
                        // Server found but needs token — go to token step
                        GuidedStepSupportFragment.add(
                            parentFragmentManager,
                            TokenStep.newInstance(serverUrl)
                        )
                    } else {
                        // No auth needed — we're done
                        Toast.makeText(requireContext(), "Connected!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(requireActivity(), MainActivity::class.java))
                        requireActivity().finish()
                    }
                }
                -1 -> {
                    Toast.makeText(requireContext(),
                        "Can't reach server at $serverUrl\nCheck the address and make sure it's on your network.",
                        Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(requireContext(),
                        "Server responded with HTTP ${result.first}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val ACTION_ADDRESS = 1L
        private const val ACTION_CONNECT = 2L
    }
}
