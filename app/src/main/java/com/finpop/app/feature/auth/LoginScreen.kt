package com.finpop.app.feature.auth

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Paper = Color(0xFFFAF8FF)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF475569)

enum class LoginAction { Google, Guest }

/** Stateless UI. The caller owns authentication, loading, errors, and navigation. */
@Composable
fun LoginScreen(
    onGoogleLogin: () -> Unit,
    onGuestLogin: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
    loadingAction: LoginAction? = null,
    errorMessage: String? = null,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Paper).safeDrawingPadding(),
    ) {
        val viewportHeight = this@BoxWithConstraints.maxHeight
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight).padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Match the reference's generous upper whitespace, without fixing screen height.
            Spacer(Modifier.height((viewportHeight * 0.285f).coerceIn(48.dp, 260.dp)))
            Column(
                modifier = Modifier.widthIn(max = 384.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FinPopLogo(Modifier.widthIn(max = 260.dp).fillMaxWidth().height(132.dp))
                Spacer(Modifier.height(16.dp))
                Text("환영합니다!", color = Ink, fontSize = 24.sp,
                    lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "별도의 복잡한 가입 없이\nGoogle 연동 또는 게스트 모드로 바로\n시작하세요",
                    color = Muted, fontSize = 14.sp, lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                LoginButton(
                    label = "Google 계정으로 계속하기", loadingLabel = "Google 인증 연결 중…",
                    loading = loadingAction == LoginAction.Google,
                    enabled = loadingAction == null, onClick = onGoogleLogin,
                    background = Color.White, border = Color(0xFFDCDCE8),
                    icon = { GoogleMark() },
                )
                Spacer(Modifier.height(12.dp))
                LoginButton(
                    label = "게스트로 시작하기", loadingLabel = "게스트로 연결 중…",
                    loading = loadingAction == LoginAction.Guest,
                    enabled = loadingAction == null, onClick = onGuestLogin,
                    background = Color(0xFFF2F3FF), border = Color(0xFFE0E2F2),
                    icon = { GamepadMark() },
                )
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(errorMessage, color = Color(0xFFBA1A1A), fontSize = 13.sp,
                        textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onTermsClick) {
                    Text("이용약관", color = Muted.copy(alpha = 0.8f), fontSize = 11.sp)
                }
                Text("·", color = Muted, fontSize = 11.sp)
                TextButton(onClick = onPrivacyClick) {
                    Text("개인정보 처리방침", color = Muted.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LoginButton(
    label: String,
    loadingLabel: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    background: Color,
    border: Color,
    icon: @Composable () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (background == Color.White) 2.dp else 1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background, contentColor = Ink,
            disabledContainerColor = background, disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF2563EB),
                strokeWidth = 2.dp)
        } else {
            icon()
        }
        Spacer(Modifier.width(10.dp))
        Text(if (loading) loadingLabel else label, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Vector geometry keeps the HTML's eyes, plaque, colored letters and hard shadow crisp. */
@Composable
private fun FinPopLogo(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "FinPOP 로고" }) {
        val factor = minOf(size.width / 260f, size.height / 132f)
        scale(factor, factor, pivot = Offset.Zero) {
            drawRoundRect(Ink, Offset(2f, 54f), Size(256f, 76f), CornerRadius(22f))
            drawRoundRect(Color.White, Offset(2f, 46f), Size(256f, 76f), CornerRadius(22f))
            drawRoundRect(Ink, Offset(2f, 46f), Size(256f, 76f), CornerRadius(22f), style = Stroke(3f))
            for (x in listOf(89f, 130f)) {
                drawRoundRect(Color.White, Offset(x, 2f), Size(41f, 54f), CornerRadius(12f))
                drawRoundRect(Ink, Offset(x, 2f), Size(41f, 54f), CornerRadius(12f), style = Stroke(3f))
                drawRoundRect(Color(0xFF020617), Offset(x + 10f, 14f), Size(21f, 29f), CornerRadius(11f))
                drawRoundRect(Color.White, Offset(x + 17f, 20f), Size(7f, 10f), CornerRadius(4f))
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 35f
                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val colors = listOf(0xFFF2553B, 0xFFFFAB2D, 0xFFF2553B, 0xFF2251E3, 0xFFFFC233, 0xFFF2553B)
            val centers = listOf(42f, 66f, 92f, 147f, 181f, 218f)
            "FINPOP".forEachIndexed { i, letter ->
                paint.color = colors[i].toInt()
                drawContext.canvas.nativeCanvas.drawText(letter.toString(), centers[i], 94f, paint)
                val halfWidth = if (letter == 'I') 2f else 9f
                drawLine(Ink, Offset(centers[i] - halfWidth, 102f),
                    Offset(centers[i] + halfWidth, 102f), 4f, StrokeCap.Round)
            }
            drawLine(Color(0xFFE2E8F0), Offset(121f, 66f), Offset(121f, 101f), 2f)
        }
    }
}

private val GooglePaths = listOf(
    Color(0xFF4285F4) to "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z",
    Color(0xFF34A853) to "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z",
    Color(0xFFFBBC05) to "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z",
    Color(0xFFEA4335) to "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z",
).map { (color, data) -> color to PathParser().parsePathString(data).toPath() }

@Composable
private fun GoogleMark() {
    Canvas(Modifier.size(20.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            GooglePaths.forEach { (color, path) -> drawPath(path, color) }
        }
    }
}

@Composable
private fun GamepadMark() {
    Canvas(Modifier.size(20.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val blue = Color(0xFF3563FF)
            val outline = Path().apply {
                moveTo(7f, 5f); lineTo(17f, 5f)
                quadraticTo(20f, 5f, 20.5f, 8f); lineTo(22f, 17f)
                quadraticTo(22f, 20f, 19f, 18f); lineTo(16f, 15f)
                lineTo(8f, 15f); lineTo(5f, 18f)
                quadraticTo(2f, 20f, 2f, 17f); lineTo(3.5f, 8f)
                quadraticTo(4f, 5f, 7f, 5f); close()
            }
            drawPath(outline, blue, style = Stroke(2f))
            drawLine(blue, Offset(6f, 10f), Offset(11f, 10f), 2f, StrokeCap.Round)
            drawLine(blue, Offset(8.5f, 7.5f), Offset(8.5f, 12.5f), 2f, StrokeCap.Round)
            drawCircle(blue, 1.2f, Offset(16f, 9f))
            drawCircle(blue, 1.2f, Offset(18f, 12f))
        }
    }
}

@Preview(name = "Login · reference", showBackground = true, widthDp = 390, heightDp = 912)
@Preview(name = "Login · compact", showBackground = true, widthDp = 360, heightDp = 640)
@Preview(name = "Login · large text", showBackground = true, widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Composable
private fun LoginScreenPreview() {
    MaterialTheme { LoginScreen({}, {}, {}, {}) }
}

@Preview(name = "Login · loading", widthDp = 390, heightDp = 844)
@Composable
private fun LoginLoadingPreview() {
    MaterialTheme { LoginScreen({}, {}, {}, {}, loadingAction = LoginAction.Google) }
}
