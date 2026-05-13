package me.weishu.kernelsu.auth.portal

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * OkHttp [CookieJar] backed by [CookieStoreManager] (aligned with oss-uploader PersistentCookieJar).
 */
class PersistentCookieJar(
    private val cookieManager: CookieStoreManager,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val lines = cookies.mapNotNull { c -> "${c.name}=${c.value}" }
        if (lines.isNotEmpty()) {
            cookieManager.saveCookies(lines)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val out = ArrayList<Cookie>()
        for (s in cookieManager.getAllCookies()) {
            val idx = s.indexOf('=')
            if (idx <= 0) continue
            val name = s.substring(0, idx).trim()
            val value = if (idx < s.length - 1) s.substring(idx + 1).trim() else ""
            runCatching {
                out.add(
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(url.host)
                        .path("/")
                        .build(),
                )
            }
        }
        return out
    }
}
