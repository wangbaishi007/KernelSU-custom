package me.weishu.kernelsu.auth.portal

import android.util.Log
import me.weishu.kernelsu.monitor.MonitorConfig
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "PortalLoginApi"

private val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

data class LoginPageResult(
    val csrfToken: String?,
    val setCookieHeaders: List<String>,
)

data class PortalLoginResult(
    val success: Boolean,
    val message: String,
)

/**
 * Same steps as oss-uploader [com.oss.uploader.auth.api.LoginApi]:
 * GET login page -> csrf + Set-Cookie; POST form with username/password/csrf_token.
 */
class PortalLoginApi(
    private val cookieManager: CookieStoreManager,
) {

    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cookieJar(PersistentCookieJar(cookieManager))
            .followRedirects(false)
            .build()

    fun fetchLoginPage(): LoginPageResult {
        val url = (MonitorConfig.PORTAL_LOGIN_BASE_URL.trimEnd('/') + MonitorConfig.PORTAL_LOGIN_PATH).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid login URL")

        val rb =
            Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")

        val cookieHeader = cookieManager.getCookieHeader()
        if (cookieHeader.isNotEmpty()) {
            rb.header("Cookie", cookieHeader)
        }

        client.newCall(rb.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Fetch login page failed: ${response.code}")
            }
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                cookieManager.saveCookies(setCookies)
            }
            val html = response.body?.string().orEmpty()
            val csrf = CsrfTokenParser.parse(html)
            Log.d(TAG, "csrf_token: ${if (csrf != null) "yes" else "no"}, set-cookie: ${setCookies.size}")
            return LoginPageResult(csrf, setCookies)
        }
    }

    fun login(username: String, password: String, csrfToken: String?): PortalLoginResult {
        if (csrfToken.isNullOrEmpty()) {
            return PortalLoginResult(false, "无法获取 csrf_token，请重试")
        }

        val url = (MonitorConfig.PORTAL_LOGIN_BASE_URL.trimEnd('/') + MonitorConfig.PORTAL_LOGIN_PATH).toHttpUrlOrNull()
            ?: return PortalLoginResult(false, "无效的登录地址")

        val body =
            FormBody.Builder()
                .add("csrf_token", csrfToken)
                .add("username", username)
                .add("password", password)
                .add("submit", "登录")
                .build()

        val rb =
            Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", MonitorConfig.PORTAL_LOGIN_BASE_URL.trimEnd('/'))
                .header("Referer", MonitorConfig.PORTAL_LOGIN_BASE_URL.trimEnd('/') + MonitorConfig.PORTAL_LOGIN_PATH)

        val cookieHeader = cookieManager.getCookieHeader()
        if (cookieHeader.isNotEmpty()) {
            rb.header("Cookie", cookieHeader)
        }

        client.newCall(rb.build()).execute().use { response ->
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                cookieManager.saveCookies(setCookies)
            }
            val code = response.code
            val html = response.body?.string().orEmpty()
            val location = response.header("Location")
            val success = isLoginSuccess(code, location, html)
            Log.d(TAG, "Login response: code=$code, redirect=$location, success=$success")
            return if (success) {
                PortalLoginResult(true, "登录成功")
            } else {
                val msg = extractErrorMessage(html) ?: "登录失败，请检查用户名和密码"
                PortalLoginResult(false, msg)
            }
        }
    }

    private fun isLoginSuccess(code: Int, location: String?, html: String): Boolean {
        if (code == 302 && location != null) {
            if (!location.contains("/auth/login")) {
                Log.d(TAG, "Login success: 302 to $location")
                return true
            }
        }
        if (code == 200) {
            if (html.contains("登录") && html.contains("loginForm")) {
                return false
            }
            if (html.contains("logout") || html.contains("退出") || html.contains("用户")) {
                Log.d(TAG, "Login success: page contains user info")
                return true
            }
        }
        return false
    }

    private fun extractErrorMessage(html: String): String? {
        if (html.contains("用户名或密码错误")) return "用户名或密码错误"
        if (html.contains("Invalid")) return "用户名或密码错误"
        return null
    }
}
