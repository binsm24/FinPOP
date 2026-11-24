package com.finpop.app.feature.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finpop.app.feature.game.BoardIcon
import com.finpop.app.feature.game.GameBoardUiState
import com.finpop.app.feature.game.MainGameBoardScreen
import com.finpop.app.feature.game.QuizTile
import com.finpop.app.feature.game.quizTile
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private val Blue = Color(0xFF1D4ED8)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF475569)
private val Paper = Color(0xFFF8FAFC)
private val Tint = Color(0xFFEFF4FB)
private val Line = Color(0xFFD8E1ED)
private val Red = Color(0xFFB91C1C)
private val Green = Color(0xFF047857)
private val Gold = Color(0xFFFFB51B)
private val Corners = RoundedCornerShape(5.dp)

private const val Question = "정기예금을 만기 전에 해지하면 일반적으로 어떻게 될까?"
private val Answers = listOf(
    "약정 이자보다 낮은 이자를 받는다",
    "약정 이자의 두 배를 받는다",
    "원금을 받을 수 없다",
    "다음 달에 자동으로 재가입된다",
)
private const val Hint = "예금 해지에는 만기 해지와 중도 해지가 있으며, 만기 해지 시 약정했던 이율과 기간에 따른 전체 이자가 지급됩니다. 중도 해지 시에는 약정한 금리보다 낮은 중도해지이율이 적용되어 이자 수익이 크게 줄어듭니다. 보통 가입 기간을 오래 채울수록 높은 비율의 중도해지 이율이 적용됩니다."
private const val Explanation = "정기예금을 만기 전에 중도해지하면 원래 약정했던 정상 이율 대신 중도해지 이율(패널티 금리)이 적용되어 이자 수익이 대폭 감소합니다. 따라서 정기예금은 계획된 만기까지 유지하는 것이 가장 유리합니다."

internal enum class QuizPage { Question, Correct, Incorrect }

