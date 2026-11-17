package com.finpop.app.feature.splash

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

private val Paper = Color(0xFFFAF8FF)
private val Outline = Color(0xFF18181B)
private val Blue = Color(0xFF1D4ED8)
private val Gold = Color(0xFFFBBF24)
private val Amber = Color(0xFFF59E0B)
private val Coral = Color(0xFFFF5A43)

/** In-app intro, following the Android system launch screen. No authentication is performed here. */
@Composable
fun SplashScreen(
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    autoEnterMillis: Long = 3_000L,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val currentOnEnter by rememberUpdatedState(onEnter)
    var entered by remember { mutableStateOf(false) }
    val enter = {
        if (!entered) {
            entered = true
            currentOnEnter()
        }
    }
    // Cancel when backgrounded; start a fresh viewing interval when resumed.
    LaunchedEffect(resumed, autoEnterMillis) {
        if (resumed) {
            delay(autoEnterMillis.coerceAtLeast(0L))
            enter()
        }
    }

    val motion = rememberInfiniteTransition(label = "Splash motion")
    val firstFloat by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Coin one 4.2 second cycle",
    )
    val secondFloat by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1_750, easing = FastOutSlowInEasing), RepeatMode.Reverse,
            initialStartOffset = StartOffset(700)),
        label = "Coin two 3.5 second cycle",
    )
    val sparkle by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1_400), RepeatMode.Reverse), label = "Star twinkle",
    )
    val pulse by motion.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(1_000), RepeatMode.Reverse), label = "Badge pulse",
    )

    Box(
        modifier.fillMaxSize().background(Paper)
            .semantics { contentDescription = "FinPOP 시작 화면" }
            .clickable(role = Role.Button, onClickLabel = "로그인 화면으로 이동", onClick = enter),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBucket(firstFloat, secondFloat, sparkle)
            // Uniform scaling keeps the mascot round on different aspect ratios.
            val logoScale = (size.width / 390f).coerceAtMost(1.8f * density)
            val logoWidth = 290f * logoScale
            translate((size.width - logoWidth) / 2f, size.height * 0.5f - 85f * logoScale) {
                scale(logoScale, logoScale, Offset.Zero) { drawBrand(pulse) }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IndicatorDot(Color.White, false)
                IndicatorDot(Color.White, false)
                IndicatorDot(Coral, true)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "TAP OR WAIT TO ENTER", color = Color.White,
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                style = TextStyle(shadow = Shadow(Outline, Offset(0f, 2f), 4f)),
            )
        }
    }
}

@Composable
private fun IndicatorDot(color: Color, elongated: Boolean) {
    Canvas(Modifier.size(if (elongated) 24.dp else 10.dp, 10.dp)) {
        drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(Outline, cornerRadius = CornerRadius(size.height / 2), style = Stroke(2.dp.toPx()))
    }
}

private fun DrawScope.drawBucket(firstFloat: Float, secondFloat: Float, sparkle: Float) {
    val rimBottom = size.height * 0.37f
    val factor = maxOf(size.width / 390f, rimBottom / 320f)
    // Equivalent to the SVG's xMidYMax slice, with a full-height striped bucket below it.
    val stripeWidth = size.width / 6f
    repeat(6) { index ->
        drawRect(if (index % 2 == 0) Blue else Paper,
            Offset(index * stripeWidth, rimBottom), Size(stripeWidth, size.height - rimBottom))
        if (index > 0) drawLine(Outline, Offset(index * stripeWidth, rimBottom),
            Offset(index * stripeWidth, size.height), 3.5f * factor)
    }
    translate((size.width - 390f * factor) / 2f, rimBottom - 320f * factor) {
        scale(factor, factor, Offset.Zero) {
            star(Offset(60f, 48f), 7f * (1f + sparkle * 0.15f))
            star(Offset(330f, 70f), 9f * (1f + sparkle * 0.15f))
            drawCircle(Gold.copy(alpha = 0.9f + sparkle * 0.1f), 3f, Offset(140f, 30f))
            coin(285f, 45f - firstFloat * 7f, 28f, Gold, "¢")
            coin(95f, 92f - secondFloat * 7f, 33f, Gold, "$")
            val back = listOf(
                Triple(30f, 195f, 34f), Triple(85f, 170f, 33f), Triple(145f, 160f, 35f),
                Triple(210f, 155f, 33f), Triple(270f, 165f, 35f), Triple(340f, 180f, 35f),
                Triple(380f, 205f, 32f),
            )
            back.forEachIndexed { index, (x, y, r) -> coin(x, y, r, if (index % 2 == 0) Amber else Gold) }
            coin(45f, 225f, 34f, Gold)
            coin(110f, 210f, 35f, Amber)
            coin(180f, 195f, 38f, Gold, "★")
            coin(245f, 205f, 36f, Gold)
            coin(310f, 215f, 35f, Amber)
            coin(365f, 235f, 32f, Gold)
            val front = listOf(
                Triple(15f, 255f, 30f), Triple(70f, 245f, 32f), Triple(140f, 240f, 34f),
                Triple(205f, 242f, 33f), Triple(275f, 242f, 33f), Triple(335f, 250f, 31f),
                Triple(385f, 265f, 28f),
            )
            front.forEachIndexed { index, (x, y, r) -> coin(x, y, r, if (index % 3 == 0) Amber else Gold) }
            repeat(6) { index ->
                val x = index * 65f
                val rim = Path().apply {
                    moveTo(x, 320f); lineTo(x, 293f)
                    quadraticTo(x, 275f, x + 32.5f, 275f)
                    quadraticTo(x + 65f, 275f, x + 65f, 293f)
                    lineTo(x + 65f, 320f); close()
                }
                drawPath(rim, if (index % 2 == 0) Blue else Paper)
                drawPath(rim, Outline, style = Stroke(3.5f))
            }
        }
    }
}

