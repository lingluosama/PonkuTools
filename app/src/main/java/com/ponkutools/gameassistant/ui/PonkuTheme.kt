package com.ponkutools.gameassistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BrownLightColors = lightColorScheme(
    primary = Color(0xFF765844),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF2C160B),
    secondary = Color(0xFF6E5D50),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8DDCC),
    onSecondaryContainer = Color(0xFF27180F),
    tertiary = Color(0xFF5F6145),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E6BD),
    onTertiaryContainer = Color(0xFF1B1D08),
    background = Color(0xFFF9F2EC),
    onBackground = Color(0xFF211A17),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF211A17),
    surfaceVariant = Color(0xFFF0E1D8),
    onSurfaceVariant = Color(0xFF51443D),
    outline = Color(0xFF84746B),
    outlineVariant = Color(0xFFD9C2B5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val BrownDarkColors = darkColorScheme(
    primary = Color(0xFFE7BFA8),
    onPrimary = Color(0xFF442A1B),
    primaryContainer = Color(0xFF5D402E),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFDBC2B1),
    onSecondary = Color(0xFF3E2E24),
    secondaryContainer = Color(0xFF51463D),
    onSecondaryContainer = Color(0xFFF8DDCC),
    tertiary = Color(0xFFC9CAA3),
    onTertiary = Color(0xFF30320F),
    tertiaryContainer = Color(0xFF47491F),
    onTertiaryContainer = Color(0xFFE5E6BD),
    background = Color(0xFF1B120E),
    onBackground = Color(0xFFF0E0D8),
    surface = Color(0xFF241A15),
    onSurface = Color(0xFFF0E0D8),
    surfaceVariant = Color(0xFF4F453F),
    onSurfaceVariant = Color(0xFFD8C2B7),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF55443B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val PonkuShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(28.dp),
)

/** Shared MD3 spacing tokens based on the 8dp layout grid. */
object MdSpacing {
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}

@Composable
fun PonkuTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) BrownDarkColors else BrownLightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = PonkuShapes,
        content = content,
    )
}

@Composable
fun PonkuBackground(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = 0.72f),
                            colors.background,
                            colors.secondaryContainer.copy(alpha = 0.62f),
                        ),
                    ),
                ),
        ) {
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

