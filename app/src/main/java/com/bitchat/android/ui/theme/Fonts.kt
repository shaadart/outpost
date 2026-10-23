package com.bitchat.android.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bitchat.android.R

val HeadingFontFamily = FontFamily(Font(R.font.geist_pixel_square))
val BodyFontFamily = FontFamily(
    Font(R.font.dongle_regular, FontWeight.Normal),
    Font(R.font.dongle_bold, FontWeight.Bold)
)

// Bytesized belongs only to the app's wordmark, never a chat name or ordinary heading.
private val OutpostTitleFontFamily = FontFamily(Font(R.font.bytesized_regular))
val OutpostTitleStyle = TextStyle(
    fontFamily = OutpostTitleFontFamily,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = 0.sp
)
