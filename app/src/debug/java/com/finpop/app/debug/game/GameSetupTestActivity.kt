package com.finpop.app.debug.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*

/** debug 빌드 전용 진입점으로 팀원 보드 화면과 분리합니다. */
class GameSetupTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var server by remember { mutableStateOf(false) }
                Column {
                    if (!server) {
                        TextButton(onClick = { server = true }) { Text("실제 서버 테스트로 이동") }
                        GameSetupTestScreen()
                    } else {
                        // 화면 전환으로 인증 정보와 진행 중 요청을 잃지 않도록 복귀는 종료 후 수행합니다.
                        GameServerTestScreen()
                    }
                }
            }
        }
    }
}
