package me.weishu.kernelsu.monitor

/**
 * Monitoring: HTTP heartbeat ([DeviceStatusReporter]) and optional Sekiro ([SekiroWebSocketClient]).
 * Toggle [ENABLE_MONITOR] / [ENABLE_SEKIRO] / [ENABLE_PORTAL_LOGIN] for builds that must not use a feature.
 */
object MonitorConfig {
    /**
     * When true, [MainActivity] requires portal session before entering the app; monitors start only after login
     * (oss-uploader: GET/POST `/auth/login` + `session` cookie).
     */
    const val ENABLE_PORTAL_LOGIN = true

    /** Base URL only (no path), e.g. `https://quanminzhuan.club` — same as oss-uploader LoginApi. */
    const val PORTAL_LOGIN_BASE_URL = "https://quanminzhuan.club"

    /** Path for HTML login form + POST. */
    const val PORTAL_LOGIN_PATH = "/auth/login"

    const val ENABLE_MONITOR = true

    /** Sekiro WebSocket registration (JSON RPC over WS). Independent of HTTP heartbeat. */
    const val ENABLE_SEKIRO = true

    /** No trailing slash; paths are appended in [DeviceStatusReporter] / [SekiroWebSocketClient]. */
    const val BASE_URL = "http://49.234.20.57:5612"

    const val GROUP = "kssss"
    const val INTERVAL_MS = 60_000L

    /**
     * WebSocket path (query `group` + `clientId` appended by client).
     * If your server uses a demo prefix, e.g. `/business-demo/register`.
     */
    const val SEKIRO_REGISTER_PATH = "/business/register"

    /** Delay before reconnect after close / failure (ms). */
    const val SEKIRO_RECONNECT_MS = 2_000L
}
