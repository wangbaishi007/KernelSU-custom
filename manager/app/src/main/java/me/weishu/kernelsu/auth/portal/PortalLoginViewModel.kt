package me.weishu.kernelsu.auth.portal

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R

data class PortalLoginUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val savedUsername: String = "",
)

class PortalLoginViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PortalLoginPrefs.PREFS_NAME, Application.MODE_PRIVATE)

    private val cookieManager = CookieStoreManager.getInstance(application)
    private val loginApi = PortalLoginApi(cookieManager)

    private val _uiState =
        MutableStateFlow(
            PortalLoginUiState(savedUsername = prefs.getString(PortalLoginPrefs.KEY_LAST_USERNAME, "").orEmpty()),
        )
    val uiState: StateFlow<PortalLoginUiState> = _uiState.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun login(username: String, password: String) {
        val app = getApplication<Application>()
        val u = username.trim()
        val err =
            when {
                u.isEmpty() -> app.getString(R.string.portal_login_error_username_required)
                u.length < 2 -> app.getString(R.string.portal_login_error_username_short)
                password.isEmpty() -> app.getString(R.string.portal_login_error_password_required)
                else -> null
            }
        if (err != null) {
            _uiState.update { it.copy(loading = false, errorMessage = err) }
            return
        }
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val page = loginApi.fetchLoginPage()
                        val csrf = page.csrfToken
                        if (csrf.isNullOrEmpty()) {
                            PortalLoginResult(false, "获取登录页失败，请重试")
                        } else {
                            loginApi.login(u, password, csrf)
                        }
                    }.getOrElse { e ->
                        PortalLoginResult(false, "网络错误: ${e.message}")
                    }
                }
            if (result.success) {
                prefs.edit().putString(PortalLoginPrefs.KEY_LAST_USERNAME, u).apply()
                _uiState.update {
                    it.copy(loading = false, errorMessage = null, savedUsername = u)
                }
                _loginSuccess.value = true
            } else {
                _uiState.update {
                    it.copy(loading = false, errorMessage = result.message)
                }
            }
        }
    }
}
