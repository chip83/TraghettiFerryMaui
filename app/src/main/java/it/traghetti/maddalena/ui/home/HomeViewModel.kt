package it.traghetti.maddalena.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.traghetti.maddalena.FerryApp
import it.traghetti.maddalena.data.ScheduleRepository
import it.traghetti.maddalena.data.StaticTimetable
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.DataSource
import it.traghetti.maddalena.domain.Departure
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.ScheduleWindow
import it.traghetti.maddalena.domain.ScheduleWindows
import it.traghetti.maddalena.location.PortLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class HomeUiState(
    val from: Port = Port.LA_MADDALENA,
    val autoLocation: Boolean = true,
    val locating: Boolean = false,
    val locationMessage: String? = null,
    /** Orario dell'orologio (fuso di Roma). */
    val now: LocalDateTime,
    /** Data/ora scelta a mano; null = si segue l'orologio. */
    val selected: LocalDateTime? = null,
    val companies: Set<Company> = Company.entries.toSet(),
    val showAll: Boolean = false,
    val loading: Boolean = true,
    val source: DataSource = DataSource.OFFLINE,
    val fetchedAt: Long? = null,
    val window: ScheduleWindow = ScheduleWindow(emptyList(), emptyList()),
    val maddalenaLinesClosed: Boolean = false,
) {
    val reference: LocalDateTime get() = selected ?: now
    val isNow: Boolean get() = selected == null
}

class HomeViewModel(
    private val repository: ScheduleRepository,
    private val locator: PortLocator,
    initialPort: Port = Port.LA_MADDALENA,
    initialAuto: Boolean = true,
    private val clock: () -> LocalDateTime = { LocalDateTime.now(ROME).truncatedTo(ChronoUnit.MINUTES) },
    tick: Boolean = true,
    private val onPortChanged: (Port) -> Unit = {},
    private val onAutoChanged: (Boolean) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(from = initialPort, autoLocation = initialAuto, now = clock()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var departures: List<Departure> = emptyList()
    private var loadedKey: Pair<LocalDate, Port>? = null
    private var loadJob: Job? = null
    private var locateJob: Job? = null

    init {
        reload()
        if (tick) viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                onTick()
            }
        }
    }

    /** Aggiorna l'orologio; se è cambiato il giorno ricarica gli orari. */
    fun onTick() {
        val now = clock()
        if (now == _state.value.now) return
        _state.update { it.copy(now = now) }
        if (needsLoad()) reload() else rebuild()
    }

    fun setPort(port: Port, manual: Boolean = true) {
        if (manual && _state.value.autoLocation) {
            _state.update { it.copy(autoLocation = false, locationMessage = null) }
            onAutoChanged(false)
        }
        if (port == _state.value.from) return
        _state.update { it.copy(from = port) }
        onPortChanged(port)
        reload()
    }

    fun swapPorts() = setPort(_state.value.from.opposite)

    fun setAutoLocation(enabled: Boolean) {
        _state.update { it.copy(autoLocation = enabled, locationMessage = null) }
        onAutoChanged(enabled)
        if (!enabled) {
            locateJob?.cancel()
            _state.update { it.copy(locating = false) }
        }
    }

    /** Rileva il porto più vicino (solo in modalità automatica). */
    fun locate() {
        if (!_state.value.autoLocation) return
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            _state.update { it.copy(locating = true, locationMessage = null) }
            val port = runCatching { locator.nearestPort() }.getOrNull()
            _state.update {
                it.copy(
                    locating = false,
                    locationMessage = if (port == null) "Posizione non disponibile: scegli il porto a mano" else "Sei più vicino a ${port.displayName}",
                )
            }
            if (port != null) setPort(port, manual = false)
        }
    }

    fun onLocationDenied() {
        _state.update { it.copy(autoLocation = false, locating = false, locationMessage = "Permesso posizione negato: scegli il porto a mano") }
        onAutoChanged(false)
    }

    fun setDate(date: LocalDate) {
        val time = _state.value.reference.toLocalTime()
        select(date.atTime(time))
    }

    fun setTime(time: LocalTime) = select(_state.value.reference.toLocalDate().atTime(time))

    private fun select(dateTime: LocalDateTime) {
        _state.update { it.copy(selected = dateTime, showAll = false) }
        if (needsLoad()) reload() else rebuild()
    }

    fun resetToNow() {
        _state.update { it.copy(selected = null, now = clock(), showAll = false) }
        if (needsLoad()) reload() else rebuild()
    }

    fun toggleCompany(company: Company) {
        _state.update {
            val next = if (company in it.companies) it.companies - company else it.companies + company
            it.copy(companies = next.ifEmpty { Company.entries.toSet() })
        }
        rebuild()
    }

    fun toggleShowAll() {
        _state.update { it.copy(showAll = !it.showAll) }
        rebuild()
    }

    fun refresh() = reload(force = true)

    private fun needsLoad() = loadedKey != (_state.value.reference.toLocalDate() to _state.value.from)

    private fun reload(force: Boolean = false) {
        val s = _state.value
        val key = s.reference.toLocalDate() to s.from
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val result = repository.load(key.first, key.second, force)
            departures = result.departures
            loadedKey = key
            _state.update { it.copy(loading = false, source = result.delcomarSource, fetchedAt = result.delcomarFetchedAt) }
            rebuild()
        }
    }

    private fun rebuild() {
        _state.update { s ->
            val window = ScheduleWindows.build(
                departures, s.reference, s.companies,
                upcomingCount = if (s.showAll) 80 else ScheduleWindows.DEFAULT_UPCOMING,
            )
            val day = s.reference.toLocalDate()
            s.copy(
                window = window,
                maddalenaLinesClosed = StaticTimetable.maddalenaLinesSeason(day) == StaticTimetable.Season.CLOSED,
            )
        }
    }

    companion object {
        val ROME: ZoneId = ZoneId.of("Europe/Rome")

        fun factory(app: FerryApp): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    repository = app.repository,
                    locator = app.locator,
                    initialPort = app.settings.lastPort,
                    initialAuto = app.settings.autoLocation,
                    onPortChanged = { app.settings.lastPort = it },
                    onAutoChanged = { app.settings.autoLocation = it },
                )
            }
        }
    }
}
