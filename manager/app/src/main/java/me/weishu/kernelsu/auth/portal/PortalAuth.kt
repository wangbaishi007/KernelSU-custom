package me.weishu.kernelsu.auth.portal

import android.content.Context

/** Portal session: same semantics as oss-uploader [com.oss.uploader.auth.AuthHelper]. */
object PortalAuth {

    fun isLoggedIn(context: Context): Boolean =
        CookieStoreManager.getInstance(context).isLoggedIn()

    fun logout(context: Context) {
        CookieStoreManager.getInstance(context).logout()
    }

    fun getCookieHeader(context: Context): String =
        CookieStoreManager.getInstance(context).getCookieHeader()
}
