package com.finpop.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MainTab(val label: String) { Board("보드"), News("뉴스"), MyPage("마이페이지") }

/** Navigation is supplied by the caller; this component contains no destination logic. */
@Composable
fun MainBottomNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = Color(0xFF0F172A)
    val blue = Color(0xFF1D4ED8)
    Column(modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(25.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawLine(ink, Offset(0f, 4.dp.toPx()), Offset(size.width, 4.dp.toPx()), 1.dp.toPx())
                drawLine(ink.copy(alpha = .5f), Offset(16.dp.toPx(), 10.dp.toPx()),
                    Offset(size.width - 16.dp.toPx(), 10.dp.toPx()), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                drawCircle(ink, 6.dp.toPx(), Offset(0f, 4.dp.toPx()))
                drawCircle(ink, 6.dp.toPx(), Offset(size.width, 4.dp.toPx()))
            }
            Text("✂ TEAR HERE   │   NO. 2026-TICKET-01", Modifier.background(Color.White).padding(horizontal = 5.dp),
                color = ink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val tint = if (selected) blue else Color(0xFF475569)
                Column(Modifier.weight(1f).heightIn(min = 58.dp)
                    .background(if (selected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC), RoundedCornerShape(2.dp))
                    .border(1.dp, if (selected) ink else Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(tab) })
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    MainChromeIcon(when (tab) { MainTab.Board -> "dice"; MainTab.News -> "news"; MainTab.MyPage -> "person" }, tint)
                    Text(tab.label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(66.dp, 11.dp)) {
                var x = 0f
                listOf(2, 1, 3, 1, 2, 4, 1, 2, 1, 3, 2, 1, 4, 2, 1, 3, 2).forEach { width ->
                    drawRect(ink, Offset(x, 0f), Size(width.dp.toPx(), size.height))
                    x += (width + 1).dp.toPx()
                }
            }
            Text(" *FIN-GAME*", color = ink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("SEC: B // ROW 04", color = Color(0xFF475569), fontSize = 8.sp)
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun MainBottomNavigationBarPreview() { MainBottomNavigationBar(MainTab.Board, {}) }
