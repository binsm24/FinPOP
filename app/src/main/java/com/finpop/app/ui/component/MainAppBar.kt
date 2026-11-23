package com.finpop.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared app header. The parent Scaffold owns system-bar insets. */
@Composable
fun MainAppBar(
    title: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit = { MainChromeIcon("dice", Color(0xFF1D4ED8)) },
) {
    val blue = Color(0xFF1D4ED8)
    val ink = Color(0xFF0F172A)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(blue).heightIn(min = 56.dp)
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).drawBehind {
                drawRect(ink, Offset(2.dp.toPx(), 2.dp.toPx()))
            }.background(Color.White).border(1.5.dp, ink), contentAlignment = Alignment.Center) {
                leadingIcon()
            }
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onMenuClick,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "메뉴 열기" }) {
                Box(Modifier.size(32.dp).drawBehind {
                    drawRect(ink, Offset(2.dp.toPx(), 2.dp.toPx()))
                }.background(Color.White).border(2.dp, ink), contentAlignment = Alignment.Center) {
                    MainChromeIcon("menu", ink)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            var x = 6.dp.toPx()
            while (x < size.width + 6.dp.toPx()) {
                drawCircle(blue, 4.dp.toPx(), Offset(x, 0f))
                x += 12.dp.toPx()
            }
        }
    }
}

/** Small local vector marks shared by the two chrome components; no network assets. */
@Composable
internal fun MainChromeIcon(kind: String, tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f)
            when (kind) {
                "dice" -> {
                    drawRoundRect(tint, Offset(3f, 3f), Size(18f, 18f), CornerRadius(2f), style = stroke)
                    listOf(7f to 7f, 17f to 7f, 12f to 12f, 7f to 17f, 17f to 17f).forEach { (x, y) ->
                        drawCircle(tint, 1.6f, Offset(x, y))
                    }
                }
                "menu" -> listOf(5f, 12f, 19f).forEach {
                    drawLine(tint, Offset(3f, it), Offset(21f, it), 2f)
                }
                "person" -> {
                    drawCircle(tint, 4f, Offset(12f, 6f), style = stroke)
                    val path = PathParser().parsePathString("M4 22v-4q0-5 8-5t8 5v4").toPath()
                    drawPath(path, tint, style = stroke)
                }
                "news" -> {
                    drawRoundRect(tint, Offset(7f, 2f), Size(14f, 19f), CornerRadius(1f), style = stroke)
                    drawPath(PathParser().parsePathString("M7 7H3v15h14M10 6h8v5h-8zM10 15h8M10 18h6").toPath(), tint, style = stroke)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun MainAppBarPreview() { MainAppBar("Game Board", {}) }
