package com.finpop.app.debug.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import com.finpop.app.feature.game.GameBoardPlayerId
import kotlinx.coroutines.delay

private const val SERVER = "https://xzxbhbdartmcyvoslwsl.supabase.co"
private data class PendingRequest(val path: String, val body: String, val key: String)

/** 인증 정보는 이 화면의 메모리에만 보관하고 로그나 저장소에 기록하지 않습니다. */
@Composable
fun GameServerTestScreen() {
    var publicKey by remember { mutableStateOf("") }
    var inputToken by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var game by remember { mutableStateOf<JSONObject?>(null) }
    var gameId by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PendingRequest?>(null) }
    var output by remember { mutableStateOf("테스트 인증 정보를 설정한 뒤 게임을 생성하거나 기존 게임 ID를 입력하세요.") }
    var busy by remember { mutableStateOf(false) }
    var showBoard by remember { mutableStateOf(false) }
    var lastDice by remember { mutableIntStateOf(1) }
    var hasDiceResult by remember { mutableStateOf(false) }
    var lastRoller by remember { mutableStateOf(GameBoardPlayerId.P1) }
    var animatedPosition by remember { mutableStateOf<Int?>(null) }
    var rolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { output = "통신 실패. 변경 요청은 같은 요청 재시도로 확인하세요." }
            finally { busy = false }
        }
    }

    suspend fun request(path: String, method: String, body: String?, key: String? = null, auth: String = token): Pair<Int, JSONObject> =
        withContext(Dispatchers.IO) {
            val connection = URL(SERVER + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10000
                connection.readTimeout = 20000
                connection.setRequestProperty("apikey", publicKey.trim())
                if (auth.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $auth")
                if (key != null) connection.setRequestProperty("Idempotency-Key", key)
                if (body != null) {
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                status to JSONObject(text)
            } finally { connection.disconnect() }
        }

    fun accept(status: Int, response: JSONObject) {
        output = "HTTP $status\n" + response.toString(2)
        if (status in 200..299) {
            val data = response.optJSONObject("data")
            val snapshot = data?.optJSONObject("game") ?: data
            if (snapshot?.has("gameId") == true) {
                if (game?.optString("gameId") != snapshot.getString("gameId")) {
                    lastDice = 1
                    hasDiceResult = false
                    animatedPosition = null
                }
                game = snapshot
                gameId = snapshot.getString("gameId")
            }
        }
    }

    suspend fun send(change: PendingRequest) {
        rolling = change.path.endsWith("/rolls")
        try {
            val (status, response) = request("/functions/v1/finpop-api" + change.path, "POST", change.body, change.key)
            val data = response.optJSONObject("data")
            val path = if (status in 200..299) data?.optJSONArray("movementPath") else null
            if (path != null) {
                // 재시도 응답도 서버가 확정한 값과 경로만 표시합니다.
                val players = game?.optJSONArray("players")
                animatedPosition = players?.let { list ->
                    (0 until list.length()).map { list.getJSONObject(it) }
                        .firstOrNull { it.optString("playerId") == "P1" }?.getInt("position")
                }
                lastDice = requireNotNull(data).getInt("diceValue")
                hasDiceResult = true
                lastRoller = GameBoardPlayerId.P1
            }
            accept(status, response)
            // HTTP 응답이 확인되기 전에는 같은 요청 키를 유지합니다.
            pending = null
            rolling = false
            if (path != null) {
                for (index in 0 until path.length()) {
                    animatedPosition = path.getInt(index)
                    delay(180)
                }
            }
        } finally {
            rolling = false
            animatedPosition = null
        }
    }

    fun mutate(path: String, body: JSONObject) {
        val change = PendingRequest(path, body.toString(), UUID.randomUUID().toString())
        pending = change
        run { send(change) }
    }

    val boardGame = game
    if (showBoard && boardGame != null) {
        GameServerBoardScreen(
            game = boardGame,
            state = boardGame.toServerBoardState(lastDice, lastRoller, rolling,
                busy || pending != null, animatedPosition),
            busy = busy, canRetry = pending != null, responseText = output,
            hasDiceResult = hasDiceResult,
            onBack = { showBoard = false },
            onRefresh = {
                if (!busy && pending == null) run {
                    val (status, response) = request(
                        "/functions/v1/finpop-api/v1/games/" + boardGame.getString("gameId"), "GET", null)
                    accept(status, response)
                }
            },
            onInitiative = {
                if (!busy && pending == null && boardGame.optString("phase") == "INITIATIVE_PENDING")
                    mutate("/v1/games/" + boardGame.getString("gameId") + "/initiative",
                        JSONObject().put("expectedVersion", boardGame.getInt("stateVersion")))
            },
            onRoll = {
                if (boardGame.toServerBoardState(lastDice, lastRoller, false,
                        busy || pending != null, null).canRollDice)
                    mutate("/v1/games/" + boardGame.getString("gameId") + "/rolls",
                        JSONObject().put("expectedVersion", boardGame.getInt("stateVersion")))
            },
            onRetry = { pending?.let { change -> if (!busy) run { send(change) } } },
        )
        return
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("실제 서버 초기 설정·주사위 테스트", style = MaterialTheme.typography.titleLarge)
        Text("실제 DB에 테스트 게임이 저장됩니다. AI 차례·퀴즈 답변 이후 처리는 아직 지원하지 않습니다.")
        if (token.isBlank()) {
            OutlinedTextField(publicKey, { publicKey = it }, label = { Text("프로젝트 anon 또는 publishable 공개 키") }, enabled = !busy)
            Text("테스트 계정의 access_token을 입력하세요. anon 키나 service_role 키를 사용자 토큰으로 넣지 마세요.")
            OutlinedTextField(inputToken, { inputToken = it }, label = { Text("테스트 사용자 access_token") },
                visualTransformation = PasswordVisualTransformation(), enabled = !busy)
            Button(enabled = !busy && publicKey.isNotBlank() && inputToken.isNotBlank(), onClick = {
                token = inputToken.trim()
                inputToken = ""
                output = "테스트 인증 정보 설정 완료. 게임 API 요청 시 서버에서 검증합니다."
            }) { Text("테스트 인증 정보 사용") }
        } else {
            Text("테스트 인증 정보 설정됨 · 화면을 닫으면 사라집니다.")
            Button(enabled = !busy && pending == null && game == null, onClick = {
                mutate("/v1/games", JSONObject().put("mode", "AI").put("characterId", "CHEESE"))
            }) { Text("1. 게임 생성") }
            OutlinedTextField(gameId, { gameId = it }, label = { Text("조회할 게임 ID") }, enabled = !busy && pending == null)
            Button(enabled = !busy && gameId.isNotBlank() && pending == null, onClick = {
                run {
                    val id = UUID.fromString(gameId.trim()).toString()
                    val (status, response) = request("/functions/v1/finpop-api/v1/games/$id", "GET", null)
                    accept(status, response)
                }
            }) { Text("게임 상태 조회") }
            val current = game
            if (current != null) {
                Button(enabled = !busy, onClick = { showBoard = true }) { Text("서버 상태로 보드 열기") }
                val phase = current.optString("phase")
                Text("상태: $phase · 현재 차례: " + current.optString("currentPlayerId"))
                Button(enabled = !busy && pending == null && phase == "INITIATIVE_PENDING", onClick = {
                    mutate("/v1/games/${current.getString("gameId")}/initiative",
                        JSONObject().put("expectedVersion", current.getInt("stateVersion")))
                }) { Text("2. 선공 결정") }
                Button(enabled = !busy && pending == null && phase == "TURN_READY" && current.optString("currentPlayerId") == "P1", onClick = {
                    mutate("/v1/games/${current.getString("gameId")}/rolls",
                        JSONObject().put("expectedVersion", current.getInt("stateVersion")))
                }) { Text("3. 주사위 굴리기") }
                if (current.optString("currentPlayerId") == "P2") Text("AI 선공입니다. 현재 서버는 AI 차례 처리를 지원하지 않습니다.")
            }
            pending?.let { change ->
                Button(enabled = !busy, onClick = { run { send(change) } }) { Text("같은 요청 재시도") }
            }
        }
        if (busy) CircularProgressIndicator()
        Text(output)
    }
}
