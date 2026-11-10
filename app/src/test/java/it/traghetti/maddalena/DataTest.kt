package it.traghetti.maddalena

import it.traghetti.maddalena.data.CachedDay
import it.traghetti.maddalena.data.DelcomarParser
import it.traghetti.maddalena.data.DelcomarSource
import it.traghetti.maddalena.data.ScheduleRepository
import it.traghetti.maddalena.data.StaticTimetable
import it.traghetti.maddalena.data.TripCache
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.DataSource
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.Trip
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

class DataTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = LocalDate.of(2026, 11, 14)

    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    // ---- parser, su pagine reali salvate da booking.delcomar.it il 10/11/2026 ----

    @Test fun `parses real results page Palau to La Maddalena`() {
        val trips = DelcomarParser.parse(fixture("delcomar_palau_lamaddalena.html"))
        assertEquals(34, trips.size)
        assertEquals(LocalTime.of(0, 0), trips.first().departure)
        assertEquals(LocalTime.of(23, 0), trips.last().departure)
        assertTrue(trips.all { it.durationMinutes == 15 && it.company == Company.DELCOMAR })
        assertEquals("Enzo D", trips.first().ship)
        assertEquals("I. Maddalena", trips.first { it.departure == LocalTime.of(6, 30) }.ship)
        assertEquals("S. Stefano", trips.first { it.departure == LocalTime.of(8, 0) }.ship)
        // il sito conferma l'orario incorporato nell'app
        assertEquals(StaticTimetable.delcomar(Port.PALAU).map { it.departure }, trips.map { it.departure })
    }

    @Test fun `parses real results page La Maddalena to Palau`() {
        val trips = DelcomarParser.parse(fixture("delcomar_lamaddalena_palau.html"))
        assertEquals(33, trips.size)
        assertEquals(LocalTime.of(0, 30), trips.first().departure)
        assertTrue(LocalTime.of(14, 15) in trips.map { it.departure })
    }

    @Test fun `parser tolerates garbage and attribute order`() {
        assertTrue(DelcomarParser.parse("<html>manutenzione</html>").isEmpty())
        val html = """<div class="card" data-hdep="23:50" data-x="1" data-ref="select-andata"><span class="time">23:50</span><span class="time alt">00:10</span></div>
            <div class="card" data-ref="select-ritorno" data-hdep="09:00"></div>
            <div class="card" data-ref="select-andata" data-hdep="99:99"></div>"""
        val trips = DelcomarParser.parse(html)
        assertEquals(1, trips.size)                       // ritorno e orario non valido scartati
        assertEquals(20, trips.single().durationMinutes)  // arrivo dopo mezzanotte
        assertNull(trips.single().ship)
    }

    // ---- cache ----

    @Test fun `cache round trip on disk`() {
        val trips = listOf(Trip(Company.DELCOMAR, LocalTime.of(6, 30), 15, "I. Maddalena"), Trip(Company.DELCOMAR, LocalTime.of(7, 30), 15, null))
        TripCache(tmp.root).put(day, Port.PALAU, trips, 1234L)
        val reread = TripCache(tmp.root).get(day, Port.PALAU)          // nuova istanza: legge dal file
        assertEquals(CachedDay(trips, 1234L), reread)
        assertNull(TripCache(tmp.root).get(day, Port.LA_MADDALENA))
        tmp.root.resolve("delcomar_PALAU_$day.txt").writeText("corrotto")
        assertNull(TripCache(tmp.root).get(day, Port.PALAU))            // file rovinato → ignorato, niente crash
    }

    @Test fun `prune removes old days`() {
        val cache = TripCache(tmp.root)
        cache.put(day.minusDays(5), Port.PALAU, emptyList(), 1); cache.put(day, Port.PALAU, emptyList(), 1)
        cache.prune(day)
        assertNull(TripCache(tmp.root).get(day.minusDays(5), Port.PALAU))
        assertTrue(TripCache(tmp.root).get(day, Port.PALAU) != null)
    }

    // ---- repository ----

    private class FakeSource(var fail: Boolean = false) : DelcomarSource {
        val calls = mutableListOf<LocalDate>()
        override suspend fun fetch(date: LocalDate, from: Port): List<Trip> {
            synchronized(calls) { calls += date }
            if (fail) throw IOException("offline")
            return listOf(Trip(Company.DELCOMAR, LocalTime.of(9, 5), 15, "Test"))
        }
    }

    @Test fun `live data replaces static timetable and is cached`() = runTest {
        val source = FakeSource(); var now = 1_000L
        val repo = ScheduleRepository(source, TripCache(tmp.root), clock = { now })
        val r = repo.load(day, Port.PALAU)
        assertEquals(DataSource.LIVE, r.delcomarSource)
        assertEquals(setOf(day, day.plusDays(1)), source.calls.toSet())     // il giorno prima non usa la rete
        // giorno precedente (statico) + 2 giorni live da 1 corsa
        assertEquals(34 + 2, r.departures.size)
        assertTrue(r.departures.first { it.departure == day.atTime(9, 5) }.live)
        assertFalse(r.departures.first().live)
        assertEquals(r.departures.sortedBy { it.departure }, r.departures)

        source.calls.clear(); now += 60_000
        repo.load(day, Port.PALAU)
        assertTrue("cache fresca: nessuna richiesta", source.calls.isEmpty())
        repo.load(day, Port.PALAU, forceRefresh = true)
        assertEquals(2, source.calls.size)
    }

    @Test fun `offline falls back to stale cache then to bundled timetable`() = runTest {
        val source = FakeSource(); var now = 0L
        val cache = TripCache(tmp.root)
        val repo = ScheduleRepository(source, cache, clock = { now }, maxAgeMillis = 1000)
        repo.load(day, Port.PALAU)
        source.fail = true; now = 10_000
        val stale = repo.load(day, Port.PALAU)
        assertEquals(DataSource.CACHE, stale.delcomarSource)
        assertEquals(0L, stale.delcomarFetchedAt)

        val other = repo.load(day, Port.LA_MADDALENA)                      // mai scaricata
        assertEquals(DataSource.OFFLINE, other.delcomarSource)
        assertEquals(34 * 3, other.departures.size)
        assertTrue(other.departures.none { it.live })
    }

    @Test fun `summer day merges both companies`() = runTest {
        val aug = LocalDate.of(2027, 8, 10)
        val repo = ScheduleRepository(FakeSource(fail = true), TripCache(null))
        val r = repo.load(aug, Port.PALAU)
        val onDay = r.departures.filter { it.departure.toLocalDate() == aug }
        assertEquals(34, onDay.count { it.company == Company.DELCOMAR })
        assertEquals(22, onDay.count { it.company == Company.MADDALENA_LINES })
        assertTrue(onDay.all { it.arrival.isAfter(it.departure) && it.to == Port.LA_MADDALENA })
    }
}
