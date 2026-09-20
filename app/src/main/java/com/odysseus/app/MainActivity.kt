package com.odysseus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.odysseus.app.ui.navigation.OdysseusNavHost
import com.odysseus.app.ui.theme.OdysseusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OdysseusApp).container
        setContent {
            OdysseusTheme {
                OdysseusNavHost(container)
            }
        }
    }
}
