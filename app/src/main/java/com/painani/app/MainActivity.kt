package com.painani.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.painani.app.ui.navigation.PainaniNavHost
import com.painani.app.ui.theme.PainaniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PainaniApp).container
        setContent {
            PainaniTheme {
                PainaniNavHost(container)
            }
        }
    }
}
