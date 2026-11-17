package com.finpop.app.debug.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

/** debug 빌드에서만 사용하는 독립 진입점입니다. 기존 로그인·보드 화면과 연결하지 않습니다. */
class GameSetupTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GameSetupTestScreen() } }
    }
}