@Composable
internal fun TileQuizPopup(
    tile: QuizTile,
    gold: Int,
    canAcquire: Boolean,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onAcquire: () -> Unit,
    onFinish: () -> Unit,
    initialPage: QuizPage = QuizPage.Question,
) {
    val inspecting = LocalInspectionMode.current
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var selection by rememberSaveable { mutableIntStateOf(if (inspecting && initialPage == QuizPage.Question) 0 else -1) }
    var seconds by rememberSaveable { mutableIntStateOf(20) }
    BackHandler { if (collapsed) onToggleCollapsed() else onFinish() }

    LaunchedEffect(page, inspecting) {
        if (!inspecting && page == QuizPage.Question) {
            while (seconds > 0) {
                delay(1000)
                seconds--
            }
            page = QuizPage.Incorrect
        }
    }

    val correct = page == QuizPage.Correct
    val failed = page == QuizPage.Incorrect
    val balance = NumberFormat.getIntegerInstance(Locale.KOREA).format(gold)
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.fillMaxSize().background(if (collapsed) Color.Transparent else Ink.copy(alpha = 0.58f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = if (collapsed) Alignment.TopCenter else Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 410.dp).fillMaxWidth().shadow(14.dp, shape).clip(shape)
                .background(Paper).border(2.dp, Ink, shape)
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = "금융 퀴즈" },
        ) {
            Column(Modifier.fillMaxWidth().background(Blue)) {
                if (correct) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End) {
                        QuizLabel(
                            if (collapsed) "⌄  퀴즈 결과 펼치기" else "⌃  보드 보기 (접기)",
                            Color.White, Color.White.copy(alpha = 0.2f),
                            Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onToggleCollapsed),
                        )
                    }
                }
                if (!collapsed) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            when (page) {
                                QuizPage.Question -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    QuizLabel("STAGE 01", Ink, Color(0xFFDDE7FF))
                                    Text("FINANCIAL QUIZ CARD", color = Color.White,
                                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                }
                                QuizPage.Correct -> QuizLabel("STAGE 01 CLEAR", Color.White, Green)
                                QuizPage.Incorrect -> QuizLabel("⊗ STAGE 01 // 오답", Red, Color(0xFFFFDFDA))
                            }
                            if (page == QuizPage.Question) {
                                QuizLabel("◴ 00:%02d".format(Locale.ROOT, seconds), Ink, Gold, Modifier.clip(CircleShape))
                            }
                            if (correct) QuizLabel("●  보유 코인  $balance G", Blue, Color.White, Modifier.clip(CircleShape))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(36.dp).background(Color.White, Corners), contentAlignment = Alignment.Center) {
                                BoardIcon(tile.icon, Blue, Modifier.size(25.dp))
                            }
                            Text(tile.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text(tile.icon.uppercase(Locale.ROOT) + if (page == QuizPage.Question) "" else " // TILE ${tile.number}",
                                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                    Canvas(Modifier.fillMaxWidth().height(7.dp)) {
                        val radius = 12.dp.toPx()
                        drawCircle(Ink.copy(alpha = 0.55f), radius, Offset(0f, size.height))
                        drawCircle(Ink.copy(alpha = 0.55f), radius, Offset(size.width, size.height))
                    }
                }
            }
            if (collapsed) return@Column

            Row(Modifier.fillMaxWidth().background(Tint).padding(horizontal = 13.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TicketText("SEC: BANK-${tile.number} // ADMIT FIN-POP")
                Barcode(Modifier.weight(1f).height(6.dp))
                TicketText("VALID TICKET")
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failed) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFFFFDEDA), Corners)
                        .border(1.dp, Color(0xFFF3ABA5), Corners).padding(10.dp)) {
                        Text(if (seconds == 0) "시간이 초과됐어요!" else "⊗  아쉬워요! 오답입니다",
                            color = Red, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Body(if (seconds == 0) "제한 시간 안에 답변을 제출하지 못했습니다." else "선택한 답변이 맞지 않아 타일을 획득하지 못했습니다.")
                    }
                }
                Column(Modifier.fillMaxWidth().background(Tint, Corners).border(1.dp, Line, Corners).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (page == QuizPage.Question) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            QuizLabel("◇ 기초 금융 상식", Blue, Color.White)
                            QuizLabel("난이도 ★☆☆", Muted, Tint)
                        }
                    } else if (correct) {
                        QuizLabel("✓ 정답", Color.White, Blue)
                    }
                    Text("Q. $Question", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 19.sp)
                    if (page == QuizPage.Question) {
                        Body(Hint)
                    } else {
                        AnswerRow(0, selected = true, onSelect = null, showCorrectLabel = failed)
                        if (correct) Box(Modifier.background(Paper, Corners).padding(8.dp)) { Body("해설 · $Explanation") }
                    }
                }

                when (page) {
                    QuizPage.Question -> {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Answers.indices.forEach { index ->
                                AnswerRow(index, selection == index, { selection = index })
                            }
                        }
                        QuizButton("정답 제출하기 (SUBMIT)", filled = true, enabled = selection >= 0) {
                            page = if (selection == 0) QuizPage.Correct else QuizPage.Incorrect
                        }
                        QuizButton("×  다음에 풀기", filled = false, onClick = onFinish)
                    }
                    QuizPage.Correct -> {
                        Column(Modifier.fillMaxWidth().background(Color.White, Corners)
                            .border(2.dp, Ink, Corners).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("타일 인수 결정", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                QuizLabel("TERRITORY TICKET", Blue, Tint)
                            }
                            Row(Modifier.fillMaxWidth().background(Tint, Corners).padding(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Body("타일 인수 비용")
                                    Text("${tile.price} G", color = Red, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Body("보유 $balance G")
                                }
                                Column(Modifier.weight(1f)) {
                                    Body("예상 통행료 수익")
                                    Text("+${tile.price / 2} G / 턴당", color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                            }
                            Body("인수하면 내 소유지가 되며, AI가 이 칸에 도착할 때 통행료가 입금됩니다.")
                            if (!canAcquire) Body("이미 소유자가 있는 타일입니다.")
                            else if (gold < tile.price) Body("보유 코인이 부족합니다.")
                            QuizButton("인수하기 (${tile.price} G 지불)", filled = true,
                                enabled = canAcquire && gold >= tile.price, onClick = onAcquire)
                            QuizButton("↪  건너뛰기 (패스)", filled = false, onClick = onFinish)
                        }
                    }
                    QuizPage.Incorrect -> {
                        Column(Modifier.fillMaxWidth().background(Color.White, Corners)
                            .border(1.dp, Line, Corners).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("💡 핵심 금융 해설", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Body(Explanation)
                        }
                        Box(Modifier.fillMaxWidth().background(Tint, Corners).padding(8.dp)) {
                            Body("이번 턴에는 ${tile.number}번 ${tile.title} 타일을 인수할 수 없으며, AI 뱅커에게 턴이 넘어갑니다.")
                        }
                        QuizButton("→  확인하고 턴 넘기기", filled = true, onClick = onFinish)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Barcode(Modifier.width(70.dp).height(12.dp))
                Box(Modifier.weight(1f)) { TicketText(if (correct) "*TILE-DEED*" else "*QUIZ-FINPOP-PASS*") }
                val stamp = when {
                    failed -> "STAMP: INCORRECT"
                    correct && canAcquire -> "LAND STATUS: AVAILABLE"
                    correct -> "LAND STATUS: OWNED"
                    else -> "STAMP: APPROVED"
                }
                TicketText(stamp, if (failed) Red else if (correct) Green else Muted)
            }
        }
    }
}

@Preview(name = "01 퀴즈 문제", showBackground = true, widthDp = 390, heightDp = 912)
@Composable
private fun QuizQuestionPreview() = QuizBoardPreview(QuizPage.Question)

@Preview(name = "02 정답과 타일 인수", showBackground = true, widthDp = 390, heightDp = 912)
@Composable
private fun QuizCorrectPreview() = QuizBoardPreview(QuizPage.Correct)

@Preview(name = "03 오답과 해설", showBackground = true, widthDp = 390, heightDp = 912)
@Composable
private fun QuizIncorrectPreview() = QuizBoardPreview(QuizPage.Incorrect)

@Composable
private fun QuizBoardPreview(page: QuizPage) {
    val deposit = checkNotNull(quizTile(1))
    val boardState = GameBoardUiState(playerPosition = deposit.id, canRollDice = false)
    var collapsed by remember { mutableStateOf(false) }
    MaterialTheme {
        MainGameBoardScreen(
            onMenuClick = {},
            onTabSelected = {},
            onRollDice = {},
            onTileClick = {},
            state = boardState,
            blurBoard = !collapsed,
            overlay = {
                TileQuizPopup(
                    tile = deposit,
                    gold = boardState.playerGold,
                    canAcquire = true,
                    collapsed = collapsed,
                    onToggleCollapsed = { collapsed = !collapsed },
                    onAcquire = {},
                    onFinish = {},
                    initialPage = page,
                )
            },
        )
    }
}

@Composable
private fun QuizLabel(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(text, modifier.background(background, Corners).padding(horizontal = 6.dp, vertical = 3.dp),
        color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 12.sp)
}

@Composable
private fun Body(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun TicketText(text: String, color: Color = Muted) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 8.sp)
}

