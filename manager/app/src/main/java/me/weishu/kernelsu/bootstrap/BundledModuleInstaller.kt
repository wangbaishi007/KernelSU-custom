package me.weishu.kernelsu.bootstrap

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ui.util.installModuleZipFromFile
import me.weishu.kernelsu.ui.util.rootAvailable
import java.io.File

/**
 * Drop KernelSU module .zip files under assets/bundled_modules/ (see README.txt).
 * On first successful launch (driver + root), installs each zip once via ksud module install.
 */
object BundledModuleConfig {
    const val ENABLE = true

    const val ASSET_DIR = "bundled_modules"

    private const val PREFS = "bundled_modules_bootstrap"
    private const val KEY_DONE = "completed"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBootstrapDone(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DONE, false)

    fun markBootstrapDone(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_DONE, true).apply()
    }
}

object BundledModuleInstaller {

    private const val TAG = "BundledModules"

    fun schedule(activity: ComponentActivity) {
        if (!BundledModuleConfig.ENABLE) return
        activity.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { runOnce(activity) }
                .onFailure { Log.e(TAG, "bootstrap failed", it) }
        }
    }

    private fun runOnce(context: Context) {
        if (BundledModuleConfig.isBootstrapDone(context)) return

        if (!Natives.isKernelReady()) {
            Log.d(TAG, "skip: kernel driver not available")
            return
        }
        if (Natives.requireNewKernel()) {
            Log.d(TAG, "skip: kernel too old for manager")
            return
        }
        if (!rootAvailable()) {
            Log.d(TAG, "skip: no root shell yet (will retry next launch)")
            return
        }

        val names =
            try {
                context.assets.list(BundledModuleConfig.ASSET_DIR) ?: emptyArray()
            } catch (e: Exception) {
                Log.w(TAG, "asset dir missing: ${BundledModuleConfig.ASSET_DIR}", e)
                BundledModuleConfig.markBootstrapDone(context)
                return
            }

        val zips = names.filter { it.endsWith(".zip", ignoreCase = true) }.sorted()
        if (zips.isEmpty()) {
            Log.i(TAG, "no .zip under assets/${BundledModuleConfig.ASSET_DIR}, marking done")
            BundledModuleConfig.markBootstrapDone(context)
            return
        }

        val cacheDir = context.cacheDir
        for (name in zips) {
            val tmp = File(cacheDir, "bundled_install_${System.currentTimeMillis()}_$name")
            runCatching {
                context.assets.open("${BundledModuleConfig.ASSET_DIR}/$name").use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                installModuleZipFromFile(
                    tmp,
                    onStdout = { line -> Log.i(TAG, "[$name] $line") },
                    onStderr = { line -> Log.w(TAG, "[$name] $line") },
                )
            }.onFailure { Log.e(TAG, "install failed: $name", it) }
            runCatching { tmp.delete() }
        }

        BundledModuleConfig.markBootstrapDone(context)
        Log.i(TAG, "bundled module bootstrap finished (${zips.size} zip(s))")
    }
}
