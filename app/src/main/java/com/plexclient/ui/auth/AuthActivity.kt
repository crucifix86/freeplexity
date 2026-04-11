package com.plexclient.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import com.plexclient.PlexApp
import com.plexclient.ui.main.MainActivity

class AuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenStore = PlexApp.instance.tokenStore

        // Already set up — go straight to main
        if (tokenStore.serverUrl != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ServerAddressStep(), android.R.id.content)
        }
    }
}
