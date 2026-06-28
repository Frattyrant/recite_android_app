package com.miearn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.miearn.app.ui.MIearnApp
import com.miearn.app.ui.MainViewModel
import com.miearn.app.ui.theme.MIearnTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MIearnTheme {
                MIearnApp(viewModel)
            }
        }
    }
}

