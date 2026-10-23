package net.klinik.rental.ui.screens.mobil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnCheckbox
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.theme.knSpacing

/**
 * The vehicle form, as a Material3 `ModalBottomSheet`.
 *
 * ## Why a sheet and not a `KnModal`
 *
 * Seven fields plus an on-screen numpad is exactly the "many inputs or a tall
 * keyboard" case the epic context names: on a tablet a centred dialog covers the
 * numpad it exists to serve, and the owner has to dismiss the thing he opened in
 * order to reach the input. A sheet grows from the bottom edge, so the numpad
 * stays above the keyboard area and reachable at every height.
 *
 * `ModalBottomSheet` is already on the classpath, so this uses the native
 * component rather than adding a second sheet to the design system — the design
 * system has a *modal* and this is deliberately not one.
 *
 * ## Why the rate is a numpad and not a text field
 *
 * The epic context: "Nominal entry uses the on-screen numpad, never the system
 * keyboard — it is used while standing and holding something, so large touch
 * targets beat typing speed." The rate is the one field on this screen that is
 * typed standing up, so it is the one that gets the numpad.
 *
 * `KnNumPad` re-syncs from `value` through a `LaunchedEffect`, so
 * [VehicleFormState.rateDigits] must hold exactly what the pad emits — a
 * grouped `"Rp 250.000"` would be normalised to something else and the owner's
 * digits would be discarded.
 *
 * @param state the form's own state, held by the ViewModel so a rotation cannot
 *   lose half-typed input.
 * @param onDismissRequest the sheet's scrim tap and back gesture. Both are wired:
 *   a sheet with no way out except its own buttons is a trap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormSheet(
    state: VehicleFormState,
    onDismissRequest: () -> Unit,
    onChange: ((VehicleFormState) -> VehicleFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    // Half the screen rather than a fixed dp: the numpad is the tallest content
    // and it has to fit with the fields above it. `skipPartiallyExpanded` so a
    // second tap on the add button does not find the sheet at half height.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // **No `heightIn` here.** A cap on the sheet's own modifier sizes the sheet's
        // window to the cap and leaves it floating above the bottom edge with the scrim
        // showing underneath. `ModalBottomSheet` already bounds itself to the window and
        // anchors to the bottom; the cap belongs on the content, below.
        modifier = modifier.testTag(TAG_SHEET),
    ) {
        // The outer column is a **bound**, not a fill: `heightIn(max = …)` wraps the
        // content up to the cap. `fillMaxHeight()` here would make the sheet claim the
        // whole window for a two-field form.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SheetMaxHeight)
                // The keyboard. djustResize shrinks the window; this keeps the sheet's action
                // row above the IME rather than under it.
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The sheet's **only** scroll container. Without it the form was
                    // clipped at the cap with the numpad's bottom rows and both action
                    // buttons below the cut — the sheet showed a form with no way out of
                    // it, which is the bug the owner reported as "Tambah mobil masih error".
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.lg)
                    // Natural height, up to what the pinned action row leaves. `fill = true`
                    // would open a sheet of mostly whitespace for a short form.
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
            KnField(
                label = stringResource(R.string.kendaraan_field_plate),
                value = state.plate,
                onValueChange = { value ->
                    onChange { it.copy(plate = value, plateError = null) }
                },
                hint = stringResource(R.string.kendaraan_plate_hint),
                error = state.plateError,
                modifier = Modifier.testTag(TAG_FIELD_PLATE),
            )

            // Brand, model and year on one wrapped row where the width allows:
            // they are three short values the owner reads as a row anyway, and
            // stacking them would push the numpad off a short screen. `FlowRow`
            // so a narrow phone wraps rather than squeezing three fields into
            // unreadable columns.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                maxItemsInEachRow = 3,
            ) {
                KnField(
                    label = stringResource(R.string.kendaraan_field_brand),
                    value = state.brand,
                    onValueChange = { value -> onChange { it.copy(brand = value) } },
                    modifier = Modifier
                        .testTag(TAG_FIELD_BRAND)
                        .weight(1f),
                )
                KnField(
                    label = stringResource(R.string.kendaraan_field_model),
                    value = state.model,
                    onValueChange = { value -> onChange { it.copy(model = value) } },
                    modifier = Modifier
                        .testTag(TAG_FIELD_MODEL)
                        .weight(1f),
                )
                KnField(
                    label = stringResource(R.string.kendaraan_field_year),
                    value = state.year,
                    onValueChange = { value -> onChange { it.copy(year = value.take(4)) } },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier
                        .testTag(TAG_FIELD_YEAR)
                        .weight(1f),
                )
            }

            KnField(
                label = stringResource(R.string.kendaraan_field_color),
                value = state.color,
                onValueChange = { value -> onChange { it.copy(color = value) } },
                modifier = Modifier.testTag(TAG_FIELD_COLOR),
            )

            // The rate, typed on the **system numeric keyboard**.
            //
            // It was a `KnNumPad` — a 4×3 grid of 66dp keys — and that made this sheet tall
            // enough to need scrolling for seven short fields. The owner's instruction:
            // *"untuk bottomsheet tambah mobil untuk input tarif harian jangan pakai numpad,
            // pakai input number keyboard android saya agar bottomsheet tidak panjang atau
            // scroll scroll, numpad hanya ada di screen setoran."*
            //
            // So the custom numpad is now Setoran's alone. Every other numeric entry in the
            // app — this rate, a year, an account number, a phone number — uses the platform
            // keyboard, which the owner already knows and which costs the sheet no height
            // when it is closed.
            KnField(
                label = stringResource(R.string.kendaraan_field_rate),
                value = state.rateDigits,
                // Digits only, normalised as they arrive: the field shows what will be
                // stored, so a pasted "250.000" becomes 250000 rather than a value the
                // parser has to guess at.
                onValueChange = { raw ->
                    val digits = raw.filter { it in '0'..'9' }.take(MaxRateDigits)
                    onChange { it.copy(rateDigits = digits, rateError = null) }
                },
                hint = stringResource(R.string.kendaraan_rate_hint),
                error = state.rateError,
                leading = stringResource(R.string.kendaraan_rate_affix),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.testTag(TAG_FIELD_RATE),
            )

            // The manual service marker, set by the owner rather than derived:
            // the one status input that is stored (see `Vehicle.isServis`). It
            // sits above the notes field, with the form's other attributes.
            KnCheckbox(
                label = stringResource(R.string.kendaraan_form_servis_label),
                hint = stringResource(R.string.kendaraan_form_servis_hint),
                checked = state.isServis,
                onCheckedChange = { value -> onChange { it.copy(isServis = value) } },
                modifier = Modifier.testTag(TAG_SERVIS),
            )

            KnField(
                label = stringResource(R.string.kendaraan_field_notes),
                value = state.notes,
                onValueChange = { value -> onChange { it.copy(notes = value) } },
                modifier = Modifier.testTag(TAG_FIELD_NOTES),
            )
            } // end of the scrolling column

            // The action row is a **sibling** of the scroll container, not its last child.
            // Inside it, the buttons scrolled with the form and sat below the cap, so they
            // were never on screen. Pinned here they are reachable at any form length.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.End),
                maxItemsInEachRow = 2,
            ) {
                KnButton(
                    label = stringResource(R.string.kendaraan_cancel),
                    onClick = onDismissRequest,
                    variant = KnButtonVariant.Ghost,
                    modifier = Modifier.testTag(TAG_CANCEL),
                )
                KnButton(
                    label = stringResource(R.string.kendaraan_save),
                    onClick = onSave,
                    variant = KnButtonVariant.Primary,
                    modifier = Modifier.testTag(TAG_SAVE),
                )
            }
        }
    }
}

/**
 * A bound on the sheet's content.
 *
 * `ModalBottomSheet` already constrains itself to the window; this stops seven
 * fields plus a 66dp-key numpad from growing past a tall tablet and pushing the
 * Save button off the top. The sheet's own scroll container handles the rest.
 */
private val SheetMaxHeight = 720.dp

/** The longest rate the field accepts, in digits: Rp 999.999.999. */
private const val MaxRateDigits = 9

internal const val TAG_SHEET = "vehicle-form-sheet"
internal const val TAG_FIELD_PLATE = "vehicle-field-plate"
internal const val TAG_FIELD_BRAND = "vehicle-field-brand"
internal const val TAG_FIELD_MODEL = "vehicle-field-model"
internal const val TAG_FIELD_YEAR = "vehicle-field-year"
internal const val TAG_FIELD_COLOR = "vehicle-field-color"
internal const val TAG_FIELD_RATE = "vehicle-field-rate"
internal const val TAG_FIELD_NOTES = "vehicle-field-notes"
internal const val TAG_NUMPAD = "vehicle-numpad"
internal const val TAG_SERVIS = "vehicle-servis"
internal const val TAG_SAVE = "vehicle-save"
internal const val TAG_CANCEL = "vehicle-cancel"