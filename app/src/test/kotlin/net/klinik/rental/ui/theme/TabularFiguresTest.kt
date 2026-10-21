package net.klinik.rental.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
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
import java.io.File

/**
 * The `tabular-nums` half of the epic context's accessibility rule: comparing a
 * column of money is the owner's actual morning job, so every figure style must
 * carry the feature setting **on a face that implements it**.
 *
 * ## Why this file exists as its own test
 *
 * Nothing else in the suite can see this. `fontFeatureSettings` is a plain
 * string on a `TextStyle` with no effect on layout bounds, so deleting it from
 * the `figure` style would leave every touch-target, contrast and formatting
 * test green while `KnTableRow`'s money column stopped aligning — a silent
 * regression in the one thing the feature exists for.
 *
 * ## Why it also reads the font files
 *
 * Because the string on the style is only half the claim. A style can carry
 * `tnum` and still not apply it, if the face it names has no such feature —
 * and that is exactly the state the KPI tile was in for a while: `displayMedium`
 * named Fraunces, Fraunces has no `tnum`, and the assertion on the string
 * passed the whole time. So this file checks the **font**, not only the style.
 *
 * The font check is a byte scan for the `tnum` tag inside the file's GSUB
 * table, which is where OpenType keeps a feature's name. It is deliberately
 * crude: a raw search for the four-byte tag is enough to answer "does this
 * face offer tabular figures", and it needs no font parser.
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
        assertCarries("figureLarge", KnTypography.Clinic.figureLarge)
        assertCarries("numpadKey", KnTypography.Clinic.numpadKey)
        assertCarries("pinKey", KnTypography.Clinic.pinKey)
    }

    /**
     * The KPI tile renders its value in `moneyLarge`, and the donut's centre in
     * `figureLarge` — both body-face styles, both carrying the feature.
     */
    @Test
    fun `the KPI and donut figure styles carry tabular figures`() {
        assertCarries("moneyLarge", KnTypography.Clinic.moneyLarge)
        assertCarries("figureLarge", KnTypography.Clinic.figureLarge)
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
        // Against the **literal** the token's spec names, not against the token itself:
        // `tableCellStyle` returns `knTypography.figure.copy(fontWeight = …)`, so
        // comparing the result's size to `KnTypography.Clinic.figure.fontSize` compares
        // a value with itself and passes for any size the token might carry.
        assertEquals(
            "a numeric cell must resolve to the figure style, not the body style",
            14.sp,
            style.fontSize,
        )
        assertEquals(
            "a numeric cell must resolve to the figure style, not the label style",
            20.sp,
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
            KnTypography.Clinic.figureLarge,
            KnTypography.Clinic.moneyLarge,
            KnTypography.Clinic.numpadKey,
            KnTypography.Clinic.pinKey,
        )
        styles.map { it.fontFeatureSettings }.distinct().let { spellings ->
            assertEquals(
                "figure styles disagree on the feature spelling: $spellings",
                listOf(TABULAR_FIGURES),
                spellings,
            )
        }
    }

    /**
     * The bundled faces agree with what the styles claim.
     *
     * The gap this closes is not hypothetical. `displayMedium` carried the feature on
     * Fraunces for the whole life of the design system, and Fraunces has no `tnum` — so
     * the setting was inert on precisely the style that draws the dashboard's money
     * figures, and an assertion on the string alone could never see it.
     *
     * `FontFamily` does not carry a name — `fontFamily.name` is `FontFamily.Default` for
     * every family — so the file a style resolves to cannot be read off the style. It is
     * read off the **font resources** instead, which answers the question directly: the
     * body faces must have the feature, because every figure style renders its numbers
     * there, and the display face must not, because a style claiming a feature its face
     * cannot deliver is a claim nothing can enforce.
     */
    @Test
    fun `the bundled faces agree with what the styles claim`() {
        listOf(
            "plus_jakarta_sans_400",
            "plus_jakarta_sans_500",
            "plus_jakarta_sans_600",
            "plus_jakarta_sans_700",
            "plus_jakarta_sans_800",
        ).forEach { file ->
            assertTrue(
                "$file must carry '$TABULAR_FIGURES' — the body face is where every " +
                    "figure style renders its numbers",
                fontHasTabularFigures(fontPath(file)),
            )
        }

        listOf("fraunces_600", "fraunces_700").forEach { file ->
            assertTrue(
                "$file must NOT carry '$TABULAR_FIGURES' — the display face is for " +
                    "headings, and a style claiming a feature it cannot deliver is a lie " +
                    "the assertion on the string alone can never see",
                !fontHasTabularFigures(fontPath(file)),
            )
        }
    }

    /**
     * The display faces are the ones that must **not** claim tabular figures.
     *
     * They name Fraunces, which has no `tnum` at all. A style that claimed the
     * feature on that face would be asserting something the renderer cannot deliver —
     * which is how `displayMedium` spent its life claiming it.
     */
    @Test
    fun `the display faces do not claim a feature they cannot deliver`() {
        listOf(
            "displayLarge" to KnTypography.Clinic.displayLarge,
            "displaySmall" to KnTypography.Clinic.displaySmall,
            "title" to KnTypography.Clinic.title,
        ).forEach { (name, style) ->
            assertEquals(
                "$name names a face without '$TABULAR_FIGURES', so it must not claim it",
                null,
                style.fontFeatureSettings,
            )
        }
    }

    /**
     * The path to a bundled font file, from the test's working directory.
     *
     * The unit test's working directory is the `app` module, so the path is relative to
     * it. The file is asserted to exist, because a style naming a face the app does not
     * bundle falls back to the system font — and the system font's features are not this
     * app's, so a missing file would make the check silently vacuous.
     */
    private fun fontPath(file: String): String {
        val path = "src/main/res/font/$file.ttf"
        assertTrue(
            "the bundled font '$path' must exist",
            File(path).exists(),
        )
        return path
    }

    /**
     * Whether the font file carries the `tnum` feature.
     *
     * A byte search for the four-character tag. OpenType stores a feature's name in
     * the GSUB table as four ASCII bytes, so finding the tag is finding the feature.
     * A false positive would need the bytes to appear somewhere else in the file, which
     * for a four-letter tag inside a font is not a concern worth a parser.
     */
    private fun fontHasTabularFigures(path: String): Boolean {
        val bytes = File(path).readBytes()
        val tag = TABULAR_FIGURES.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..bytes.size - tag.size) {
            for (j in tag.indices) {
                if (bytes[i + j] != tag[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun assertCarries(what: String, style: androidx.compose.ui.text.TextStyle) {
        assertTrue(
            "$what must carry '$TABULAR_FIGURES' so figures align in a column, " +
                "was '${style.fontFeatureSettings}'",
            style.fontFeatureSettings.orEmpty().contains(TABULAR_FIGURES),
        )
    }
}
