package com.finpop.app.debug.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finpop.app.data.game.GameRules
import com.finpop.app.feature.game.GameBoardUiState
import com.finpop.app.feature.game.MainGameBoardContent
import org.json.JSONObject

/** 로그인·일반 앱 이동 흐름을 바꾸지 않는 실제 서버 검증용 보드입니다. */
@Composable
internal fun GameServerBoardScreen(
    game: JSONObject, state: GameBoardUiState,
    busy: Boolean, canRetry: Boolean, responseText: String, hasDiceResult: Boolean,
    onBack: () -> Unit, onRefresh: () -> Unit, onInitiative: () -> Unit,
    onRoll: () -> Unit, onRetry: () -> Unit,
) {
    var details by remember { mutableStateOf<String?>(null) }
    BackHandler { onBack() }
    val phase = game.getString("phase")
    val status = when {
        phase == "INITIATIVE_PENDING" -> "선공 결정 전입니다."
        phase == "QUIZ_OPEN" -> "퀴즈가 발급됐습니다. 답변 처리는 아직 지원하지 않습니다."
        game.optString("currentPlayerId") == "P2" -> "AI 차례입니다. AI 이동은 아직 지원하지 않습니다."
        phase != "TURN_READY" -> "이동 후 처리 대기: $phase"
        else -> "사용자 차례입니다. 보드 중앙 주사위를 눌러주세요."
    }
    Surface {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("테스트 설정") }
                TextButton(enabled = !busy && !canRetry, onClick = onRefresh) { Text("상태 새로고침") }
                TextButton(onClick = { details = responseText }) { Text("응답 보기") }
            }
            Text(status, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            if (!hasDiceResult) Text("이동 주사위 결과 대기 · 보드의 초기 주사위 그림은 결과가 아닙니다.",
                Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            if (phase == "INITIATIVE_PENDING") {
                Button(enabled = !busy && !canRetry, onClick = onInitiative,
                    modifier = Modifier.padding(horizontal = 12.dp)) { Text("선공 결정") }
            }
            if (canRetry && !busy) {
                Button(onClick = onRetry, modifier = Modifier.padding(horizontal = 12.dp)) { Text("같은 요청 재시도") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            MainGameBoardContent(
                state = state,
                onRollDice = { if (state.canRollDice) onRoll() },
                onTileClick = { boardId ->
                    val tile = GameRules.tiles.single { it.tileId == boardTileToServer(boardId) }
                    details = "서버 칸 ${tile.tileId} · ${tile.topic} · ${tile.price}G"
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Text(responseText.lineSequence().firstOrNull().orEmpty(),
                Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            val quiz = game.optJSONObject("pendingQuiz")
            if (quiz != null) TextButton(onClick = {
                val choices = quiz.getJSONArray("choices")
                details = quiz.getString("question") + "\n\n" +
                    (0 until choices.length()).joinToString("\n") { index ->
                        val choice = choices.getJSONObject(index)
                        "${choice.getInt("id")}. ${choice.getString("text")}"
                    } + "\n\n제한 시간: " + quiz.getString("deadlineAt") +
                    "\n답변 API는 아직 연결되지 않았습니다."
            }) { Text("발급된 퀴즈 확인") }
        }
    }
    details?.let { text ->
        AlertDialog(onDismissRequest = { details = null }, title = { Text("서버 상태 확인") },
            text = { Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { details = null }) { Text("닫기") } })
    }
}
