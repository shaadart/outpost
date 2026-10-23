package com.bitchat.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// Dongle has a small x-height; its point sizes are larger to retain readable text.
internal const val BASE_FONT_SIZE = 26

private fun body(size: Int, lineHeight: Int = size, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
)

private fun heading(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = HeadingFontFamily,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp
)

val Typography = Typography(
    displayLarge = heading(36, 44),
    displayMedium = heading(32, 40),
    displaySmall = heading(28, 36),
    headlineLarge = heading(28, 36),
    headlineMedium = heading(24, 32),
    headlineSmall = heading(22, 30),
    titleLarge = heading(20, 28),
    titleMedium = heading(18, 26),
    titleSmall = heading(16, 24),
    bodyLarge = body(28),
    bodyMedium = body(BASE_FONT_SIZE),
    bodySmall = body(22),
    labelLarge = body(26, weight = FontWeight.Bold),
    labelMedium = body(22),
    labelSmall = body(20)
)
