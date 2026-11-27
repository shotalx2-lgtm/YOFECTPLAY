package com.yofect.play

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlaybackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val attribution = intent.getStringExtra(EXTRA_ATTRIBUTION).orEmpty()
        if (streamUrl.isBlank() || !(streamUrl.startsWith("https://") || streamUrl.startsWith("http://"))) {
            finish()
            return
        }
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        val player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            playWhenReady = true
            prepare()
        }
        setContent {
            var playbackError by remember { mutableStateOf<String?>(null) }
            DisposableEffect(player) {
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = "تعذر تشغيل هذا الملف. قد يكون الرابط غير متاح أو بصيغة غير مدعومة."
                    }
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener); player.release() }
            }
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                if (playbackError != null) {
                    Text(playbackError!!, color = Color.White, modifier = Modifier.padding(12.dp))
                }
                if (attribution.isNotBlank()) {
                    Text(attribution, color = Color.LightGray, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Player lifecycle is managed by the composable's DisposableEffect below.
    }

    companion object {
        const val EXTRA_URL = "yofect.playback.url"
        const val EXTRA_TITLE = "yofect.playback.title"
        const val EXTRA_ATTRIBUTION = "yofect.playback.attribution"
    }
}
