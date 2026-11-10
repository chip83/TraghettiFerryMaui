package it.traghetti.maddalena.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traghetti.maddalena.Settings
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.DataSource
import it.traghetti.maddalena.domain.Departure
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.ScheduleWindows
import it.traghetti.maddalena.location.AndroidPortLocator
import it.traghetti.maddalena.purchase.BookingRequest
import it.traghetti.maddalena.ui.theme.Sea
import it.traghetti.maddalena.ui.theme.TimeStyle
import it.traghetti.maddalena.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    locator: AndroidPortLocator,
    settings: Settings,
    onOpenCard: () -> Unit,
    onPurchase: (BookingRequest) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetFor by remember { mutableStateOf<Departure?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) viewModel.locate() else viewModel.onLocationDenied()
    }
    fun locateOrAsk() {
        if (locator.hasPermission()) viewModel.locate()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) { if (state.autoLocation) locateOrAsk() }
    // Al ritorno in primo piano si riallinea subito l'orologio.
    LifecycleResumeEffect(Unit) {
        viewModel.onTick()
        onPauseOrDispose { }
    }

    HomeContent(
        state = state,
        onSwap = viewModel::swapPorts,
        onSelectPort = { viewModel.setPort(it) },
        onToggleAuto = { enabled ->
            viewModel.setAutoLocation(enabled)
            if (enabled) locateOrAsk()
        },
        onDate = viewModel::setDate,
        onTime = viewModel::setTime,
        onNow = viewModel::resetToNow,
        onToggleCompany = viewModel::toggleCompany,
        onToggleShowAll = viewModel::toggleShowAll,
        onRefresh = viewModel::refresh,
        onOpenCard = onOpenCard,
        onBuy = { sheetFor = it },
    )

    sheetFor?.let { dep ->
        PurchaseSheet(
            departure = dep,
            defaultResident = settings.resident,
            onDismiss = { sheetFor = null },
            onConfirm = { request ->
                settings.resident = request.resident
                sheetFor = null
                onPurchase(request)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onSwap: () -> Unit,
    onSelectPort: (Port) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onDate: (LocalDate) -> Unit,
    onTime: (java.time.LocalTime) -> Unit,
    onNow: () -> Unit,
    onToggleCompany: (Company) -> Unit,
    onToggleShowAll: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCard: () -> Unit,
    onBuy: (Departure) -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.loading && !state.window.isEmpty, onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("home_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "header") {
                    Header(state, onSwap, onSelectPort, onToggleAuto, onOpenCard,
                        onPickDate = { showDate = true }, onPickTime = { showTime = true }, onNow = onNow)
                }
                item(key = "hero") {
                    Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                        NextDepartureCard(state, onBuy)
                    }
                }
                item(key = "filters") {
                    Row(
                        Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Company.entries.forEach { c ->
                            FilterChip(
                                selected = c in state.companies,
                                onClick = { onToggleCompany(c) },
                                label = { Text(c.displayName) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(c.color)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = c.color.copy(alpha = 0.16f),
                                ),
                            )
                        }
                    }
                }
                if (state.window.past.isNotEmpty()) {
                    item(key = "past_title") { SectionTitle("Appena partiti") }
                    items(state.window.past, key = { "p" + it.company + it.departure }) { dep ->
                        DepartureRow(dep, state.reference, past = true, onBuy = null)
                    }
                }
                item(key = "next_title") { SectionTitle(if (state.isNow) "Prossime partenze" else "Partenze dalle ${state.reference.format(HHMM)}") }
                if (state.window.upcoming.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (state.loading) "Carico gli orari…" else "Nessuna partenza trovata per i filtri scelti.",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.window.upcoming, key = { "u" + it.company + it.departure }) { dep ->
                    DepartureRow(dep, state.reference, past = false, onBuy = { onBuy(dep) })
                }
                item(key = "more") {
                    TextButton(onClick = onToggleShowAll, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(if (state.showAll) "Mostra meno" else "Mostra tutte le partenze")
                    }
                }
                item(key = "footer") { Footer(state) }
            }
        }
    }

    if (showDate) DatePickerSheet(state.reference.toLocalDate(), state.now.toLocalDate(), onDismiss = { showDate = false }) {
        showDate = false; onDate(it)
    }
    if (showTime) TimePickerSheet(state.reference.toLocalTime(), onDismiss = { showTime = false }) {
        showTime = false; onTime(it)
    }
}

