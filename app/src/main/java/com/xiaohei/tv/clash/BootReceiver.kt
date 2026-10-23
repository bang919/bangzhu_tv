package com.xiaohei.tv.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaohei.tv.XiaoheiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val app = context.applicationContext as? XiaoheiApp ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = app.container.settings.data.first()
                if (settings.vpnEnabled &&
                    settings.autoStart &&
                    ClashController.vpnPermissionIntent(context) == null
                ) {
                    app.container.clash.connect(settings)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
