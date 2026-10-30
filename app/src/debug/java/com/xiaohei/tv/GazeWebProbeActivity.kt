package com.xiaohei.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.xiaohei.tv.data.net.TlsCompat
import okhttp3.OkHttpClient
import org.json.JSONObject

@UnstableApi
@SuppressLint("SetJavaScriptEnabled")
class GazeWebProbeActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var playbackStarted = false
    private var lastCapClickAt = 0L
    private var lastLoggedAt = 0L
    private var lastResolveAt = 0L
    private var resolveAttempts = 0

    private val playUrl = "https://gaze.red/play/7aa18ab9ec213c18d9bd8d5ce2f16d91"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WebView.setWebContentsDebuggingEnabled(true)

        val root = FrameLayout(this)
        webView = buildWebView()
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        player = buildPlayer()
        playerView = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            player = this@GazeWebProbeActivity.player
        }
        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, CAPTURE_JS, setOf("*"))
            Log.i(TAG, "document-start script installed")
        } else {
            Log.e(TAG, "DOCUMENT_START_SCRIPT unsupported")
        }

        webView.loadUrl(playUrl)
        handler.postDelayed(poll, 1500)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(this)
        wv.setBackgroundColor(Color.BLACK)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString =
                "Mozilla/5.0 (Linux; Android 12; Pixel 4a) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        wv.addJavascriptInterface(Bridge(), "GazeBridge")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(TAG, "onPageFinished $url")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "main frame error ${error?.errorCode}: ${error?.description}")
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                Log.i(TAG, "console ${msg?.message()}")
                return true
            }
        }
        return wv
    }

    private fun buildPlayer(): ExoPlayer {
        val client = TlsCompat.configure(OkHttpClient.Builder()).build()
        val httpFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .setDefaultRequestProperties(mapOf("Referer" to "https://gaze.red/"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
            )
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.i(TAG, "exo state=$state duration=$duration")
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "exo error ${error.errorCodeName}: ${error.message}", error)
                    }
                })
            }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (isFinishing) return
            webView.evaluateJavascript(STATE_JS) { raw -> handleState(parseJson(raw)) }
            handler.postDelayed(this, 1500)
        }
    }

    private fun handleState(state: JSONObject?) {
        if (state == null) {
            Log.w(TAG, "state null")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastLoggedAt > 5000) {
            lastLoggedAt = now
            Log.i(TAG, "state $state")
        }
        if (state.has("cap")) {
            clickCap(state.getJSONObject("cap"))
        }
        val ready = state.optBoolean("ready")
        val globalsReady =
            state.optString("fn") == "function" &&
                state.optString("meta") == "function" &&
                state.optString("mids") != "undefined" &&
                state.optString("kf") != "undefined"
        if (ready && globalsReady && playbackStarted.not() && now - lastResolveAt > 3000) {
            lastResolveAt = now
            resolveAttempts++
            webView.evaluateJavascript(RESOLVE_JS) { raw ->
                Log.i(TAG, "resolve #$resolveAttempts -> $raw")
            }
        }
    }

    private fun clickCap(cap: JSONObject) {
        val now = SystemClock.uptimeMillis()
        if (now - lastCapClickAt < 4000) return
        lastCapClickAt = now
        val density = resources.displayMetrics.density
        val x = (cap.optDouble("x") * density).toFloat()
        val y = (cap.optDouble("y") * density).toFloat()
        Log.i(TAG, "cap click at ($x, $y) css=(${cap.optDouble("x")}, ${cap.optDouble("y")})")
        dispatchTap(x, y)
    }

    private fun dispatchTap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0)
        webView.requestFocus()
        webView.dispatchTouchEvent(down)
        handler.postDelayed({ webView.dispatchTouchEvent(up) }, 80)
    }

    private fun parseJson(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            when (val value = org.json.JSONTokener(raw).nextValue()) {
                is JSONObject -> value
                is String -> JSONObject(value)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "parse failed: $e raw=${raw.take(200)}")
            null
        }
    }

    private fun startPlayback(playlist: String) {
        Log.i(TAG, "startPlayback len=${playlist.length} head=${playlist.take(120)}")
        webView.evaluateJavascript(
            "document.querySelectorAll('video').forEach(v=>{try{v.pause()}catch(e){}});",
            null
        )
        webView.visibility = View.GONE
        webView.onPause()
        playerView.visibility = View.VISIBLE

        val encoded = Base64.encodeToString(playlist.toByteArray(), Base64.NO_WRAP)
        player.setMediaItem(
            MediaItem.Builder()
                .setUri("data:application/vnd.apple.mpegurl;base64,$encoded")
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        )
        player.prepare()

        val logger = object : Runnable {
            var ticks = 0
            override fun run() {
                if (isFinishing || ticks >= 40) return
                Log.i(
                    TAG,
                    "t=${player.currentPosition} buf=${player.bufferedPosition} " +
                        "state=${player.playbackState} playing=${player.isPlaying}"
                )
                ticks++
                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(logger, 2000)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!playbackStarted) webView.onResume()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        webView.destroy()
        super.onDestroy()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onPlaylist(playlist: String) {
            Log.i(TAG, "bridge playlist len=${playlist.length}")
            runOnUiThread {
                if (!playbackStarted) {
                    playbackStarted = true
                    startPlayback(playlist)
                }
            }
        }
    }

    companion object {
        private const val TAG = "GazeWebProbe"

        private const val CAPTURE_JS = """
(function () {
  if (window.__gazeHookInstalled) return;
  window.__gazeHookInstalled = true;
  window.__gazePlaylist = null;
  var orig = URL.createObjectURL.bind(URL);
  URL.createObjectURL = function (b) {
    try {
      if (b && typeof b.text === 'function') {
        b.text().then(function (text) {
          if (text && text.indexOf('#EXTM3U') >= 0) {
            window.__gazePlaylist = text;
            console.log('GAZE_PLAYLIST ' + text.length);
          }
        }).catch(function () {});
      }
    } catch (e) {}
    return orig(b);
  };
})();
"""

        private const val STATE_JS = """
(function () {
  var r = {};
  var w = document.querySelector('cap-widget');
  if (w) {
    var b = w.getBoundingClientRect();
    if (b.width > 0 && b.height > 0) r.cap = { x: b.left + b.width / 2, y: b.top + b.height / 2 };
  }
  r.title = document.title;
  r.url = location.href;
  r.ready = !!(window.__gazePlaylist && window.__gazePlaylist.indexOf('#EXTM3U') >= 0);
  r.len = window.__gazePlaylist ? window.__gazePlaylist.length : 0;
  r.fn = typeof window._0x350ef8027;
  r.meta = typeof window.metaphysics;
  r.mids = typeof window.mids;
  r.kf = typeof window.killing_formalism;
  r.dpr = window.devicePixelRatio;
  r.video = document.querySelectorAll('video').length;
  return JSON.stringify(r);
})()
"""

        private const val RESOLVE_JS = """
(function () {
  try {
    if (window.__gazeSent) return JSON.stringify({ ok: true, sent: true });
    var raw = window.__gazePlaylist || '';
    var text = raw.replace(/^\s+/, '');
    if (text.indexOf('#EXTM3U') !== 0) return JSON.stringify({ ok: false, err: 'not m3u8' });
    if (typeof window._0x350ef8027 !== 'function' || typeof window.metaphysics !== 'function') {
      return JSON.stringify({ ok: false, err: 'globals missing' });
    }
    var key = decodeURIComponent(window.killing_formalism);
    var lines = text.split(/\r?\n/);
    var out = [];
    var resolved = 0, dropped = 0, hosts = {};
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i].trim();
      if (line.indexOf('!_') === 0) {
        var url = '';
        try {
          url = window._0x350ef8027(window.metaphysics(line, window.mids, key));
        } catch (e) { url = ''; }
        if (!url || !/^https?:\/\//.test(url)) {
          dropped++;
          if (out.length && out[out.length - 1].indexOf('#EXTINF') === 0) out.pop();
          continue;
        }
        resolved++;
        try {
          var h = new URL(url).host;
          hosts[h] = (hosts[h] || 0) + 1;
        } catch (e) {}
        out.push(url);
      } else if (line.length > 0) {
        out.push(line);
      }
    }
    var rewritten = out.join('\n');
    if (resolved < 20) {
      return JSON.stringify({ ok: false, err: 'too few segments', resolved: resolved, dropped: dropped });
    }
    window.__gazeSent = true;
    GazeBridge.onPlaylist(rewritten);
    return JSON.stringify({ ok: true, resolved: resolved, dropped: dropped, hosts: hosts, len: rewritten.length });
  } catch (e) {
    return JSON.stringify({ ok: false, err: String(e) });
  }
})()
"""
    }
}
