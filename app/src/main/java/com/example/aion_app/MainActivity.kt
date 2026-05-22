package com.example.aion_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.aion_app.navigation.AionNavHost
import com.example.aion_app.ui.theme.AionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AionTheme {
                AionNavHost()
            }
        }
    }
}