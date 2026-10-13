package net.klinik.rental

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import java.time.LocalDate

/**
 * Story 1.1 is scaffolding only — no feature UI yet.
 *
 * `LocalDate.now()` is called here deliberately rather than somewhere in the
 * domain layer: on API 24/25 `java.time` only exists because of core library
 * desugaring. If that config is ever lost, this line is where it crashes —
 * visibly, at startup — instead of failing silently later.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val today = LocalDate.now()

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text(
                        text = "Klinik.Net — $today",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}