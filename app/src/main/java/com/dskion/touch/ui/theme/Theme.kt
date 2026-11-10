package com.dskion.touch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TouchPrimary,
    onPrimary = TouchSurfaceLight,
    primaryContainer = TouchPrimaryContainerLight,
    onPrimaryContainer = TouchOnPrimaryContainerLight,
    background = TouchBackgroundLight,
    surface = TouchSurfaceLight,
    onSurface = TouchOnSurfaceLight,
    onBackground = TouchOnSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = TouchPrimaryDark,
    onPrimary = TouchOnSurfaceLight,
    primaryContainer = TouchPrimaryContainerDark,
    onPrimaryContainer = TouchOnPrimaryContainerDark,
    background = TouchBackgroundDark,
    surface = TouchSurfaceDark,
    onSurface = TouchOnSurfaceDark,
    onBackground = TouchOnSurfaceDark,
)

private val TouchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TouchTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, shapes = TouchShapes, content = content)
}
