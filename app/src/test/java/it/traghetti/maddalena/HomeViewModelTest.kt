package it.traghetti.maddalena

import it.traghetti.maddalena.data.ScheduleRepository
import it.traghetti.maddalena.data.TripCache
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.DataSource
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private var now = LocalDateTime.of(2027, 8, 10, 10, 20)
    private var gps: Port? = Port.PALAU
    private var savedPort: Port? = null

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(auto: Boolean = true) = HomeViewModel(
        repository = ScheduleRepository({ _, _ -> throw IOException("offline") }, TripCache(null)),
        locator = { gps },
        initialPort = Port.LA_MADDALENA,
        initialAuto = auto,
        clock = { now },
        tick = false,
        onPortChanged = { savedPort = it },
    )

    private fun HomeViewModel.times() = state.value.window.upcoming.map { it.departure.toLocalTime().toString() }

    @Test fun `initial state shows 3 past and 6 upcoming from both companies`() {
        val s = vm().state.value
        assertFalse(s.loading)
        assertEquals(DataSource.OFFLINE, s.source)
        assertEquals(3, s.window.past.size); assertEquals(6, s.window.upcoming.size)
        // 10:20 di agosto da La Maddalena: Delcomar 10:30, 11:00… e Maddalena Lines 10:45, 11:15…
        assertEquals(listOf("10:30", "10:45", "11:00", "11:15", "11:30", "11:45"), s.window.upcoming.map { it.departure.toLocalTime().toString() })
        assertEquals(setOf(Company.DELCOMAR, Company.MADDALENA_LINES), s.window.upcoming.map { it.company }.toSet())
        assertFalse(s.maddalenaLinesClosed)
    }

    @Test fun `gps selects nearest port, manual choice disables gps`() {
        val vm = vm()
        vm.locate()
        assertEquals(Port.PALAU, vm.state.value.from)
        assertTrue(vm.state.value.autoLocation)
        assertEquals(Port.PALAU, savedPort)
        assertEquals("10:30", vm.times().first())             // Palau: Delcomar 10:30
        vm.swapPorts()
        assertEquals(Port.LA_MADDALENA, vm.state.value.from)
        assertFalse(vm.state.value.autoLocation)
        vm.locate()                                            // ignorato in manuale
        assertEquals(Port.LA_MADDALENA, vm.state.value.from)
    }

    @Test fun `gps unavailable keeps port and explains`() {
        gps = null
        val vm = vm(); vm.locate()
        assertEquals(Port.LA_MADDALENA, vm.state.value.from)
        assertTrue(vm.state.value.locationMessage!!.contains("non disponibile"))
        vm.onLocationDenied()
        assertFalse(vm.state.value.autoLocation)
    }

    @Test fun `custom date and time then back to now`() {
        val vm = vm()
        vm.setTime(LocalTime.of(23, 40))
        assertFalse(vm.state.value.isNow)
        assertEquals(listOf("00:30", "01:30", "03:00"), vm.times().take(3))     // corse dopo mezzanotte
        assertEquals(LocalDate.of(2027, 8, 11), vm.state.value.window.next!!.departure.toLocalDate())
        vm.setDate(LocalDate.of(2026, 12, 25)); vm.setTime(LocalTime.of(9, 0))
        assertTrue(vm.state.value.maddalenaLinesClosed)                          // inverno: solo Delcomar
        assertTrue(vm.state.value.window.upcoming.all { it.company == Company.DELCOMAR })
        assertEquals("09:00", vm.times().first())
        vm.resetToNow()
        assertTrue(vm.state.value.isNow)
        assertEquals("10:30", vm.times().first())
    }

    @Test fun `clock tick moves departed ferries to the past`() {
        val vm = vm()
        assertEquals("10:30", vm.times().first())
        now = now.withHour(10).withMinute(31); vm.onTick()
        assertEquals("10:45", vm.times().first())
        assertEquals("10:30", vm.state.value.window.past.last().departure.toLocalTime().toString())
        now = LocalDateTime.of(2027, 8, 11, 0, 5); vm.onTick()                   // cambio giorno → ricarica
        assertEquals("00:30", vm.times().first())
        assertEquals(LocalDate.of(2027, 8, 11), vm.state.value.window.next!!.departure.toLocalDate())
    }

    @Test fun `filters and show all`() {
        val vm = vm()
        vm.toggleCompany(Company.DELCOMAR)
        assertTrue(vm.state.value.window.upcoming.all { it.company == Company.MADDALENA_LINES })
        vm.toggleCompany(Company.MADDALENA_LINES)                                // mai zero compagnie
        assertEquals(Company.entries.toSet(), vm.state.value.companies)
        vm.toggleShowAll()
        assertTrue(vm.state.value.window.upcoming.size > 30)
        vm.toggleShowAll()
        assertEquals(6, vm.state.value.window.upcoming.size)
        assertNull(vm.state.value.selected)
    }
}
