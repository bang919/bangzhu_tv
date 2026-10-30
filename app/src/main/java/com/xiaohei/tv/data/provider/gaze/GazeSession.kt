package com.xiaohei.tv.data.provider.gaze

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * gaze.red 的 WebView 会话：页面在 Compose 内容后方常驻，
 * 负责绕过 CAP、读取渲染后的 DOM，并捕获 hls.js 生成的 m3u8。
 */
@SuppressLint("SetJavaScriptEnabled")
class GazeSession(private val context: Context) {
    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var attachedActivity: WeakReference<Activity>? = null
    private var lastCapClickAt = 0L
    private var filterUrl: String? = null
    private var filterPage = 0

    @Volatile
    private var pendingPlaylist: String? = null

    fun attach(activity: Activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { attach(activity) }
            return
        }
        if (attachedActivity?.get() === activity && webView != null) return
        destroyWebView()
        val view = createWebView(activity)
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: run {
            Log.e(TAG, "content view missing, gaze session not attached")
            view.destroy()
            return
        }
        root.addView(
            view,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        webView = view
        attachedActivity = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (attachedActivity?.get() !== activity) return
        destroyWebView()
    }

    fun pause() {
        webView?.onPause()
    }

    fun resume() {
        webView?.onResume()
    }

    suspend fun loadHtml(
        url: String,
        readyJs: String,
        timeoutMs: Long = 40_000,
        timeoutMessage: String = "视频源页面加载超时，请重试",
    ): String = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val view = requireWebView()
            filterUrl = null
            filterPage = 0
            view.loadUrl(url)
            awaitReady(view, urlReadyJs(url, readyJs), timeoutMs, timeoutMessage)
            val raw = evaluate(view, "document.documentElement.outerHTML")
            decodeJsString(raw) ?: throw IOException("视频源页面读取失败，请重试")
        }
    }

    suspend fun loadFilterPage(
        url: String,
        page: Int,
        timeoutMs: Long = 45_000,
    ): String = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val view = requireWebView()
            if (filterUrl != url) {
                view.loadUrl(url)
                awaitReady(
                    view,
                    urlReadyJs(url, FILTER_READY_JS),
                    35_000,
                    "视频源列表加载超时，请重试",
                )
                filterUrl = url
                filterPage = 1
            }
            var guard = 0
            while (filterPage != page) {
                if (guard++ >= 16) throw IOException("无法加载第 $page 页，请重试")
                val clicked = decodeJsString(evaluate(view, pageClickJs(page)))?.toIntOrNull()
                if (clicked == null || clicked <= 0) {
                    throw IOException("无法加载第 $page 页，请重试")
                }
                delay(350)
                awaitReady(
                    view,
                    pageReadyJs(clicked),
                    timeoutMs,
                    "第 $clicked 页加载超时，请重试",
                )
                filterPage = clicked
            }
            awaitImages(view)
            val raw = evaluate(view, "document.documentElement.outerHTML")
            decodeJsString(raw) ?: throw IOException("视频源页面读取失败，请重试")
        }
    }

    private suspend fun awaitImages(view: WebView, timeoutMs: Long = 8_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(view, IMAGES_READY_JS) == "true") return
            delay(250)
        }
    }

    suspend fun loadDetailPage(videoId: String): String = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val view = requireWebView()
            filterUrl = null
            filterPage = 0
            val detailUrl = "$BASE_URL/play/$videoId"
            view.loadUrl(detailUrl)
            awaitReady(
                view,
                urlReadyJs(detailUrl, DETAIL_READY_JS),
                35_000,
                "视频源详情页加载超时，请重试",
            )
            val raw = evaluate(view, "document.documentElement.outerHTML")
            decodeJsString(raw) ?: throw IOException("视频源页面读取失败，请重试")
        }
    }

    suspend fun capturePlaylist(
        videoId: String,
        group: Int,
        episodeIndex: Int,
    ): String = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val view = requireWebView()
            filterUrl = null
            filterPage = 0
            pendingPlaylist = null
            val detailUrl = "$BASE_URL/play/$videoId"
            view.loadUrl(detailUrl)
            awaitReady(
                view,
                urlReadyJs(detailUrl, DETAIL_READY_JS),
                35_000,
                "视频源播放页加载超时，请重试",
            )

            val tabSelector = "#play-source-tab-g$group"
            val tabActive = evaluate(
                view,
                "!!(document.querySelector('$tabSelector') && document.querySelector('$tabSelector').classList.contains('active'))",
            ) == "true"
            if (!tabActive) {
                val tabFound = evaluate(
                    view,
                    "!!(function(){var t=document.querySelector('$tabSelector');if(!t)return false;t.click();return true;})()",
                ) == "true"
                if (!tabFound) throw IOException("播放线路不存在，请切换线路重试")
                delay(500)
            }

            val buttons = "#play-source-panel-g$group .playbtn"
            awaitReady(
                view,
                "document.querySelectorAll('$buttons').length > $episodeIndex",
                15_000,
                "线路加载超时，请切换线路重试",
            )

            val active = evaluate(
                view,
                "!!(document.querySelectorAll('$buttons')[$episodeIndex] && document.querySelectorAll('$buttons')[$episodeIndex].classList.contains('jg'))",
            ) == "true"
            val captured = if (active) {
                awaitPlaylist(view, 12_000) ||
                    (clearAndClick(view, buttons, episodeIndex) && awaitPlaylist(view, 15_000))
            } else {
                clearAndClick(view, buttons, episodeIndex) && awaitPlaylist(view, 15_000)
            }
            if (!captured) throw IOException("视频源没有返回播放地址，请切换线路重试")

            val stats = parseJsObject(evaluate(view, RESOLVE_JS))
            if (stats?.optBoolean("ok") != true) {
                throw IOException(stats?.optString("err")?.takeIf { it.isNotBlank() } ?: "解析播放地址失败，请重试")
            }
            val playlist = pendingPlaylist?.takeIf { it.length > 2_000 }
                ?: throw IOException("解析播放地址失败，请重试")
            evaluate(
                view,
                "document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}});true",
            )
            playlist
        }
    }

    private suspend fun clearAndClick(view: WebView, selector: String, index: Int): Boolean {
        pendingPlaylist = null
        evaluate(view, "window.__gazePlaylist=null;window.__gazeSent=false;")
        return evaluate(
            view,
            "!!(function(){var b=document.querySelectorAll('$selector')[$index];if(!b)return false;" +
                "b.scrollIntoView({block:'center'});b.click();return true;})()",
        ) == "true"
    }

    private suspend fun awaitPlaylist(view: WebView, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val state = pollState(view, "!!(window.__gazePlaylist && window.__gazePlaylist.indexOf('#EXTM3U')>=0)")
            if (state?.optBoolean("ok") == true) return true
            delay(300)
        }
        return false
    }

    private suspend fun awaitReady(
        view: WebView,
        readyJs: String,
        timeoutMs: Long,
        timeoutMessage: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val state = pollState(view, readyJs)
            if (state?.optBoolean("ok") == true) return
            delay(300)
        }
        throw IOException(timeoutMessage)
    }

    private suspend fun pollState(view: WebView, readyJs: String): JSONObject? {
        val raw = evaluate(view, stateJs(readyJs))
        val json = decodeJsString(raw) ?: return null
        val state = runCatching { JSONObject(json) }.getOrNull() ?: return null
        state.optJSONObject("cap")?.let(::clickCap)
        return state
    }

    private fun clickCap(cap: JSONObject) {
        val now = SystemClock.uptimeMillis()
        if (now - lastCapClickAt < 3_500) return
        lastCapClickAt = now
        val density = webView?.resources?.displayMetrics?.density ?: 1f
        val x = (cap.optDouble("x") * density).toFloat()
        val y = (cap.optDouble("y") * density).toFloat()
        Log.i(TAG, "cap tap at ($x, $y)")
        val downTime = SystemClock.uptimeMillis()
        val view = webView ?: return
        view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        main.postDelayed(
            {
                view.dispatchTouchEvent(
                    MotionEvent.obtain(downTime, downTime + 90, MotionEvent.ACTION_UP, x, y, 0)
                )
            },
            90,
        )
    }

    private suspend fun evaluate(view: WebView, js: String): String? =
        withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { continuation ->
                view.evaluateJavascript(js) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        }

    private fun requireWebView(): WebView =
        webView ?: throw IOException("视频源页面尚未准备好，请重试")

    private fun destroyWebView() {
        val view = webView ?: return
        webView = null
        attachedActivity = null
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        runCatching { view.stopLoading() }
        runCatching { view.destroy() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(activity: Activity): WebView {
        val view = WebView(activity)
        view.setBackgroundColor(Color.BLACK)
        view.alpha = 0.01f
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = USER_AGENT
        }
        view.addJavascriptInterface(Bridge(), "GazeBridge")
        view.webViewClient = WebViewClient()
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                val text = message?.message().orEmpty()
                if (text.startsWith("GAZE_")) Log.i(TAG, "console $text")
                return true
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, CAPTURE_JS, setOf("*"))
        } else {
            Log.e(TAG, "DOCUMENT_START_SCRIPT unsupported; playlist capture unavailable")
        }
        return view
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onPlaylist(playlist: String) {
            pendingPlaylist = playlist
        }
    }

    private fun parseJsObject(raw: String?): JSONObject? {
        val text = decodeJsString(raw) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching {
            when (val value = JSONTokener(raw).nextValue()) {
                is String -> value
                is JSONObject -> value.toString()
                else -> null
            }
        }.getOrNull()
    }

    private fun urlReadyJs(url: String, readyJs: String): String = """
(function () {
  var target = ${JSONObject.quote(url)};
  var href = location.href;
  if (href !== target && href.indexOf(target + '&') !== 0 &&
      href.indexOf(target + '#') !== 0 && href !== target + '/') return false;
  return !!($readyJs);
})()
"""

    private fun stateJs(readyJs: String): String = """
(function () {
  var r = {};
  var w = document.querySelector('cap-widget');
  if (w) {
    var b = w.getBoundingClientRect();
    if (b.width > 0 && b.height > 0) r.cap = { x: b.left + b.width / 2, y: b.top + b.height / 2 };
  }
  r.title = document.title || '';
  r.url = location.href;
  r.ok = !!($readyJs);
  return JSON.stringify(r);
})()
"""

    private fun pageClickJs(page: Int): String = """
(function () {
  var nav = document.querySelector('#pagination2');
  if (!nav) return '0';
  var target = $page;
  var buttons = Array.prototype.slice.call(nav.querySelectorAll('button[data-page]'));
  var pick = null, below = null, above = null;
  buttons.forEach(function (button) {
    var value = Number(button.dataset.page);
    if (!value || button.disabled) return;
    if (value === target) { pick = button; return; }
    if (value < target && (!below || value > Number(below.dataset.page))) below = button;
    if (value > target && (!above || value < Number(above.dataset.page))) above = button;
  });
  pick = pick || below || above;
  if (!pick) return '0';
  pick.click();
  return String(pick.dataset.page);
})()
"""

    private fun pageReadyJs(page: Int): String = """
(function () {
  if (document.readyState === 'loading') return false;
  var nav = document.querySelector('#pagination2');
  var mains = document.querySelector('#mains');
  var empty = document.querySelector('.jA');
  if (!nav || !mains || !empty) return false;
  if (mains.classList.contains('is-loading')) return false;
  var label = nav.getAttribute('aria-label') || '';
  if (label.indexOf('当前第 $page 页') < 0) return false;
  return mains.querySelectorAll('article.eP').length > 0 || !empty.hidden;
})()
"""

    companion object {
        private const val TAG = "GazeSession"
        const val BASE_URL = "https://gaze.red"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Pixel 4a) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val FILTER_READY_JS = """
(function () {
  if (document.readyState === 'loading') return false;
  var t = document.title || '';
  if (!t || t.indexOf('访问验证') >= 0) return false;
  var mains = document.querySelector('#mains');
  var empty = document.querySelector('.jA');
  if (!mains || !empty) return false;
  if (mains.classList.contains('is-loading')) return false;
  return mains.querySelectorAll('article.eP').length > 0 || !empty.hidden;
})()
"""

        private const val DETAIL_READY_JS = """
(function () {
  var t = document.title || '';
  if (!t || t.indexOf('访问验证') >= 0) return false;
  if (!document.querySelector('.eV h5.lb')) return false;
  return document.readyState !== 'loading';
})()
"""

        private const val IMAGES_READY_JS = """
(function () {
  var imgs = document.querySelectorAll('#mains article.eP img');
  if (!imgs.length) return true;
  var done = 0;
  for (var i = 0; i < imgs.length; i++) {
    var src = imgs[i].getAttribute('src') || '';
    var lazy = imgs[i].getAttribute('data-src') || '';
    if (lazy || (src && src.indexOf('/static/img/svg/') < 0)) done++;
  }
  return done >= Math.ceil(imgs.length * 0.9);
})()
"""

        const val HOME_READY_JS = """
(function () {
  var t = document.title || '';
  if (!t || t.indexOf('访问验证') >= 0) return false;
  return document.querySelectorAll('article.eP').length > 3;
})()
"""

        private const val CAPTURE_JS = """
(function () {
  if (window.__gazeHookInstalled) return;
  window.__gazeHookInstalled = true;
  window.__gazePlaylist = null;
  document.addEventListener('play', function (e) {
    try { e.target.muted = true; } catch (err) {}
  }, true);
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

        private const val RESOLVE_JS = """
(function () {
  try {
    var raw = window.__gazePlaylist || '';
    var text = raw.replace(/^\s+/, '');
    if (text.indexOf('#EXTM3U') !== 0) return JSON.stringify({ ok: false, err: '未捕获到播放列表' });
    if (typeof window._0x350ef8027 !== 'function' || typeof window.metaphysics !== 'function') {
      return JSON.stringify({ ok: false, err: '播放页解析组件未就绪' });
    }
    var key = decodeURIComponent(window.killing_formalism);
    var lines = text.split(/\r?\n/);
    var out = [];
    var resolved = 0, dropped = 0;
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
        out.push(url);
      } else if (line.length > 0) {
        out.push(line);
      }
    }
    if (resolved < 20) {
      return JSON.stringify({ ok: false, err: '播放地址解析失败，请切换线路重试' });
    }
    var rewritten = out.join('\n');
    window.__gazeSent = true;
    GazeBridge.onPlaylist(rewritten);
    return JSON.stringify({ ok: true, resolved: resolved, dropped: dropped, len: rewritten.length });
  } catch (e) {
    return JSON.stringify({ ok: false, err: String(e) });
  }
})()
"""
    }
}
