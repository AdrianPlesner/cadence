package dk.azp.cadence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dk.azp.cadence.ui.CadenceNavHost
import dk.azp.cadence.ui.LocalAppContainer
import dk.azp.cadence.ui.theme.CadenceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CadenceApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                CadenceTheme {
                    CadenceNavHost()
                }
            }
        }
    }
}
