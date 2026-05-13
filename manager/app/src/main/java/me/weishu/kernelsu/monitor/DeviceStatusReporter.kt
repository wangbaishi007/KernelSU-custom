package me.weishu.kernelsu.monitor

import android.app.Application
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ksuApp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

private const val LOG_TAG = "KernelSU"

/**
 * Periodic POST of device / app / KernelSU state. Runs on a background coroutine; failures are logged only.
 */
object DeviceStatusReporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    fun start(application: Application) {
        if (!MonitorConfig.ENABLE_MONITOR) {
            Log.d(LOG_TAG, "[Monitor] disabled (ENABLE_MONITOR=false)")
            return
        }
        synchronized(this) {
            if (started) return
            started = true
        }
        Log.i(LOG_TAG, "[Monitor] start (interval=${MonitorConfig.INTERVAL_MS}ms)")
        scope.launch {
            while (isActive) {
                runCatching {
                    reportOnce(application)
                }.onFailure { e ->
                    Log.e(LOG_TAG, "[Monitor] report failed: ${e.message}", e)
                }
                delay(MonitorConfig.INTERVAL_MS)
            }
        }
    }

    private fun reportOnce(app: Application) {
        val url = "${MonitorConfig.BASE_URL.trimEnd('/')}/business/deviceStatus"
        val pkg = app.packageName
        val pInfo = app.packageManager.getPackageInfo(pkg, 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(pInfo)
        val versionName = pInfo.versionName.orEmpty()

        val (ksuStatus, rootGranted) = resolveKsuState(app)

        val json = JSONObject().apply {
            put("group", MonitorConfig.GROUP)
            put("packageName", pkg)
            put("appVersionName", versionName)
            put("appVersionCode", versionCode)
            put("deviceModel", Build.MODEL)
            put("brand", Build.BRAND)
            put("manufacturer", Build.MANUFACTURER)
            put("androidVersion", Build.VERSION.RELEASE.orEmpty())
            put("sdkInt", Build.VERSION.SDK_INT)
            put("kernelVersion", runCatching { Os.uname().release }.getOrElse { "" })
            put("ksuStatus", ksuStatus)
            put("isRootGranted", rootGranted)
            put("timestamp", System.currentTimeMillis())
        }

        val body = json.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        ksuApp.okhttpClient.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                Log.i(LOG_TAG, "[Monitor] report success (${resp.code})")
            } else {
                Log.w(LOG_TAG, "[Monitor] report failed: HTTP ${resp.code}")
            }
        }
    }

    /**
     * Uses JNI ([Natives]) only — does not open a root shell via libsu, so missing `libksud.so`
     * or blocked `su` will not spam logs during heartbeat.
     */
    private fun resolveKsuState(app: Application): Pair<String, Boolean> =
        runCatching {
            val ver = Natives.version
            if (!Natives.isKernelReady()) {
                return@runCatching "no_driver" to false
            }
            if (Natives.isSafeMode) {
                return@runCatching "safe_mode" to false
            }
            if (Natives.requireNewKernel()) {
                val p = Natives.getAppProfile(app.packageName, Process.myUid())
                return@runCatching "needs_kernel_update" to p.allowSu
            }
            val profile = Natives.getAppProfile(app.packageName, Process.myUid())
            val rootGranted = profile.allowSu
            val status = if (rootGranted) "enabled" else "manager_only"
            status to rootGranted
        }.getOrElse { e ->
            Log.w(LOG_TAG, "[Monitor] ksu state fallback: ${e.message}")
            "unknown" to false
        }
}
