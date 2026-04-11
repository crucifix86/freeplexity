package com.plexclient

import android.app.Application
import com.plexclient.api.PlexClient
import com.plexclient.data.TokenStore

class PlexApp : Application() {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var plexClient: PlexClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenStore = TokenStore(this)
        plexClient = PlexClient(tokenStore)
    }

    companion object {
        lateinit var instance: PlexApp
            private set
    }
}
