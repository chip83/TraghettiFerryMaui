package it.traghetti.maddalena.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Departure
import it.traghetti.maddalena.purchase.BookingRequest
import it.traghetti.maddalena.purchase.Vehicle
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Riepilogo della corsa e opzioni da precompilare sul sito della compagnia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseSheet(
    departure: Departure,
    defaultResident: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (BookingRequest) -> Unit,
) {
    var adults by remember { mutableIntStateOf(1) }
    var children by remember { mutableIntStateOf(0) }
    var vehicle by remember { mutableStateOf(Vehicle.NONE) }
    var resident by remember { mutableStateOf(defaultResident) }
    val delcomar = departure.company == Company.DELCOMAR

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${departure.from.displayName} → ${departure.to.displayName}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        departure.departure.format(DateTimeFormatter.ofPattern("EEEE d MMMM 'alle' HH:mm", Locale.ITALIAN)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CompanyBadge(departure.company)
            }

            if (delcomar) {
                Stepper("Adulti", adults, 1..9) { adults = it }
                Stepper("Bambini 6-11 anni", children, 0..9) { children = it }
                VehicleField(vehicle) { vehicle = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tariffa residenti", style = MaterialTheme.typography.bodyLarge)
                        Text("Serve un documento che attesti la residenza", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = resident, onCheckedChange = { resident = it })
                }
            } else {
                Text(
                    "Si aprirà la prenotazione Caronte & Tourist con la tratta già impostata: data, corsa e passeggeri si scelgono nei passaggi successivi del sito.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    onConfirm(
                        BookingRequest(
                            company = departure.company,
                            from = departure.from,
                            date = departure.departure.toLocalDate(),
                            time = departure.departure.toLocalTime(),
                            adults = adults, children = children, vehicle = vehicle, resident = resident && delcomar,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sheet_continue"),
            ) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Continua sul sito ${departure.company.operatedBy}")
            }
            Text(
                "L'acquisto avviene sul sito ufficiale della compagnia, dentro l'app. Il pagamento è gestito dal loro circuito sicuro.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Rounded.Remove, contentDescription = "Meno $label")
        }
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Rounded.Add, contentDescription = "Più $label")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleField(selected: Vehicle, onSelect: (Vehicle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Veicolo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Vehicle.entries.forEach { v ->
                DropdownMenuItem(text = { Text(v.label) }, onClick = { onSelect(v); expanded = false })
            }
        }
    }
}
