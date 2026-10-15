package net.klinik.rental.ui.screens.kendaraan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import net.klinik.rental.ui.components.KnNumPad
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
        modifier = modifier.testTag(TAG_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .heightIn(max = SheetMaxHeight),
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

            KnField(
                label = stringResource(R.string.kendaraan_field_rate),
                value = state.rateDigits,
                onValueChange = { value ->
                    // Echoed straight back: `KnNumPad` normalises whatever it is
                    // given and re-syncs from `value`, so a caller that clamped
                    // or reformatted would see its own digits discarded.
                    onChange { it.copy(rateDigits = value, rateError = null) }
                },
                hint = stringResource(R.string.kendaraan_rate_hint),
                error = state.rateError,
                // A static affix, not an input: the digits come from the numpad.
                leading = stringResource(R.string.kendaraan_rate_affix),
                enabled = false,
                modifier = Modifier.testTag(TAG_FIELD_RATE),
            )

            // The numpad itself. `onSave` here only *commits the rate digits* —
            // it does not submit the form. A single Save key cannot mean two
            // things, and "I finished the rate" is a different intent from "I
            // finished the whole car".
            KnNumPad(
                value = state.rateDigits,
                onValueChange = { digits ->
                    onChange { it.copy(rateDigits = digits, rateError = null) }
                },
                onSave = { amount ->
                    onChange { it.copy(rateDigits = amount.toString(), rateError = null) }
                },
                modifier = Modifier.testTag(TAG_NUMPAD),
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
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