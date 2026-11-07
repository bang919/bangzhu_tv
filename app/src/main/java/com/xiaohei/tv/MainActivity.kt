package com.xiaohei.tv

import android.os.Bundle
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaohei.tv.ui.XiaoheiTvApp
import com.xiaohei.tv.ui.theme.XiaoheiTheme

// Compose's TV default pivots the focused item to 30% of the viewport, which makes rows jump
// on every focus move; use minimal scrolling instead.
private val MinimalBringIntoViewSpec = object : BringIntoViewSpec {}

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    internal var keyEventObserver: ((keyCode: Int, repeatCount: Int) -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            keyEventObserver?.invoke(event.keyCode, event.repeatCount)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        requestedOrientation = if (isTv) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, !isTv)
        if (isTv) WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            XiaoheiTheme {
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides MinimalBringIntoViewSpec,
                ) {
                    XiaoheiTvApp(viewModel())
                }
            }
        }
        (application as XiaoheiApp).container.gaze.attach(this)
    }

    override fun onResume() {
        super.onResume()
        (application as XiaoheiApp).container.gaze.resume()
    }

    override fun onPause() {
        (application as XiaoheiApp).container.gaze.pause()
        super.onPause()
    }

    override fun onDestroy() {
        (application as XiaoheiApp).container.gaze.detach(this)
        super.onDestroy()
    }
}
