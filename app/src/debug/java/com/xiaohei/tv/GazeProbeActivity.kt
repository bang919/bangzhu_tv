package com.xiaohei.tv

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.xiaohei.tv.data.net.TlsCompat
import okhttp3.OkHttpClient
import java.io.File

@UnstableApi
class GazeProbeActivity : Activity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val playlist = File(getExternalFilesDir(null), "gaze.m3u8")
        Log.i("GazeProbe", "playlist=${playlist.absolutePath} exists=${playlist.exists()} size=${playlist.length()}")

        val client = TlsCompat.configure(OkHttpClient.Builder()).build()
        val httpFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .setDefaultRequestProperties(mapOf("Referer" to "https://gaze.red/"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
            )
            .build()
            .apply { playWhenReady = true }

        setContentView(
            PlayerView(this).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
                player = this@GazeProbeActivity.player
            }
        )

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.i("GazeProbe", "state=$state duration=${player.duration}")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("GazeProbe", "error ${error.errorCodeName}: ${error.message}", error)
            }
        })

        val encoded = android.util.Base64.encodeToString(
            playlist.readText().toByteArray(),
            android.util.Base64.NO_WRAP
        )
        player.setMediaItem(
            MediaItem.Builder()
                .setUri("data:application/vnd.apple.mpegurl;base64,$encoded")
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                .build()
        )
        player.prepare()

        val handler = android.os.Handler(mainLooper)
        var ticks = 0
        val logger = object : Runnable {
            override fun run() {
                Log.i(
                    "GazeProbe",
                    "t=${player.currentPosition} buf=${player.bufferedPosition} " +
                        "state=${player.playbackState} playing=${player.isPlaying}"
                )
                if (++ticks < 30) handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(logger, 2000)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
