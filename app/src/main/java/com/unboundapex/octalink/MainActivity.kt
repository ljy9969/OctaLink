package com.unboundapex.octalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.unboundapex.octalink.navigation.PosseApp
import com.unboundapex.octalink.ui.theme.OctaLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 초기화는 OctaLinkApplication.onCreate 로 이전 (HolidayRepository / RepositoryProvider / KakaoSdk)
        setContent {
            OctaLinkTheme { PosseApp() }
        }
    }
}
