package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        delay(1300) // 2s total splash presentation
        onSplashFinished()
    }

    // Blend of vibrant green and fresh crisp white as requested
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF4ADE80), // Fresh Bright Green
            Color(0xFF86EFAC),
            Color(0xFFF0FDF4),
            Color(0xFFFFFFFF)  // Pure Crisp White
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onSplashFinished()
            }
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(scale.value)
        ) {
            // LariKita Logo Box with rounded shadow
            Surface(
                modifier = Modifier
                    .size(170.dp)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(36.dp), spotColor = Color(0x33000000)),
                shape = RoundedCornerShape(36.dp),
                color = Color.White
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_larikita_logo),
                    contentDescription = "LariKita Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(36.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Main Branding Header
            Text(
                text = "LariKita",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF14532D) // Deep Sporty Forest Green
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Required Creator Subhead
            Text(
                text = "by Rafline Dev",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFF166534)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "GPS Running Tracker • Local-First",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4B7258)
            )
        }

        // Bottom footer version
        Text(
            text = "Versi 1.0 • Smart Sync Enabled",
            fontSize = 11.sp,
            color = Color(0xFF6B8A74),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
