package com.plexclient.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.plexclient.PlexApp
import com.plexclient.ui.main.MainActivity

class AuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenStore = PlexApp.instance.tokenStore
        tokenStore.serverUrl = "http://192.168.1.223:32400"
        tokenStore.serverName = "POUGHKEEPSIE"
        tokenStore.authToken = "Ge5FRWacHEf9bA2u94bp"

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
