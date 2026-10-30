package com.xiaohei.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
class GazeMetaProbeActivity : Activity() {
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var lastClickAt = 0L
    private var step = 0
    private var settledFor = 0
    private var lastTitle = ""

    private val urls = listOf(
        "filter" to "https://gaze.red/filter?mform=1",
        "search" to "https://gaze.red/filter?search=%E8%82%96%E7%94%B3%E5%85%8B%E7%9A%84%E6%95%91%E8%B5%8E",
        "series" to "https://gaze.red/play/c3b353267a46fcd93c38388fd15eb5c9",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WebView.setWebContentsDebuggingEnabled(true)
        val root = FrameLayout(this)
        webView = buildWebView()
        root.addView(webView, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)
        loadStep()
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
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                Log.i(TAG, "console ${msg?.message()}")
                return true
            }
        }
        return wv
    }

    private fun loadStep() {
        if (step >= urls.size) {
            Log.i(TAG, "all steps done")
            return
        }
        val (name, url) = urls[step]
        Log.i(TAG, "load step=$name url=$url")
        settledFor = 0
        lastTitle = ""
        webView.loadUrl(url)
    }

    private val poll = object : Runnable {
        override fun run() {
            webView.evaluateJavascript(STATE_JS) { raw ->
                handle(raw)
            }
            handler.postDelayed(this, 1500)
        }
    }

    private fun handle(raw: String?) {
        val state = runCatching {
            JSONObject(org.json.JSONTokener(raw ?: "null").nextValue() as? String ?: "{}")
        }.getOrNull() ?: return
        val now = SystemClock.uptimeMillis()
        if (state.has("cap")) {
            val cap = state.getJSONObject("cap")
            if (now - lastClickAt > 4000) {
                lastClickAt = now
                val d = state.optDouble("dpr", 2.0)
                val x = (cap.optDouble("x") * d).toFloat()
                val y = (cap.optDouble("y") * d).toFloat()
                Log.i(TAG, "cap click ($x,$y)")
                tap(x, y)
            }
        }
        val title = state.optString("title")
        if (title != lastTitle) {
            lastTitle = title
            Log.i(TAG, "title=$title articles=${state.optInt("articles")} loc=${state.optString("url")}")
        }
        val ready = title.isNotBlank() && !title.contains("访问验证") &&
            (state.optInt("articles") > 0 || state.optBoolean("empty") || state.optString("pageTitle").isNotBlank())
        if (ready) {
            settledFor++
            if (settledFor == 3) {
                val name = urls[step].first
                webView.evaluateJavascript(
                    "document.documentElement.outerHTML",
                ) { html ->
                    val text = runCatching {
                        org.json.JSONTokener(html ?: "null").nextValue() as? String
                    }.getOrNull()
                    if (text != null) {
                        val file = File(getExternalFilesDir(null), "probe_$name.html")
                        file.writeText(text)
                        Log.i(TAG, "saved $name size=${text.length} articles=${state.optInt("articles")}")
                    }
                    step++
                    loadStep()
                }
            }
        }
    }

    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        webView.requestFocus()
        webView.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        handler.postDelayed({
            webView.dispatchTouchEvent(MotionEvent.obtain(t, t + 80, MotionEvent.ACTION_UP, x, y, 0))
        }, 80)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun log(msg: String) {
            Log.i(TAG, "js: $msg")
        }
    }

    companion object {
        private const val TAG = "GazeMetaProbe"

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
  r.dpr = window.devicePixelRatio;
  r.articles = document.querySelectorAll('article').length;
  r.empty = !!document.querySelector('.empty, .empty-state, [data-empty]');
  r.pageTitle = (document.querySelector('h1') || {}).innerText || '';
  r.playbtns = document.querySelectorAll('.playbtn').length;
  r.groups = document.querySelectorAll('[data-source-group]').length;
  r.pager = document.querySelectorAll('[data-page]').length;
  return JSON.stringify(r);
})()
"""
    }
}
