package me.weishu.kernelsu.ui.component

import androidx.compose.runtime.Composable
import me.weishu.kernelsu.Natives

@Composable
fun KsuIsValid(
    content: @Composable () -> Unit
) {
    val ksuVersion = Natives.version.takeIf { it > 0 }

    if (ksuVersion != null) {
        content()
    }
}
