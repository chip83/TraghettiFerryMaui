package it.traghetti.maddalena

import it.traghetti.maddalena.data.StaticTimetable
import it.traghetti.maddalena.data.StaticTimetable.Season
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Departure
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.ScheduleWindows
import it.traghetti.maddalena.location.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DomainTest {
    private val day = LocalDate.of(2026, 11, 14)

    private fun deps(vararg times: String, company: Company = Company.DELCOMAR, date: LocalDate = day) = times.map {
        val d = date.atTime(LocalTime.parse(it))
        Departure(company, Port.PALAU, d, d.plusMinutes(15))
    }

    @Test fun `window shows last 3 past and next 6`() {
        val all = deps("06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00")
        val w = ScheduleWindows.build(all, day.atTime(10, 30))
        assertEquals(listOf("08:00", "09:00", "10:00"), w.past.map { it.departure.toLocalTime().toString() })
        assertEquals(listOf("11:00", "12:00", "13:00", "14:00", "15:00", "16:00"), w.upcoming.map { it.departure.toLocalTime().toString() })
        assertEquals("11:00", w.next!!.departure.toLocalTime().toString())
    }

    @Test fun `departure exactly at reference time is upcoming`() {
        val w = ScheduleWindows.build(deps("10:00", "10:30"), day.atTime(10, 30))
        assertEquals(1, w.past.size)
        assertEquals(day.atTime(10, 30), w.next!!.departure)
    }

    @Test fun `late evening shows after-midnight ferries of next day (old app showed nothing)`() {
        val all = deps("22:00", "23:00") + deps("00:00", "01:00", date = day.plusDays(1))
        val w = ScheduleWindows.build(all, day.atTime(23, 40))
        assertEquals(2, w.past.size)
        assertEquals(listOf(day.plusDays(1).atTime(0, 0), day.plusDays(1).atTime(1, 0)), w.upcoming.map { it.departure })
    }

    @Test fun `company filter and empty list`() {
        val all = deps("10:00") + deps("10:15", company = Company.MADDALENA_LINES)
        val w = ScheduleWindows.build(all, day.atTime(9, 0), setOf(Company.MADDALENA_LINES))
        assertEquals(listOf(Company.MADDALENA_LINES), w.upcoming.map { it.company })
        assertTrue(ScheduleWindows.build(emptyList(), day.atTime(9, 0)).isEmpty)
    }

    @Test fun `countdown labels`() {
        assertEquals("in partenza", ScheduleWindows.countdownLabel(0))
        assertEquals("tra 12 min", ScheduleWindows.countdownLabel(12))
        assertEquals("tra 1 h 05 min", ScheduleWindows.countdownLabel(65))
        assertEquals("partito", ScheduleWindows.countdownLabel(-3))
        assertEquals(-30L, ScheduleWindows.minutesUntil(deps("10:00").first(), LocalDateTime.of(day, LocalTime.of(10, 30))))
    }

    @Test fun `maddalena lines seasons follow the official calendar`() {
        fun s(m: Int, d: Int) = StaticTimetable.maddalenaLinesSeason(LocalDate.of(2027, m, d))
        assertEquals(Season.CLOSED, s(3, 31)); assertEquals(Season.LOW, s(4, 1)); assertEquals(Season.LOW, s(5, 31))
        assertEquals(Season.MID, s(6, 1)); assertEquals(Season.MID, s(7, 31)); assertEquals(Season.AUGUST, s(8, 1))
        assertEquals(Season.AUGUST, s(8, 31)); assertEquals(Season.MID, s(9, 1)); assertEquals(Season.MID, s(10, 10))
        assertEquals(Season.LOW, s(10, 11)); assertEquals(Season.LOW, s(10, 31)); assertEquals(Season.CLOSED, s(11, 1))
    }

    @Test fun `static timetables have the published number of departures`() {
        assertEquals(34, StaticTimetable.delcomar(Port.LA_MADDALENA).size)
        assertEquals(34, StaticTimetable.delcomar(Port.PALAU).size)
        val aug = LocalDate.of(2027, 8, 15); val jun = LocalDate.of(2027, 6, 15); val apr = LocalDate.of(2027, 4, 15)
        assertEquals(22, StaticTimetable.maddalenaLines(Port.PALAU, aug).size)
        assertEquals(22, StaticTimetable.maddalenaLines(Port.LA_MADDALENA, aug).size)
        assertEquals(19, StaticTimetable.maddalenaLines(Port.PALAU, jun).size)
        assertEquals(19, StaticTimetable.maddalenaLines(Port.LA_MADDALENA, jun).size)
        assertEquals(10, StaticTimetable.maddalenaLines(Port.PALAU, apr).size)
        assertEquals(0, StaticTimetable.maddalenaLines(Port.PALAU, LocalDate.of(2026, 12, 1)).size)
        // tutte ordinate e senza doppioni
        Port.entries.forEach { p ->
            val t = StaticTimetable.delcomar(p).map { it.departure }
            assertEquals(t.sorted().distinct(), t)
        }
    }

    @Test fun `nearest port from gps position`() {
        assertEquals(Port.LA_MADDALENA, Geo.nearestPort(41.2140, 9.4060))   // centro La Maddalena
        assertEquals(Port.PALAU, Geo.nearestPort(41.1800, 9.3830))          // porto di Palau
        assertEquals(Port.PALAU, Geo.nearestPort(40.9230, 9.4960))          // Olbia: sulla terraferma → Palau
        assertEquals(Port.LA_MADDALENA, Geo.nearestPort(41.2130, 9.4600))   // Caprera
        val km = Geo.distanceKm(Port.PALAU.latitude, Port.PALAU.longitude, Port.LA_MADDALENA.latitude, Port.LA_MADDALENA.longitude)
        assertTrue("distanza porti $km km", km in 3.0..6.0)
    }
}
