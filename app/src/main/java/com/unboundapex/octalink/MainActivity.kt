package com.unboundapex.octalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.unboundapex.octalink.navigation.PosseApp
import com.unboundapex.octalink.ui.theme.AppThemeStore
import com.unboundapex.octalink.ui.theme.OctaLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 초기화는 OctaLinkApplication.onCreate 로 이전 (HolidayRepository / RepositoryProvider / KakaoSdk)
        // 테마 싱글톤 — SharedPreferences 동기 로딩. setContent 전에 초기값 확정 → 첫 프레임부터 정확한 테마.
        AppThemeStore.init(this)
        setContent {
            // ProfileScreen 과 같은 싱글톤 StateFlow 를 구독해 토글 즉시 전체 트리 재구성.
            val theme by AppThemeStore.theme.collectAsState()
            OctaLinkTheme(theme = theme) { PosseApp() }
        }
    }
}
