package com.unboundapex.octalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.unboundapex.octalink.data.HolidayRepository
import com.unboundapex.octalink.navigation.PosseApp
import com.unboundapex.octalink.ui.theme.TeamPosseTheme

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
