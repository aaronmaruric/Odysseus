package com.painani.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painani.app.ui.theme.PainaniTheme

/**
 * Health Connect requires every client to expose a privacy policy screen. It is launched from
 * the system permission dialog and from Settings > Health Connect > app permissions.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PainaniTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    ) {
                        Text("HEALTH DATA", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = """
                                Painani is a personal training log. Everything it stores stays on this phone.

                                What it reads from Health Connect
                                • Heart rate — to attach average and maximum heart rate to your runs and each kilometre split.
                                • Steps, distance, active calories, exercise time, sleep (with stages) and resting heart rate — cached on the phone and charted on the Stats page.
                                • Weight — weigh-ins from your watch, scale or Samsung Health are added to your weight log.

                                What it writes to Health Connect
                                • Your runs (with GPS route and distance) and strength sessions, so other health apps can see them.
                                • Weights you log in Painani.

                                Nothing is sent to any server. There is no account and no analytics. You can revoke access at any time in Health Connect settings; Painani keeps working without it.
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
