package it.traghetti.maddalena.data

import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.DataSource
import it.traghetti.maddalena.domain.Departure
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.Trip
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.time.LocalDate

/** Partenze di più giorni con l'indicazione della provenienza del dato Delcomar. */
data class ScheduleResult(
    val departures: List<Departure>,
    val delcomarSource: DataSource,
    /** Istante (epoch ms) a cui risale il dato Delcomar mostrato, se proviene dal sito. */
    val delcomarFetchedAt: Long? = null,
)

class ScheduleRepository(
    private val remote: DelcomarSource,
    private val cache: TripCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = 6 * 60 * 60 * 1000L,
) {
    /**
     * Partenze da [from] per il giorno precedente, [date] e il successivo
     * (servono per mostrare "ultime partite" e "prossime" anche a cavallo della mezzanotte).
     *
     * Delcomar: cache fresca → rete → cache scaduta → orario incorporato.
     * La rete viene interrogata solo per [date] e [date]+1 (in parallelo).
     */
    suspend fun load(date: LocalDate, from: Port, forceRefresh: Boolean = false): ScheduleResult = coroutineScope {
        val liveDays = listOf(date, date.plusDays(1)).map { d -> async { d to delcomarDay(d, from, forceRefresh, allowNetwork = true) } }
        val previous = date.minusDays(1) to delcomarDay(date.minusDays(1), from, false, allowNetwork = false)
        val days = listOf(previous) + liveDays.map { it.await() }

        val departures = days.flatMap { (d, day) ->
            day.trips.map { it.on(d, from, live = day.source != DataSource.OFFLINE) } +
                StaticTimetable.maddalenaLines(from, d).map { it.on(d, from, live = false) }
        }.sortedBy { it.departure }

        val main = days[1].second
        ScheduleResult(departures, main.source, main.fetchedAt)
    }

    private data class Day(val trips: List<Trip>, val source: DataSource, val fetchedAt: Long?)

    private suspend fun delcomarDay(date: LocalDate, from: Port, forceRefresh: Boolean, allowNetwork: Boolean): Day {
        val cached = cache.get(date, from)
        val fresh = cached != null && clock() - cached.fetchedAtMillis < maxAgeMillis
        if (cached != null && (fresh && !forceRefresh || !allowNetwork)) {
            return Day(cached.trips, if (fresh) DataSource.LIVE else DataSource.CACHE, cached.fetchedAtMillis)
        }
        if (allowNetwork) {
            try {
                val trips = remote.fetch(date, from)
                val now = clock()
                cache.put(date, from, trips, now)
                return Day(trips, DataSource.LIVE, now)
            } catch (_: IOException) {
                // si ripiega su cache scaduta o orario incorporato
            }
        }
        return if (cached != null) Day(cached.trips, DataSource.CACHE, cached.fetchedAtMillis)
        else Day(StaticTimetable.delcomar(from), DataSource.OFFLINE, null)
    }

    private fun Trip.on(date: LocalDate, from: Port, live: Boolean): Departure {
        val dep = date.atTime(departure)
        return Departure(company, from, dep, dep.plusMinutes(durationMinutes.toLong()), ship, live && company == Company.DELCOMAR)
    }
}
