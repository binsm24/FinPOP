package com.finpop.app.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finpop.app.ui.component.MainAppBar
import com.finpop.app.ui.component.MainBottomNavigationBar
import com.finpop.app.ui.component.MainTab
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Ink = Color(0xFF0F172A)
private val Blue = Color(0xFF1D4ED8)
private val AiRed = Color(0xFFDC2626)
private val Paper = Color(0xFFF8FAFC)
private val Muted = Color(0xFF475569)

/** UI adapter IDs: map core.model.PlayerId explicitly when the domain module is merged. */
enum class GameBoardPlayerId { P1, P2 }

/** UI data only. The game owner supplies balances, turns, ownership and dice results. */
data class GameBoardUiState(
    val playerGold: Int = 2400,
    val aiGold: Int = 1850,
    val playerTurnsRemaining: Int = 4,
    val aiTurnsRemaining: Int = 5,
    val currentPlayerId: GameBoardPlayerId = GameBoardPlayerId.P1,
    // Keep the roller after the result arrives, even when currentPlayerId changes.
    val dicePlayerId: GameBoardPlayerId = currentPlayerId,
    // Set from TURN_READY, remaining turns and island wait status in the game owner.
    val canRollDice: Boolean = true,
    val diceValue: Int = 4,
    val isRolling: Boolean = false,
    val playerPosition: Int = 1,
    val aiPosition: Int = 4,
    val playerOwnedTiles: Set<Int> = setOf(1, 8, 11),
    val aiOwnedTiles: Set<Int> = setOf(4, 5),
) {
    val isPlayerTurn: Boolean get() = currentPlayerId == GameBoardPlayerId.P1
}

/**
 * Full-screen entry point, with reusable chrome. When a parent already owns the Scaffold,
 * use MainGameBoardContent in its content slot to avoid duplicating app bars.
 * Tile IDs run clockwise from island (0) through dividend (13).
 */
@Composable
fun MainGameBoardScreen(
    onMenuClick: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
    onRollDice: (GameBoardPlayerId) -> Unit,
    onTileClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: GameBoardUiState = GameBoardUiState(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize().background(Paper).safeDrawingPadding(),
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MainAppBar("Game Board", onMenuClick) },
        bottomBar = { MainBottomNavigationBar(MainTab.Board, onTabSelected) },
    ) { padding ->
        MainGameBoardContent(state, onRollDice, onTileClick, Modifier.padding(padding))
    }
}

@Composable
fun MainGameBoardContent(
    state: GameBoardUiState,
    onRollDice: (GameBoardPlayerId) -> Unit,
    onTileClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()
            .verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 15.dp, top = 8.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerCard("나", state.playerGold, state.playerTurnsRemaining, state.isPlayerTurn, false, Modifier.weight(1f))
                PlayerCard("AI", state.aiGold, state.aiTurnsRemaining, !state.isPlayerTurn, true, Modifier.weight(1f))
            }
            Board(state, onRollDice, onTileClick)
        }
    }
}

