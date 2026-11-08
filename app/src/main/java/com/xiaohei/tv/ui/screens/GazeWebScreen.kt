package com.xiaohei.tv.ui.screens

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.view.View
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xiaohei.tv.MainActivity
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.components.AutoFocus
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONTokener

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GazeWebScreen(videoId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val isTv = (context as? com.xiaohei.tv.MainActivity)?.isTvDevice == true
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var loading by remember(videoId) { mutableStateOf(true) }
    fun closeFullscreen() {
        (fullscreenView?.parent as? ViewGroup)?.removeView(fullscreenView)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        activity?.requestedOrientation = if (isTv) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, !isTv)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                if (isTv) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler {
        when {
            fullscreenView != null -> closeFullscreen()
            webView?.canGoBack() == true -> webView?.goBack()
            else -> onBack()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (fullscreenView != null) closeFullscreen()
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                            if (fullscreenView != null) { callback.onCustomViewHidden(); return }
                            fullscreenView = view
                            fullscreenCallback = callback
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            activity?.window?.let { window ->
                                WindowCompat.setDecorFitsSystemWindows(window, false)
                                WindowInsetsControllerCompat(window, window.decorView).apply {
                                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    hide(WindowInsetsCompat.Type.systemBars())
                                }
                                window.addContentView(view, FrameLayout.LayoutParams(-1, -1))
                            }
                        }
                        override fun onHideCustomView() { closeFullscreen() }
                    }
                    webView = this
                    if (videoId.matches(Regex("[0-9a-fA-F]{16,}"))) loadUrl("https://gaze.red/play/$videoId")
                }
            },
        )
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
    }
}
