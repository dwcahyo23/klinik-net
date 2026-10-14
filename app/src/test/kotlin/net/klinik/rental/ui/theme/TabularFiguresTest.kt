package net.klinik.rental.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.components.KnTableCell
import net.klinik.rental.ui.components.KnTableRow
import net.klinik.rental.ui.components.tableCellStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The `tabular-nums` half of the epic context's accessibility rule: comparing a
 * column of money is the owner's actual morning job, so every figure style must
 * carry the feature setting.
 *
 * ## Why this exists as its own test
 *
 * Nothing else in the suite can see this. `fontFeatureSettings` is a plain
 * string on a `TextStyle` with no effect on layout bounds, so deleting it from
 * the `figure` style would leave every touch-target, contrast and formatting
 * test green while `KnTableRow`'s money column stopped aligning — a silent
 * regression in the one thing the feature exists for.
 *
 * The assertion is on the *style*, not on a rendered column, because that is the
 * unit the token is defined in; a rendering assertion here would additionally
 * depend on which font actually resolved.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TabularFiguresTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the figure style carries tabular figures`() {
        assertCarries(
            "figure",
            KnTypography.Clinic.figure,
        )
    }

    @Test
    fun `the money and key styles carry tabular figures`() {
        assertCarries("moneyLarge", KnTypography.Clinic.moneyLarge)
        assertCarries("numpadKey", KnTypography.Clinic.numpadKey)
        assertCarries("pinKey", KnTypography.Clinic.pinKey)
    }

    /**
     * The KPI tile renders its value in `displayMedium`, so that style is in
     * scope too even though the display face has no `onum` figures to switch to.
     */
    @Test
    fun `the KPI value style carries tabular figures`() {
        assertCarries("displayMedium", KnTypography.Clinic.displayMedium)
    }

    /**
 * A numeric table cell resolves to the tabular style — asserted through the
 * component's own resolution, not a locally rebuilt style.
 *
 * An earlier version composed a `KnTableRow` and then asserted on
 * `KnTypography.Clinic.figure.copy(fontWeight = Bold)`, which is the test's
 * *guess* at what the cell uses. Repointing the numeric branch of
 * `tableCellStyle` at any other style would have left that assertion green while
 * the money column stopped aligning — the test would have been asserting on
 * itself. So the style is read from the same function `KnTableRow` calls.
 */
@Test
    fun `a numeric table cell resolves to the tabular figure style`() {
        val resolved = mutableListOf<TextStyle>()
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                // The real row, so a change to its cell styling is exercised.
                KnTableRow(
                    cells = listOf(
                        KnTableCell("B 1235 TF"),
                        KnTableCell("Rp 350.000", numeric = true),
                    ),
                )
                // And the exact style that row resolves for that numeric cell.
                resolved += tableCellStyle(KnTableCell("Rp 350.000", numeric = true))
            }
        }
        compose.waitForIdle()

        val style = resolved.last()
        assertCarries("numeric cell style", style)
        assertEquals(
            "a numeric cell must resolve to the figure style, not the body style",
            KnTypography.Clinic.figure.fontSize,
            style.fontSize,
        )
        assertEquals(
            "a numeric cell must resolve to the figure style, not the label style",
            KnTypography.Clinic.figure.lineHeight,
            style.lineHeight,
        )
    }

    /**
     * Every figure style must carry the *same* feature string, so a future style
     * cannot quietly spell it differently — `tnum` and `tnum ` are both accepted
     * by the shaper but only one is the token.
     */
    @Test
    fun `every figure style spells the feature setting identically`() {
        val styles = listOf(
            KnTypography.Clinic.figure,
            KnTypography.Clinic.moneyLarge,
            KnTypography.Clinic.numpadKey,
            KnTypography.Clinic.pinKey,
            KnTypography.Clinic.displayMedium,
        )
        styles.map { it.fontFeatureSettings }.distinct().let { spellings ->
            assertEquals(
                "figure styles disagree on the feature spelling: $spellings",
                listOf(TABULAR_FIGURES),
                spellings,
            )
        }
    }

    private fun assertCarries(what: String, style: androidx.compose.ui.text.TextStyle) {
        assertTrue(
            "$what must carry '$TABULAR_FIGURES' so figures align in a column, " +
                "was '${style.fontFeatureSettings}'",
            style.fontFeatureSettings.orEmpty().contains(TABULAR_FIGURES),
        )
    }
}