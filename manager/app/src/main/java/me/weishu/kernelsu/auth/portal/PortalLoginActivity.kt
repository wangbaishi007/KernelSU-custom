package me.weishu.kernelsu.auth.portal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.R
import me.weishu.kernelsu.monitor.MonitorBootstrap
import me.weishu.kernelsu.ui.MainActivity
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.theme.KernelSUTheme

@OptIn(ExperimentalMaterial3Api::class)
class PortalLoginActivity : ComponentActivity() {

    private val viewModel: PortalLoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val uiMode =
            UiMode.fromValue(
                prefs.getString("ui_mode", UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE,
            )

        setContent {
            CompositionLocalProvider(LocalUiMode provides uiMode) {
                KernelSUTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val success by viewModel.loginSuccess.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    var navigated by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(success) {
                        if (success && !navigated) {
                            navigated = true
                            MonitorBootstrap.startIfEligible(application)
                            context.startActivity(
                                Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                },
                            )
                            finish()
                        }
                    }

                    PortalLoginScreen(
                        state = state,
                        onLogin = { u, p -> viewModel.login(u, p) },
                        onClearError = { viewModel.clearError() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalLoginScreen(
    state: PortalLoginUiState,
    onLogin: (String, String) -> Unit,
    onClearError: () -> Unit,
) {
    var user by remember { mutableStateOf(state.savedUsername) }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(state.savedUsername) {
        user = state.savedUsername
    }

    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.portal_login_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = user,
                onValueChange = {
                    user = it
                    onClearError()
                },
                label = { Text(stringResource(R.string.portal_login_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = {
                    pass = it
                    onClearError()
                },
                label = { Text(stringResource(R.string.portal_login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onLogin(user, pass) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.loading) {
                        stringResource(R.string.portal_login_loading)
                    } else {
                        stringResource(R.string.portal_login_submit)
                    },
                )
            }
        }
    }
}
