package it.traghetti.maddalena.domain

import java.time.LocalDateTime
import java.time.LocalTime

/** I due porti della tratta. */
enum class Port(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    /** Nome usato dal motore di prenotazione Delcomar (parametro `tratta`). */
    val delcomarName: String,
    /** Codice porto usato dal motore di prenotazione Caronte & Tourist. */
    val caronteCode: String,
) {
    LA_MADDALENA("La Maddalena", 41.2137, 9.4046, "LA MADDALENA", "MADD"),
    PALAU("Palau", 41.1787, 9.3845, "PALAU", "PALA");

    val opposite: Port get() = if (this == LA_MADDALENA) PALAU else LA_MADDALENA
}

enum class Company(val displayName: String, val operatedBy: String) {
    DELCOMAR("Delcomar", "Delcomar"),
    MADDALENA_LINES("Maddalena Lines", "Caronte & Tourist"),
}

/** Una corsa "astratta" di un orario giornaliero (senza data). */
data class Trip(
    val company: Company,
    val departure: LocalTime,
    val durationMinutes: Int,
    val ship: String? = null,
)

/** Una partenza concreta in una certa data. */
data class Departure(
    val company: Company,
    val from: Port,
    val departure: LocalDateTime,
    val arrival: LocalDateTime,
    val ship: String? = null,
    /** true se l'orario arriva dal sito della compagnia (in tempo reale o da cache). */
    val live: Boolean = false,
) {
    val to: Port get() = from.opposite
}

/** Da dove arrivano gli orari mostrati. */
enum class DataSource { LIVE, CACHE, OFFLINE }
