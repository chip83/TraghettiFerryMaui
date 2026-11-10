package it.traghetti.maddalena.domain

import java.time.Duration
import java.time.LocalDateTime

/** Finestra di partenze attorno a un istante: le ultime già partite e le prossime. */
data class ScheduleWindow(
    val past: List<Departure>,
    val upcoming: List<Departure>,
) {
    val next: Departure? get() = upcoming.firstOrNull()
    val isEmpty: Boolean get() = past.isEmpty() && upcoming.isEmpty()
}

object ScheduleWindows {
    const val DEFAULT_PAST = 3
    const val DEFAULT_UPCOMING = 6

    /**
     * Costruisce la finestra attorno a [at].
     *
     * A differenza della vecchia versione (che confrontava stringhe "HH:mm" dentro un solo giorno
     * e quindi a tarda sera non mostrava più nulla), qui si lavora su date-ora complete: le corse
     * dopo mezzanotte compaiono correttamente come "prossime".
     */
    fun build(
        departures: List<Departure>,
        at: LocalDateTime,
        companies: Set<Company> = Company.entries.toSet(),
        pastCount: Int = DEFAULT_PAST,
        upcomingCount: Int = DEFAULT_UPCOMING,
    ): ScheduleWindow {
        val sorted = departures.asSequence()
            .filter { it.company in companies }
            .sortedWith(compareBy<Departure> { it.departure }.thenBy { it.company.ordinal })
            .toList()
        val firstUpcoming = sorted.indexOfFirst { !it.departure.isBefore(at) }
            .let { if (it < 0) sorted.size else it }
        return ScheduleWindow(
            past = sorted.subList((firstUpcoming - pastCount).coerceAtLeast(0), firstUpcoming),
            upcoming = sorted.subList(firstUpcoming, (firstUpcoming + upcomingCount).coerceAtMost(sorted.size)),
        )
    }

    /** Minuti mancanti alla partenza (negativi se già partita). */
    fun minutesUntil(departure: Departure, now: LocalDateTime): Long =
        Duration.between(now, departure.departure).toMinutes()

    /** Testo compatto tipo "tra 12 min", "tra 1 h 05 min", "in partenza". */
    fun countdownLabel(minutes: Long): String = when {
        minutes < 0 -> "partito"
        minutes == 0L -> "in partenza"
        minutes < 60 -> "tra $minutes min"
        minutes < 24 * 60 -> "tra ${minutes / 60} h ${"%02d".format(minutes % 60)} min"
        else -> "tra ${minutes / (24 * 60)} g"
    }
}
