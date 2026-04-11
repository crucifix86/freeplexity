package com.plexclient.ui.playback

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.plexclient.R

class PlaybackActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_playback)
    }

    companion object {
        const val EXTRA_ITEM = "extra_item"
        const val EXTRA_RESUME = "extra_resume"
    }
}
