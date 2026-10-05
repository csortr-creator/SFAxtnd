package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFADC7FF), onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF284777), onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBBC6DC), onSecondary = Color(0xFF253141),
    secondaryContainer = Color(0xFF3B4758), onSecondaryContainer = Color(0xFFD7E2F9),
    tertiary = Color(0xFF9FD0C5), onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF205047), onTertiaryContainer = Color(0xFFBAECE1),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C22),
    surfaceContainer = Color(0xFF1D2026), surfaceContainerHigh = Color(0xFF282A31),
    surfaceContainerHighest = Color(0xFF33353C), surfaceVariant = Color(0xFF434750),
    onSurfaceVariant = Color(0xFFC3C6D0), outline = Color(0xFF8D919B), outlineVariant = Color(0xFF434750),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF365E9D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF102F60),
    secondary = Color(0xFF535F70), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E2F9), onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF38665B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBAECE1), onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF191C22),
    surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191C22),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEDF4), surfaceContainerHigh = Color(0xFFE6E7EE),
    surfaceContainerHighest = Color(0xFFE0E2E8), surfaceVariant = Color(0xFFDFE2EC),
    onSurfaceVariant = Color(0xFF434750), outline = Color(0xFF747882), outlineVariant = Color(0xFFC3C6D0),
)

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