@Composable
private fun Header(
    state: HomeUiState,
    onSwap: () -> Unit,
    onSelectPort: (Port) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onOpenCard: () -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onNow: () -> Unit,
) {
    val brush = if (isSystemInDarkTheme()) Sea.headerBrushDark else Sea.headerBrush
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(brush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Traghetti", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("La Maddalena ⇄ Palau", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = onOpenCard, modifier = Modifier.testTag("open_card")) {
                Icon(Icons.Rounded.CreditCard, contentDescription = "La mia carta", tint = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Tratta: partenza → arrivo con pulsante di inversione
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.14f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                PortLabel("Partenza da", state.from.displayName, Alignment.Start, Modifier.weight(1f), nameTag = "from_port")
                FilledIconButton(
                    onClick = onSwap,
                    modifier = Modifier.size(48.dp).testTag("swap"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Sea.Ocean),
                ) { Icon(Icons.Rounded.SwapHoriz, contentDescription = "Inverti la tratta") }
                PortLabel("Arrivo a", state.from.opposite.displayName, Alignment.End, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.autoLocation,
                onClick = { onToggleAuto(!state.autoLocation) },
                label = { Text(if (state.autoLocation) "GPS automatico" else "GPS spento") },
                leadingIcon = {
                    if (state.locating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
                },
                colors = headerChipColors(),
                border = null,
                modifier = Modifier.testTag("gps_chip"),
            )
            if (!state.autoLocation) {
                Port.entries.forEach { p ->
                    FilterChip(
                        selected = state.from == p,
                        onClick = { onSelectPort(p) },
                        label = { Text(p.displayName, maxLines = 1) },
                        colors = headerChipColors(),
                        border = null,
                    )
                }
            }
        }
        state.locationMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val today = state.now.toLocalDate()
            val date = state.reference.toLocalDate()
            val dateLabel = when (date) {
                today -> "Oggi, ${date.format(DAY)}"
                today.plusDays(1) -> "Domani, ${date.format(DAY)}"
                else -> date.format(DAY)
            }
            HeaderChip(dateLabel, Icons.Rounded.CalendarMonth, onPickDate, Modifier.testTag("date_chip"))
            HeaderChip(state.reference.format(HHMM), Icons.Rounded.Schedule, onPickTime, Modifier.testTag("time_chip"))
            if (!state.isNow) HeaderChip("Adesso", Icons.Rounded.Update, onNow, Modifier.testTag("now_chip"))
        }
    }
}

@Composable
private fun headerChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.14f),
    labelColor = Color.White,
    iconColor = Color.White,
    selectedContainerColor = Color.White,
    selectedLabelColor = Sea.Deep,
    selectedLeadingIconColor = Sea.Ocean,
)

@Composable
private fun HeaderChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.White.copy(alpha = 0.14f), labelColor = Color.White, leadingIconContentColor = Color.White,
        ),
        border = null,
        modifier = modifier,
    )
}

@Composable
private fun PortLabel(caption: String, name: String, align: Alignment.Horizontal, modifier: Modifier, nameTag: String = "to_port") {
    Column(modifier, horizontalAlignment = align) {
        Text(caption, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(nameTag))
    }
}

