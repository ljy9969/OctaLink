package com.teamposse.striking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.teamposse.striking.data.HolidayRepository
import com.teamposse.striking.navigation.PosseApp
import com.teamposse.striking.ui.theme.TeamPosseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        HolidayRepository.init(applicationContext)
        setContent {
            TeamPosseTheme { PosseApp() }
        }
    }
}
