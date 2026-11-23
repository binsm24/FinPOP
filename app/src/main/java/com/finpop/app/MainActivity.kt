package com.finpop.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import com.finpop.app.navigation.Navigation
import com.finpop.app.feature.splash.SplashScreen

class MainActivity : ComponentActivity() {
    private var showSplash by mutableStateOf(true)
    private var splashSession by mutableIntStateOf(0)

    override fun onRestart() {
        super.onRestart()
        splashSession++
        showSplash = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1D4ED8),
                    background = Color(0xFFFAF8FF),
                    surface = Color(0xFFFAF8FF),
                    onBackground = Color(0xFF0F172A),
                    onSurface = Color(0xFF0F172A),
                ),
            ) {
                Crossfade(targetState = showSplash, animationSpec = tween(350), label = "Splash to login") { splash ->
                    if (splash) {
                        key(splashSession) {
                            SplashScreen(onEnter = { showSplash = false })
                        }
                    } else {
                        Navigation(onMessage = ::showMessage)
                    }
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
