package com.example.ticketier.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * MATERIAL 3 ROLES, mapped onto your palette:
 *   primary   = your brand purple      -> main actions (Login button, Get Tickets, FAB)
 *   secondary = deeper purple          -> supporting UI (selected chips, nav indicator tint)
 *   tertiary  = pink                   -> the ACCENT: small highlights (date badge month, tags)
 *   error     = your coral             -> validation errors
 *   containers = soft tints of a role  -> backgrounds that carry content in that role
 *   onX       = text/icons drawn ON X  -> always readable
 * Screens now ask for roles (MaterialTheme.colorScheme.primary), never TicketierPurple.
 */
private val TicketierLightColorScheme = lightColorScheme(
    primary = TicketierPurple,
    onPrimary = Color.White,
    primaryContainer = TicketierPurpleContainer,
    onPrimaryContainer = TicketierOnPurpleContainer,

    secondary = TicketierPurpleDark,
    onSecondary = Color.White,
    secondaryContainer = TicketierSecondaryContainer,
    onSecondaryContainer = TicketierOnPurpleContainer,

    tertiary = TicketierPinkDeep,
    onTertiary = Color.White,
    tertiaryContainer = TicketierPinkContainer,
    onTertiaryContainer = TicketierOnPinkContainer,

    background = Color.White,
    onBackground = TicketierBackground,
    surface = Color.White,
    onSurface = TicketierBackground,
    surfaceVariant = TicketierSurfaceVariantLight,
    onSurfaceVariant = TicketierSlate,
    outline = TicketierGray,
    outlineVariant = TicketierLightGray,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = TicketierContainerLow,
    surfaceContainer = TicketierContainer,
    surfaceContainerHigh = TicketierContainerHigh,
    surfaceContainerHighest = TicketierContainerHighest
)

private val TicketierDarkColorScheme = darkColorScheme(
    primary = TicketierPurpleLight,
    onPrimary = TicketierOnPurpleContainer,
    primaryContainer = TicketierDarkPrimaryContainer,
    onPrimaryContainer = TicketierPurpleContainer,

    secondary = TicketierDarkSecondary,
    onSecondary = TicketierOnPurpleContainer,
    secondaryContainer = TicketierDarkSecondaryContainer,
    onSecondaryContainer = TicketierPurpleContainer,

    tertiary = TicketierPink,
    onTertiary = TicketierOnPinkContainer,
    tertiaryContainer = TicketierDarkPinkContainer,
    onTertiaryContainer = TicketierPinkContainer,

    error = TicketierCoral,
    onError = TicketierBackground,

    background = TicketierBackground,
    onBackground = TicketierWhite,
    surface = TicketierBackground,
    onSurface = TicketierWhite,
    surfaceVariant = TicketSurface,
    onSurfaceVariant = TicketierLightGray,
    outline = TicketierGray,
    outlineVariant = TicketierDarkOutlineVariant,

    surfaceContainerLowest = TicketierDarkContainerLowest,
    surfaceContainerLow = TicketierDarkContainerLow,
    surfaceContainer = TicketSurface,
    surfaceContainerHigh = TicketierDarkContainerHigh,
    surfaceContainerHighest = TicketierDarkContainerHighest
)

// SHAPES: components ask for a shape by size. These match the radii you were already using
// (14 dp fields/buttons, 18 dp cards), so the app keeps its look but is now themeable.
private val TicketierShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TicketierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TicketierDarkColorScheme else TicketierLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TicketierShapes,
        content = content
    )
}