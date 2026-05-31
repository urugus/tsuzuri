package com.urugus.tsuzuri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.urugus.tsuzuri.ui.TsuzuriApp
import com.urugus.tsuzuri.ui.theme.TsuzuriTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TsuzuriTheme {
                TsuzuriApp()
            }
        }
    }
}
