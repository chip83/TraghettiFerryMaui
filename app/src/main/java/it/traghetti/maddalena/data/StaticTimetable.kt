package it.traghetti.maddalena.data

import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.Trip
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/**
 * Orari incorporati nell'app (funzionano senza rete).
 *
 * Fonti, rilevate il 10/11/2026:
 *  - Delcomar: https://delcomar.it/la-maddalena-palau/ (orario in vigore 1/11 – 31/3) e motore
 *    booking.delcomar.it (traversata 15 minuti).
 *  - Maddalena Lines (Caronte & Tourist): https://www.carontetourist.it/it/sardegna/orari
 *    (servizio attivo dal 1° aprile al 31 ottobre, tre fasce stagionali).
 *
 * Per Delcomar l'app prova sempre ad aggiornare l'orario del giorno dal sito; questi dati sono
 * il ripiego offline.
 */
object StaticTimetable {
    const val DELCOMAR_MINUTES = 15
    const val MADDALENA_LINES_MINUTES = 20

    private fun times(s: String): List<LocalTime> = s.trim().split(Regex("\\s+")).map(LocalTime::parse)

    private val delcomarFromLaMaddalena = times(
        """00:30 01:30 03:00 04:00 05:00 06:00 07:00 07:30 08:00 08:30 09:00 09:30 10:00 10:30
           11:00 11:30 12:00 12:30 13:00 13:30 14:15 15:15 16:00 16:30 17:00 17:30 18:00 18:30
           19:00 19:30 20:30 21:30 22:30 23:30"""
    )
    private val delcomarFromPalau = times(
        """00:00 01:00 02:00 03:30 04:30 05:30 06:30 07:30 08:00 08:30 09:00 09:30 10:00 10:30
           11:00 11:30 12:00 12:30 13:00 13:30 14:00 14:45 15:45 16:30 17:00 17:30 18:00 18:30
           19:00 19:30 20:00 21:00 22:00 23:00"""
    )

    /** Fasce stagionali Maddalena Lines. */
    enum class Season { LOW, MID, AUGUST, CLOSED }

    fun maddalenaLinesSeason(date: LocalDate): Season {
        val d = MonthDay.from(date)
        fun between(a: MonthDay, b: MonthDay) = !d.isBefore(a) && !d.isAfter(b)
        return when {
            between(MonthDay.of(8, 1), MonthDay.of(8, 31)) -> Season.AUGUST
            between(MonthDay.of(6, 1), MonthDay.of(7, 31)) ||
                between(MonthDay.of(9, 1), MonthDay.of(10, 10)) -> Season.MID
            between(MonthDay.of(4, 1), MonthDay.of(5, 31)) ||
                between(MonthDay.of(10, 11), MonthDay.of(10, 31)) -> Season.LOW
            else -> Season.CLOSED
        }
    }

    private val mlFromPalau = mapOf(
        Season.LOW to times("08:50 09:50 10:50 11:50 12:50 13:45 15:30 16:45 17:45 18:45"),
        Season.MID to times(
            """08:15 08:45 09:15 09:45 10:15 10:45 11:15 11:45 12:15 12:45 13:15 14:15 15:15
               16:15 16:45 17:15 17:45 18:15 19:15"""
        ),
        Season.AUGUST to times(
            """08:15 08:45 09:15 09:45 10:15 10:45 11:15 11:45 12:15 12:45 13:15 14:15 15:15
               16:15 16:45 17:15 17:45 18:15 18:45 19:15 19:45 20:15"""
        ),
    )
    private val mlFromLaMaddalena = mapOf(
        Season.LOW to times("08:20 09:20 10:20 11:20 12:20 13:15 15:00 16:15 17:15 18:15"),
        Season.MID to times(
            """08:15 08:45 09:15 09:45 10:15 10:45 11:15 11:45 12:15 12:45 13:45 14:45 15:45
               16:15 16:45 17:15 17:45 18:15 18:45"""
        ),
        Season.AUGUST to times(
            """07:45 08:15 08:45 09:15 09:45 10:15 10:45 11:15 11:45 12:15 12:45 13:45 14:45
               15:45 16:15 16:45 17:15 17:45 18:15 18:45 19:15 19:45"""
        ),
    )

    fun delcomar(from: Port): List<Trip> =
        (if (from == Port.LA_MADDALENA) delcomarFromLaMaddalena else delcomarFromPalau)
            .map { Trip(Company.DELCOMAR, it, DELCOMAR_MINUTES) }

    fun maddalenaLines(from: Port, date: LocalDate): List<Trip> {
        val table = if (from == Port.LA_MADDALENA) mlFromLaMaddalena else mlFromPalau
        return table[maddalenaLinesSeason(date)].orEmpty()
            .map { Trip(Company.MADDALENA_LINES, it, MADDALENA_LINES_MINUTES) }
    }
}
