package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MontserratAlternatesFontFamily
import com.example.ui.theme.RobotoCondensedFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pure `#000000` AMOLED branded splash overlay inspired by AmpereFlow:
 * - Animates the A-Kit logo mark in with a tactile spring scale-up (`0.75f -> 1.0f`)
 * - Pulses a subtle radial cyan/blue glow behind the mark
 * - Fades in the A-Kit wordmark cleanly within ~650ms total, then cross-fades out
 *   directly into the live Power dashboard.
 */
@Composable
fun AKitSplashOverlay(
    visible: Boolean,
    accentColor: Color,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logoScale = remember { Animatable(0.75f) }
    val logoAlpha = remember { Animatable(0f) }
    val glowPulse = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffsetY = remember { Animatable(18f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect

        // Parallel spring emblem reveal + radial glow pulse
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = 0.62f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        launch {
            glowPulse.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
            glowPulse.animateTo(
                targetValue = 0.72f,
                animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
            )
        }
        launch {
            delay(150L)
            launch {
                wordmarkAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                )
            }
            launch {
                wordmarkOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                )
            }
        }

        // Complete branded entrance in ~650ms then smoothly cross-fade into dashboard
        delay(650L)
        onAnimationFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("akit_splash_overlay"),
            contentAlignment = Alignment.Center,
        ) {
            // Subtle radial cyan/blue glow aura behind the emblem
            Canvas(modifier = Modifier.size(260.dp)) {
                val progress = glowPulse.value.coerceIn(0f, 1.2f)
                if (progress > 0.01f) {
                    val radius = (size.minDimension / 2f) * (0.65f + 0.35f * progress)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.30f * progress),
                                Color(0xFF00E5FF).copy(alpha = 0.14f * progress),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_akit_logo_mark),
                    contentDescription = stringResource(R.string.cd_app_emblem),
                    modifier = Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = logoAlpha.value
                        },
                )

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = wordmarkAlpha.value
                        translationY = wordmarkOffsetY.value
                    },
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = TextStyle(
                            fontFamily = MontserratAlternatesFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            letterSpacing = 1.2.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "OFFLINE UTILITY SUITE",
                        style = TextStyle(
                            fontFamily = RobotoCondensedFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 2.6.sp,
                        ),
                        color = accentColor.copy(alpha = 0.88f),
                    )
                }
            }
        }
    }
}
