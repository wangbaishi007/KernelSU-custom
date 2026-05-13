package me.weishu.kernelsu.auth.portal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Persists session cookies for the portal login flow (aligned with oss-uploader CookieStoreManager).
 */
class CookieStoreManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = ReentrantLock()
    private val cookieSet: MutableSet<String> = HashSet()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val saved = prefs.getStringSet(KEY_COOKIES, null) ?: return
        lock.withLock {
            cookieSet.clear()
            cookieSet.addAll(saved)
        }
        Log.d(TAG, "Loaded ${saved.size} cookies from prefs")
    }

    private fun saveToPrefs() {
        val toSave = lock.withLock { HashSet(cookieSet) }
        prefs.edit().putStringSet(KEY_COOKIES, toSave).apply()
    }

    fun saveCookies(setCookieHeaders: List<String>?) {
        if (setCookieHeaders.isNullOrEmpty()) return
        lock.withLock {
            for (header in setCookieHeaders) {
                val cookie = parseCookieFromSetCookie(header) ?: continue
                val eq = cookie.indexOf('=')
                if (eq <= 0) continue
                val name = cookie.substring(0, eq).trim()
                if (name.isEmpty()) continue
                removeCookieByNameUnlocked(name)
                cookieSet.add(cookie)
            }
        }
        saveToPrefs()
        val total = lock.withLock { cookieSet.size }
        Log.d(TAG, "Saved ${setCookieHeaders.size} cookies, total=$total")
    }

    private fun removeCookieByNameUnlocked(name: String) {
        cookieSet.removeAll { it.startsWith("$name=") }
    }

    private fun parseCookieFromSetCookie(setCookie: String?): String? {
        if (setCookie == null) return null
        val idx = setCookie.indexOf(';')
        return if (idx > 0) setCookie.substring(0, idx).trim() else setCookie.trim()
    }

    fun getCookieHeader(): String =
        lock.withLock {
            if (cookieSet.isEmpty()) ""
            else cookieSet.joinToString("; ")
        }

    /** Match oss-uploader: presence of `session=` cookie means logged in. */
    fun isLoggedIn(): Boolean =
        lock.withLock {
            cookieSet.any { it.startsWith("session=") }
        }

    fun logout() {
        lock.withLock { cookieSet.clear() }
        saveToPrefs()
        Log.d(TAG, "Logged out, cookies cleared")
    }

    fun getAllCookies(): List<String> =
        lock.withLock { Collections.unmodifiableList(cookieSet.toList()) }

    companion object {
        private const val TAG = "PortalCookieStore"
        private const val PREFS_NAME = "login_cookies"
        private const val KEY_COOKIES = "cookies"

        @Volatile
        private var instance: CookieStoreManager? = null

        fun getInstance(context: Context): CookieStoreManager {
            instance?.let { return it }
            synchronized(this) {
                return instance ?: CookieStoreManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
