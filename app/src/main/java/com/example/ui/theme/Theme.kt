package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BlackNoirColorScheme = darkColorScheme(
    primary = NoirPureWhite,
    onPrimary = NoirBlackBg,
    primaryContainer = NoirContainerHigh,
    onPrimaryContainer = NoirPureWhite,
    secondary = NoirSoftWhite,
    onSecondary = NoirBlackBg,
    secondaryContainer = NoirElevatedSurface,
    onSecondaryContainer = NoirPureWhite,
    tertiary = NoirMutedWhite,
    onTertiary = NoirBlackBg,
    tertiaryContainer = NoirElevatedSurface,
    onTertiaryContainer = NoirPureWhite,
    background = NoirBlackBg,
    onBackground = NoirPureWhite,
    surface = NoirCardSurface,
    onSurface = NoirPureWhite,
    surfaceVariant = NoirElevatedSurface,
    onSurfaceVariant = NoirMutedWhite,
    surfaceContainerLowest = NoirBlackBg,
    surfaceContainerLow = NoirSurfaceLow,
    surfaceContainer = NoirCardSurface,
    surfaceContainerHigh = NoirElevatedSurface,
    surfaceContainerHighest = NoirContainerHigh,
    outline = NoirOutline,
    outlineVariant = NoirHairlineOutline,
    error = NoirRedText,
    onError = NoirRedBg,
    errorContainer = NoirRedBg,
    onErrorContainer = NoirPureWhite
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AgendaPenalTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BlackNoirColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