@Composable
private fun NextDepartureCard(state: HomeUiState, onBuy: (Departure) -> Unit) {
    val next = state.window.next
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().testTag("hero"),
    ) {
        if (next == null) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Carico gli orari…")
                } else Text("Nessuna partenza in programma")
            }
            return@Surface
        }
        val minutes = ScheduleWindows.minutesUntil(next, state.reference)
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.isNow) "PROSSIMA PARTENZA" else "PRIMA PARTENZA UTILE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CompanyBadge(next.company)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(next.departure.format(HHMM), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(
                        if (state.isNow) ScheduleWindows.countdownLabel(minutes) else dayHint(next.departure, state.reference),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.isNow && minutes <= 10) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("countdown"),
                    )
                    Text(
                        "arrivo a ${next.to.displayName} alle ${next.arrival.format(HHMM)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            next.ship?.let {
                Text("Nave $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onBuy(next) },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("hero_buy"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text("Acquista il biglietto")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

private fun dayHint(departure: LocalDateTime, reference: LocalDateTime): String =
    if (departure.toLocalDate() == reference.toLocalDate()) departure.toLocalDate().format(DAY)
    else "il giorno dopo, ${departure.toLocalDate().format(DAY)}"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun CompanyBadge(company: Company) {
    Surface(shape = RoundedCornerShape(50), color = company.color) {
        Text(
            company.displayName,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DepartureRow(dep: Departure, reference: LocalDateTime, past: Boolean, onBuy: (() -> Unit)?) {
    val minutes = ScheduleWindows.minutesUntil(dep, reference)
    val nextDay = dep.departure.toLocalDate() != reference.toLocalDate()
    val description = "${dep.company.displayName}, partenza ${dep.departure.format(HHMM)}, arrivo ${dep.arrival.format(HHMM)}" +
        if (past) ", già partito" else ""
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (past) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .alpha(if (past) 0.55f else 1f)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(if (past) "row_past" else "row_upcoming"),
    ) {
        Row(Modifier.height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(72.dp).background(dep.company.color))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dep.departure.format(HHMM), style = TimeStyle, color = MaterialTheme.colorScheme.onSurface)
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward, null,
                        Modifier.padding(horizontal = 8.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(dep.arrival.format(HHMM), style = TimeStyle.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val detail = buildList {
                    add(dep.company.displayName)
                    dep.ship?.let { add(it) }
                    if (nextDay && !past) add("domani")
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = dep.company.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (past) {
                Text("partito", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
            } else {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
                    if (minutes in 0 until 180) {
                        Text(ScheduleWindows.countdownLabel(minutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                    }
                    if (onBuy != null) TextButton(onClick = onBuy, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("Acquista") }
                }
            }
        }
    }
}

@Composable
private fun Footer(state: HomeUiState) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("source")) {
            val (icon, tint, text) = when (state.source) {
                DataSource.LIVE -> Triple(Icons.Rounded.CloudDone, Sea.Live, "Orari Delcomar aggiornati dal sito ufficiale${fetchedLabel(state.fetchedAt)}")
                DataSource.CACHE -> Triple(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant, "Sei offline: orari Delcomar salvati${fetchedLabel(state.fetchedAt)}")
                DataSource.OFFLINE -> Triple(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant, "Sei offline: orari Delcomar incorporati nell'app")
            }
            Icon(icon, null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.maddalenaLinesClosed) {
            Text(
                "Maddalena Lines (Caronte & Tourist) effettua il servizio dal 1° aprile al 31 ottobre.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Gli orari possono variare per meteo, scioperi o festività: verifica sempre sul sito della compagnia.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun fetchedLabel(millis: Long?): String {
    millis ?: return ""
    val t = Instant.ofEpochMilli(millis).atZone(HomeViewModel.ROME)
    val today = LocalDate.now(HomeViewModel.ROME)
    return if (t.toLocalDate() == today) " alle ${t.format(HHMM)}" else " il ${t.format(DateTimeFormatter.ofPattern("d/M"))} alle ${t.format(HHMM)}"
}
