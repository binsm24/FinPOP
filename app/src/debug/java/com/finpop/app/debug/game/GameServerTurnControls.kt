package com.finpop.app.debug.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** 서버 상태에 맞는 조작만 제공하며 구매 가격·차감 턴은 클라이언트가 정하지 않습니다. */
@Composable
internal fun GameServerTurnControls(
    game: JSONObject, blocked: Boolean, onPurchase: (String) -> Unit,
    onComplete: () -> Unit, onSkipIsland: () -> Unit, onAiFollowup: () -> Unit,
) {
    val phase = game.optString("phase")
    val actor = game.optString("currentPlayerId")
    if (phase == "PURCHASE_PENDING") {
        val purchase = game.getJSONObject("pendingPurchase")
        val price = purchase.getInt("price")
        val players = game.getJSONArray("players")
        val gold = (0 until players.length()).map { players.getJSONObject(it) }
            .single { it.getString("playerId") == actor }.getInt("gameGold")
        if (actor == "P1") {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !blocked && gold >= price, onClick = { onPurchase("BUY") }) { Text("구매 ${price}G") }
                OutlinedButton(enabled = !blocked, onClick = { onPurchase("PASS") }) { Text("패스") }
            }
            if (gold < price) Text("구매 금액이 부족합니다. 패스를 선택하세요.",
                Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        } else TextButton(enabled = !blocked, onClick = onAiFollowup) { Text("AI 구매·패스 진행") }
    }
    if (phase == "TURN_REVIEW") {
        TextButton(enabled = !blocked, onClick = onComplete) {
            Text(if (actor == "P2") "AI 차례 마무리" else "차례 종료")
        }
    }
    if (phase == "TURN_READY" && actor in listOf("P1", "P2") && game.islandWaitFor(actor) > 0) {
        TextButton(enabled = !blocked, onClick = onSkipIsland) { Text("무인도 대기 · 1턴 소비") }
    }
}
