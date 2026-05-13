package me.weishu.kernelsu.monitor

import android.app.Application
import android.util.Log
import me.weishu.kernelsu.auth.portal.PortalAuth

private const val LOG_TAG = "KernelSU"

/** Starts HTTP heartbeat + Sekiro only when portal login is disabled or session exists. */
object MonitorBootstrap {

    fun startIfEligible(app: Application) {
        if (MonitorConfig.ENABLE_PORTAL_LOGIN && !PortalAuth.isLoggedIn(app)) {
            Log.d(LOG_TAG, "[Monitor] skip bootstrap until portal login")
            return
        }
        DeviceStatusReporter.start(app)
        SekiroWebSocketClient.start(app)
    }
}