private fun DrawScope.coin(x: Float, y: Float, radius: Float, color: Color, symbol: String? = null) {
    drawCircle(color, radius, Offset(x, y))
    drawCircle(Outline, radius, Offset(x, y), style = Stroke(3.5f))
    if (symbol != null) {
        drawCircle(Amber, radius - 8f, Offset(x, y),
            style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f))))
        lettering(symbol, x, y + 8f, 24f, Color(0xFFD97706))
    }
}

private fun DrawScope.star(center: Offset, radius: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.28f, center.y - radius * 0.28f)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + radius * 0.28f, center.y + radius * 0.28f)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.28f, center.y + radius * 0.28f)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - radius * 0.28f, center.y - radius * 0.28f); close()
    }
    drawPath(path, Gold)
    drawPath(path, Outline, style = Stroke(1.5f))
}

private fun DrawScope.drawBrand(pulse: Float) {
    drawRoundRect(Outline, Offset(7f, 45f), Size(276f, 90f), CornerRadius(24f))
    drawRoundRect(Color.White, Offset(7f, 33f), Size(276f, 90f), CornerRadius(24f))
    drawRoundRect(Outline, Offset(7f, 33f), Size(276f, 90f), CornerRadius(24f), style = Stroke(4f))
    for (x in listOf(124f, 162f)) {
        drawOval(Color.White, Offset(x - 20f, 0f), Size(40f, 46f))
        drawOval(Outline, Offset(x - 20f, 0f), Size(40f, 46f), style = Stroke(3.5f))
        drawOval(Color(0xFF09090B), Offset(x - 10f, 9f), Size(23f, 28f))
        drawCircle(Color.White, 4f, Offset(x + 4f, 17f))
    }
    val centers = listOf(43f, 72f, 105f, 164f, 206f, 249f)
    val colors = listOf(Coral, Gold, Coral, Blue, Gold, Coral)
    "FINPOP".forEachIndexed { index, letter ->
        lettering(letter.toString(), centers[index], 94f, 46f, Outline)
        lettering(letter.toString(), centers[index], 92f, 46f, colors[index])
        val half = if (letter == 'I') 3f else 11f
        drawLine(Outline, Offset(centers[index] - half, 102f),
            Offset(centers[index] + half, 102f), 4f, StrokeCap.Round)
    }
    drawLine(Color(0xFFD4D4D8), Offset(135f, 53f), Offset(135f, 102f), 2f)
    drawRoundRect(Outline, Offset(42f, 145f), Size(206f, 32f), CornerRadius(16f))
    drawRoundRect(Color(0xFFFEF08A), Offset(42f, 141f), Size(206f, 32f), CornerRadius(16f))
    drawRoundRect(Outline, Offset(42f, 141f), Size(206f, 32f), CornerRadius(16f), style = Stroke(2.5f))
    drawCircle(Coral.copy(alpha = pulse), 3.5f, Offset(62f, 157f))
    drawCircle(Outline, 3.5f, Offset(62f, 157f), style = Stroke(1f))
    lettering("FUN · FINANCE · POPCORN", 151f, 161f, 11.5f, Outline)
}

private fun DrawScope.lettering(text: String, x: Float, y: Float, fontSize: Float, color: Color) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb(
            (color.alpha * 255).toInt(), (color.red * 255).toInt(),
            (color.green * 255).toInt(), (color.blue * 255).toInt(),
        )
        textSize = fontSize
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

@Preview(name = "Splash reference", widthDp = 390, heightDp = 912)
@Preview(name = "Splash compact", widthDp = 360, heightDp = 640)
@Composable
private fun SplashScreenPreview() {
    SplashScreen(onEnter = {}, autoEnterMillis = Long.MAX_VALUE)
}
