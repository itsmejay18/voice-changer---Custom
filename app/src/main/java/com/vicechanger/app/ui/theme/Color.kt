package com.vicechanger.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * VICE CHANGER palette: a dark studio look with a rose primary and a violet secondary,
 * with a mint accent reserved for measured values (levels, latency) so anything in mint is
 * always a real number from the audio engine.
 */
object ViceColors {
    val Rose = Color(0xFFFF4D8D)
    val RoseSoft = Color(0xFFFF7FB2)
    val RoseDeep = Color(0xFFC2185B)
    val Violet = Color(0xFF7C5CFF)
    val VioletDeep = Color(0xFF3B2A6B)
    val Mint = Color(0xFF35E1C4)
    val Amber = Color(0xFFFFB44D)
    val Red = Color(0xFFFF5A6E)

    val Ink = Color(0xFF0C0A12)
    val InkRaised = Color(0xFF15111F)
    val Surface = Color(0xFF171226)
    val SurfaceHigh = Color(0xFF211A33)
    val Outline = Color(0xFF332A4A)
    val OutlineSoft = Color(0xFF261F3A)

    val TextPrimary = Color(0xFFF3EEFA)
    val TextSecondary = Color(0xFFA99FC0)

    val Paper = Color(0xFFFDF7FB)
    val PaperSurface = Color(0xFFFFFFFF)
    val PaperRaised = Color(0xFFF4ECF5)
    val PaperOutline = Color(0xFFE2D5E6)
    val PaperText = Color(0xFF1B1424)
    val PaperTextSecondary = Color(0xFF6A5E78)

    val RoseViolet = listOf(Rose, Violet)
    val StudioBackdrop = listOf(Color(0xFF2A1236), Color(0xFF1A0E24), Ink)
    val RoseGlow = listOf(Color(0x66FF4D8D), Color(0x007C5CFF))
}

/** Header gradient used on every screen title block. */
val HeaderBrush: Brush = Brush.verticalGradient(ViceColors.StudioBackdrop)

/** Primary action gradient. */
val ActionBrush: Brush = Brush.horizontalGradient(ViceColors.RoseViolet)

/** Accent glow behind the microphone orb on the home screen. */
val OrbBrush: Brush = Brush.radialGradient(ViceColors.RoseGlow)
