package com.finpop.app.debug.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import kotlin.math.ceil

/** 팀원 보드는 그대로 사용하고 퀴즈 상호작용만 debug 연결 계층에 둡니다. */
@Composable
internal fun GameServerQuizControls(
    game: JSONObject, blocked: Boolean,
    onAnswer: (Int) -> Unit, onTimeout: () -> Unit, onAiAnswer: () -> Unit,
) {
    val quiz = game.optJSONObject("pendingQuiz")
    val attempt = quiz?.optString("attemptId")
    val isUser = game.optString("currentPlayerId") == "P1"
    var showQuiz by remember(attempt) { mutableStateOf(quiz != null && isUser) }
    var seconds by remember(attempt) { mutableIntStateOf(20) }
    var timeoutSent by remember(attempt) { mutableStateOf(false) }
    val latestTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(attempt, blocked) {
        if (quiz == null) return@LaunchedEffect
        val deadline = Instant.parse(quiz.getString("deadlineAt")).toEpochMilli()
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            seconds = ceil(remaining.coerceAtLeast(0) / 1000.0).toInt()
            if (remaining <= 0) {
                if (isUser && !blocked && !timeoutSent) {
                    timeoutSent = true
                    latestTimeout()
                }
                break
            }
            delay(200)
        }
    }
    if (quiz != null) {
        Row {
            TextButton(onClick = { showQuiz = true }) { Text("퀴즈 보기 · ${seconds}초") }
            if (!isUser) TextButton(enabled = !blocked, onClick = onAiAnswer) { Text("AI 판정 다시 시도") }
            else if (seconds == 0) TextButton(enabled = !blocked, onClick = onTimeout) { Text("시간 초과 확인") }
        }
    }
    if (quiz != null && showQuiz) {
        AlertDialog(
            onDismissRequest = { showQuiz = false },
            title = { Text(if (isUser) "퀴즈 · ${seconds}초" else "AI 퀴즈") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(quiz.getString("question"))
                    val choices = quiz.getJSONArray("choices")
                    for (index in 0 until choices.length()) {
                        val choice = choices.getJSONObject(index)
                        OutlinedButton(
                            enabled = isUser && !blocked && seconds > 0,
                            onClick = { onAnswer(choice.getInt("id")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${choice.getInt("id")}. ${choice.getString("text")}") }
                    }
                    Text("표시 시간은 기기 기준이며 최종 마감은 서버가 판정합니다.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showQuiz = false }) { Text("보드로 돌아가기") } },
        )
    }
    val result = game.optJSONObject("lastQuizResult")
    val resultAttempt = result?.optString("attemptId")
    var showResult by remember(resultAttempt) { mutableStateOf(result != null) }
    if (result != null) {
        TextButton(onClick = { showResult = true }) { Text("퀴즈 판정 결과") }
    }
    if (result != null && showResult) {
        val resolution = when (result.getString("resolution")) {
            "CORRECT" -> "정답"
            "TIMEOUT" -> "시간 초과"
            else -> "오답"
        }
        AlertDialog(onDismissRequest = { showResult = false },
            title = { Text("${result.getString("playerId")} · $resolution") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.getString("question"))
                    Text("정답: ${result.getInt("correctChoiceId")}번")
                    Text(result.getString("explanation"))
                    Text(if (game.optString("phase") == "PURCHASE_PENDING")
                        "구매 선택 대기입니다. 구매·패스 API는 다음 단계에서 연결합니다."
                    else "정산이 필요한 경우 잔액에 반영됐습니다. 차례 종료는 다음 단계에서 연결합니다.")
                }
            },
            confirmButton = { TextButton(onClick = { showResult = false }) { Text("닫기") } })
    }
}