@Composable
private fun Barcode(modifier: Modifier) {
    Canvas(modifier) {
        val widths = listOf(2, 1, 3, 1, 2, 4, 1, 2, 3, 1, 2, 1, 4, 2, 1, 3, 2)
        val unit = size.width / (widths.sum() + widths.lastIndex)
        var x = 0f
        widths.forEach { width ->
            drawRect(Ink, Offset(x, 0f), Size(width * unit, size.height))
            x += (width + 1) * unit
        }
    }
}

@Composable
private fun AnswerRow(index: Int, selected: Boolean, onSelect: (() -> Unit)?, showCorrectLabel: Boolean = false) {
    val selectionModifier = if (onSelect == null) Modifier else
        Modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect)
    Row(Modifier.fillMaxWidth().clip(Corners).background(if (selected) Color(0xFFEDF2FF) else Color.White)
        .border(1.dp, if (selected) Blue.copy(alpha = 0.55f) else Line, Corners)
        .then(selectionModifier).heightIn(min = if (onSelect == null) 40.dp else 44.dp).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuizLabel(if (showCorrectLabel) "정답" else "${index + 1}",
            if (selected) Color.White else Ink, if (selected) Blue else Line,
            if (showCorrectLabel) Modifier else Modifier.clip(CircleShape))
        Text(Answers[index], Modifier.weight(1f), color = if (selected) Blue else Ink, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium, lineHeight = 17.sp)
        Text(if (selected) "✓" else "○", color = if (selected) Blue else Line, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuizButton(text: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val background = if (filled && enabled) Blue else Tint
    Text(text, Modifier.fillMaxWidth().clip(Corners).background(background)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = if (filled) 13.dp else 10.dp),
        color = if (filled && enabled) Color.White else Muted,
        fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, fontSize = if (filled) 17.sp else 13.sp)
}
