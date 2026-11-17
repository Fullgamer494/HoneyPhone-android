package dev.honeyphone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.honeyphone.app.ui.HoneyPhoneApp
import dev.honeyphone.app.ui.HoneyPhoneTheme
import dev.honeyphone.app.ui.HoneyPhoneViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            HoneyPhoneTheme {
                HoneyPhoneApp(viewModel = viewModel<HoneyPhoneViewModel>())
            }
        }
    }
}
