package com.bitchat.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PanicWipeOverlay(
    onWipeComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableIntStateOf(0) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(500))
        delay(500)
        phase = 1
        delay(800)
        phase = 2
        delay(1200)
        onWipeComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFCC0000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Text(
                text = "\u26A0\uFE0F",
                fontSize = 64.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "WIPING SECURE ENCLAVE",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (phase) {
                0 -> {
                    Text(
                        text = "Destroying encryption keys...",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                1 -> {
                    Text(
                        text = "Clearing message history...",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                2 -> {
                    Text(
                        text = "Identity wiped. Generating new identity...",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            androidx.compose.material3.LinearProgressIndicator(
                progress = {
                    when {
                        phase == 0 -> 0.3f
                        phase == 1 -> 0.6f
                        else -> 0.9f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
