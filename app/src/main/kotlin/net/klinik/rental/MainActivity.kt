package net.klinik.rental

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knTypography
import java.time.LocalDate

/**
 * The single Activity for the app.
 *
 * `LocalDate.now()` is called here deliberately rather than somewhere in the
 * domain layer: on API 24/25 `java.time` only exists because of core library
 * desugaring. If that config is ever lost, this line is where it crashes —
 * visibly, at startup — instead of failing silently later.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val today = LocalDate.now()

        setContent {
            KlinikNetTheme(darkTheme = isSystemInDarkTheme()) {
                KlinikNetApp(today = today)
            }
        }
    }
}

/**
 * Placeholder content until Story 2.2 replaces it with the vehicle-fleet screen.
 *
 * The date is passed in rather than read here so this composable stays free of
 * a clock: "today" is the domain `Clock`'s job, and a UI-layer
 * `LocalDate.now()` would be a second source of truth that a test could not
 * pin.
 */
@Composable
private fun KlinikNetApp(today: LocalDate) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Text(
            text = stringResource(R.string.home_placeholder_body),
            style = MaterialTheme.knTypography.bodyLarge,
        )
    }
}