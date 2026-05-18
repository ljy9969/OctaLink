package com.unboundapex.octalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.navigation.PosseApp
import com.unboundapex.octalink.ui.theme.AppThemeViewModel
import com.unboundapex.octalink.ui.theme.OctaLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 초기화는 OctaLinkApplication.onCreate 로 이전 (HolidayRepository / RepositoryProvider / KakaoSdk)
        setContent {
            // 회원이 ProfileScreen 에서 선택한 테마(다크/라이트) 를 setContent 트리 전체에 적용.
            // AppThemeViewModel 은 SharedPreferences 영속이라 앱 재시작 후에도 선택 유지.
            val themeVm: AppThemeViewModel = viewModel()
            val theme by themeVm.theme.collectAsState()
            OctaLinkTheme(theme = theme) { PosseApp() }
        }
    }
}
