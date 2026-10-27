package dev.clement.streakkit.android.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Ember = Color(0xFFE2662F)
private val EmberDark = Color(0xFFFFB68F)

private val LightScheme = lightColorScheme(primary = Ember)
private val DarkScheme = darkColorScheme(primary = EmberDark)

/**
 * Material 3 with dynamic color where the platform offers it. Kept to one screen's worth of
 * theming on purpose — this repo is about the tooling, not a design system.
 */
@Composable
fun StreakKitTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            isDark -> {
                DarkScheme
            }

            else -> {
                LightScheme
            }
        }

    MaterialTheme(colorScheme = colors, content = content)
}