@Composable
private fun PlayerCard(name: String, gold: Int, turns: Int, active: Boolean, ai: Boolean, modifier: Modifier) {
    val accent = if (ai) Color(0xFFD9472B) else Blue
    Column(modifier.heightIn(min = 84.dp).hardShadow(2f).background(Color.White, RoundedCornerShape(4.dp))
        .border(2.dp, Ink, RoundedCornerShape(4.dp)).padding(10.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(34.dp).background(if (ai) Color(0xFFFEE2E2) else Blue, RoundedCornerShape(12.dp))
                .border(1.5.dp, if (ai) accent else Ink, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                BoardIcon(if (ai) "robot" else "person", if (ai) accent else Color.White, Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("${NumberFormat.getIntegerInstance(Locale.US).format(gold)} G", color = if (ai) Ink else Blue,
                    fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${turns.coerceAtLeast(0)}턴 남음", Modifier.background(accent.copy(alpha = .1f)).padding(horizontal = 4.dp, vertical = 1.dp),
                color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(if (active) (if (ai) "AI 턴" else "내 턴") else "대기",
                Modifier.background(if (active) accent else Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                color = if (active) Color.White else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private data class BoardTile(val id: Int, val column: Int, val row: Int, val number: String,
    val title: String, val footer: String, val icon: String, val arrow: String, val color: Color = Color.White)

private val Tiles = listOf(
    BoardTile(0, 0, 0, "휴식", "무인도", "1턴 대기", "island", "→", Color(0xFFE8F5E9)),
    BoardTile(1, 1, 0, "01", "예금", "120G", "deposit", "→"),
    BoardTile(2, 2, 0, "02", "적금", "150G", "savings", "→"),
    BoardTile(3, 3, 0, "", "황금열쇠", "이벤트", "key", "↓", Color(0xFFFFDDB8)),
    BoardTile(4, 3, 1, "03", "금리", "180G", "rate", "↓"),
    BoardTile(5, 3, 2, "04", "자산", "210G", "asset", "↓"),
    BoardTile(6, 3, 3, "05", "펀드", "170G", "fund", "↓"),
    BoardTile(7, 3, 4, "START", "출발", "+200G", "flag", "←"),
    BoardTile(8, 2, 4, "07", "주식", "190G", "stock", "←"),
    BoardTile(9, 1, 4, "08", "채권", "160G", "bond", "←"),
    BoardTile(10, 0, 4, "", "황금열쇠", "이벤트", "key", "↑", Color(0xFFFFDDB8)),
    BoardTile(11, 0, 3, "09", "만기", "200G", "hourglass", "↑"),
    BoardTile(12, 0, 2, "10", "복리", "220G", "chart", "↑"),
    BoardTile(13, 0, 1, "11", "배당", "240G", "coin", "↑"),
)

@Composable
private fun Board(state: GameBoardUiState, onRollDice: (GameBoardPlayerId) -> Unit, onTileClick: (Int) -> Unit) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth().hardShadow().background(Color(0xFFE2E8F0))
        .border(2.dp, Ink).padding(7.dp)) {
        val gap = 4.dp
        val tileWidth = (this@BoxWithConstraints.maxWidth - gap * 3) / 4
        val tileHeight = (tileWidth * 1.18f).coerceAtLeast(99.dp) + 40.dp * (fontScale - 1f)
        Box(Modifier.fillMaxWidth().height(tileHeight * 5 + gap * 4)) {
            Box(Modifier.offset(x = tileWidth + gap, y = tileHeight + gap)
                .size(tileWidth * 2 + gap, tileHeight * 3 + gap * 2)
                .background(Paper, RoundedCornerShape(3.dp)).border(2.dp, Ink, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RollingDice(state, onRollDice)
                    Spacer(Modifier.height(12.dp))
                    Text(if (state.isRolling) "ROLLING…" else "DICE ${state.diceValue.coerceIn(1, 6)}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(when {
                        state.isRolling -> "주사위 굴리는 중"
                        state.playerTurnsRemaining <= 0 -> "게임 종료"
                        !state.isPlayerTurn -> "AI 턴 진행 중"
                        !state.canRollDice -> "이벤트 처리 중"
                        else -> "주사위를 눌러 굴려보세요"
                    },
                        color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
                }
            }
            Tiles.forEach { tile ->
                Tile(tile, state, { onTileClick(tile.id) },
                    Modifier.offset(x = (tileWidth + gap) * tile.column, y = (tileHeight + gap) * tile.row).size(tileWidth, tileHeight))
            }
        }
    }
}

@Composable
private fun Tile(tile: BoardTile, state: GameBoardUiState, onClick: () -> Unit, modifier: Modifier) {
    val owned = when (tile.id) { in state.playerOwnedTiles -> Blue; in state.aiOwnedTiles -> Color(0xFFD9472B); else -> null }
    Box(modifier.background(tile.color).border(2.dp, Ink)
        .clickable(role = Role.Button, onClickLabel = "${tile.title} 상세 보기", onClick = onClick)
        .semantics { contentDescription = "${tile.title}, ${tile.footer}" }.padding(5.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tile.number, color = if (tile.id == 7 || tile.id == 0) Color.White else Ink,
                    modifier = Modifier.background(when (tile.id) { 7 -> Blue; 0 -> Muted; else -> Color.Transparent }).padding(horizontal = 1.dp),
                    fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(tile.arrow, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(if (tile.icon == "key") 36.dp else 27.dp)
                    .background(if (tile.icon == "key") Color.White else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    BoardIcon(tile.icon, if (tile.icon in listOf("deposit", "stock", "chart", "flag", "coin")) Blue else Ink,
                        Modifier.size(if (tile.icon == "island") 32.dp else 24.dp))
                }
                Text(tile.title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            }
        }
        if (owned != null) {
            Box(Modifier.align(Alignment.TopStart).padding(top = 14.dp).rotate(-12f).size(23.dp)
                .border(1.dp, owned.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) {
                Text("G", color = owned.copy(alpha = .45f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
        val playerHere = state.playerPosition == tile.id
        val aiHere = state.aiPosition == tile.id
        if (playerHere || aiHere) {
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (playerHere) Pawn(false)
                if (aiHere) Pawn(true)
            }
        }
    }
}

@Composable
private fun Pawn(ai: Boolean) {
    Box(Modifier.size(23.dp).background(if (ai) Muted else Color(0xFFF59E0B), CircleShape)
        .border(1.5.dp, Ink, CircleShape).semantics { contentDescription = if (ai) "AI 현재 위치" else "내 현재 위치" },
        contentAlignment = Alignment.Center) {
        BoardIcon(if (ai) "robot" else "person", if (ai) Color.White else Ink, Modifier.size(15.dp))
    }
}

@Composable
private fun RollingDice(state: GameBoardUiState, onRollDice: (GameBoardPlayerId) -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.isRolling, state.dicePlayerId) {
        progress.snapTo(0f)
        if (state.isRolling) progress.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
    }
    val rolling = state.isRolling
    val rollerName = if (state.dicePlayerId == GameBoardPlayerId.P1) "플레이어" else "AI"
    val dotColor = if (state.dicePlayerId == GameBoardPlayerId.P1) Blue else AiRed
    val phase = progress.value
    val face = if (rolling) (phase * 24).toInt() % 6 + 1 else state.diceValue
    Box(Modifier.size(80.dp)
        .graphicsLayer {
            rotationZ = if (rolling) phase * 720f else 0f
            translationY = if (rolling) (-kotlin.math.abs(kotlin.math.sin(phase * Math.PI * 3)) * 16.dp.toPx()).toFloat() else 0f
        }
        .hardShadow(4f).background(Color.White, RoundedCornerShape(8.dp))
        .border(2.dp, Ink, RoundedCornerShape(8.dp))
        .clickable(enabled = state.canRollDice && state.isPlayerTurn && !rolling && state.playerTurnsRemaining > 0,
            role = Role.Button, onClickLabel = "주사위 굴리기", onClick = { onRollDice(state.currentPlayerId) })
        .semantics { contentDescription = if (rolling) "$rollerName 주사위 굴리는 중" else "$rollerName 주사위 ${state.diceValue}" }
        .padding(13.dp)) {
        DiceFace(face, Modifier.fillMaxSize(), dotColor = dotColor)
    }
}

@Composable
private fun DiceFace(value: Int, modifier: Modifier, dotColor: Color = Blue, background: Color = Color.Transparent) {
    val face = value.coerceIn(1, 6)
    Canvas(modifier.semantics { contentDescription = "주사위 $face" }) {
        drawRect(background)
        val points = buildList {
            if (face % 2 == 1) add(.5f to .5f)
            if (face >= 2) { add(.2f to .2f); add(.8f to .8f) }
            if (face >= 4) { add(.8f to .2f); add(.2f to .8f) }
            if (face == 6) { add(.2f to .5f); add(.8f to .5f) }
        }
        points.forEach { (x, y) -> drawCircle(dotColor, size.minDimension * .13f, Offset(size.width * x, size.height * y)) }
    }
}

private fun Modifier.hardShadow(offset: Float = 3f) = drawBehind {
    drawRoundRect(Ink, Offset(offset.dp.toPx(), offset.dp.toPx()), size, CornerRadius(3.dp.toPx()))
}

private val IconPaths = mapOf(
    "person" to "M9 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M5 21v-4q0-5 7-5t7 5v4z",
    "robot" to "M4 7h16v14H4zM12 3v4M9 3h6M1 11v6M23 11v6M8 11v3M16 11v3M9 17h6",
    "island" to "M1 4l6 3 5-3 5 3 6-3v16l-6 3-5-3-5 3-6-3zM7 7v16M12 4v16M17 7v16",
    "deposit" to "M3 4h18v16H3zM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M12 10v4M10 12h4",
    "savings" to "M5 7h14v3H5zM5 13h14v3H5zM5 19h14v3H5zM9 3h6v3H9z",
    "key" to "M3 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0M13 12h9M18 12v4M21 12v3M7 12h2",
    "rate" to "M5 19L19 5M4 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M16 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "asset" to "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20M12 2v20M2 12h20",
    "fund" to "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20M12 2v10l7 5",
    "flag" to "M4 23V2M4 4h17l-3 6 3 5H4",
    "stock" to "M2 20l6-7 5 3 8-12M15 4h6v6",
    "bond" to "M4 2h16v20H4zM7 6h10M7 10h10M11 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    "hourglass" to "M5 2h14M5 22h14M6 2v5l6 5-6 5v5M18 2v5l-6 5 6 5v5",
    "chart" to "M1 22h22M3 21v-5h4v5M9 21V11h4v10M15 21V6h4v15M21 21V2",
    "coin" to "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20M15 7h-5q-4 4 2 5t-2 5h-2M12 5v14",
).mapValues { (_, path) -> PathParser().parsePathString(path).toPath() }

@Composable
private fun BoardIcon(kind: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val path = IconPaths[kind] ?: return@scale
            if (kind == "island") drawPath(path, Color(0xFF10B981))
            if (kind == "flag") drawPath(path, Blue.copy(alpha = .3f))
            drawPath(path, tint, style = Stroke(1.8f))
        }
    }
}

@Preview(name = "Board · reference", showBackground = true, widthDp = 390, heightDp = 912)
@Preview(name = "Board · compact", showBackground = true, widthDp = 360, heightDp = 640)
@Preview(name = "Board · large text", showBackground = true, widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Composable
private fun MainGameBoardPreview() {
    // Interactive preview only; production dice results come from the game owner.
    var dice by remember { mutableIntStateOf(4) }
    var rolling by remember { mutableStateOf(false) }
    LaunchedEffect(rolling) {
        if (rolling) {
            delay(900)
            dice = Random.nextInt(1, 7)
            rolling = false
        }
    }
    MaterialTheme {
        MainGameBoardScreen({}, {}, { if (!rolling) rolling = true }, {},
            state = GameBoardUiState(diceValue = dice, isRolling = rolling))
    }
}

@Preview(name = "Board · AI dice", showBackground = true, widthDp = 390, heightDp = 912)
@Composable
private fun AiDicePreview() {
    MaterialTheme {
        MainGameBoardScreen({}, {}, {}, {}, state = GameBoardUiState(
            currentPlayerId = GameBoardPlayerId.P2, dicePlayerId = GameBoardPlayerId.P2,
            diceValue = 6,
        ))
    }
}
