package dev.honeyphone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import dev.honeyphone.app.ui.HoneyPhoneApp
import dev.honeyphone.app.ui.HoneyPhoneTheme
import dev.honeyphone.app.ui.HoneyPhoneViewModel
import dev.honeyphone.app.core.work.PeriodicReviewScheduler

class MainActivity : ComponentActivity() {
    private lateinit var honeyPhoneViewModel: HoneyPhoneViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PeriodicReviewScheduler.ensureScheduled(applicationContext)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        honeyPhoneViewModel = androidx.lifecycle.ViewModelProvider(this)[HoneyPhoneViewModel::class.java]
        setContent {
            HoneyPhoneTheme {
                HoneyPhoneApp(viewModel = honeyPhoneViewModel)
            }
        }
    }

    override fun onStop() {
        if (::honeyPhoneViewModel.isInitialized) honeyPhoneViewModel.stopHoneypot()
        super.onStop()
    }
}
