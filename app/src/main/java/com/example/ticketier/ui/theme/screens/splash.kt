package com.example.ticketier.ui.theme.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import com.example.ticketier.ui.theme.TicketierTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    // Animatable holds a value that can change smoothly over time
    val logoAlpha = remember { Animatable(0f) }   // 0 = invisible, 1 = fully visible
    val logoScale = remember { Animatable(0.6f) } // starts small, grows to full size

    // LaunchedEffect runs once when the screen first appears
    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 900)) }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
        delay(2200)   // how long the splash stays up in total
        onFinished()  // tell the navigation to move on
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),   // was Color.White
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Ticketier",
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = GrandHotel),
            color = MaterialTheme.colorScheme.primary,           // was TicketierPurple
            modifier = Modifier.graphicsLayer {
                alpha = logoAlpha.value
                scaleX = logoScale.value
                scaleY = logoScale.value
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    TicketierTheme(darkTheme = false) {
        SplashScreen(onFinished = {})
    }
}