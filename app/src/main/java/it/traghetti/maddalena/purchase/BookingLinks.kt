package it.traghetti.maddalena.purchase

import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Port
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime

/** Veicoli accettati dal motore Delcomar (valore = testo esatto dell'opzione del sito). */
enum class Vehicle(val label: String, val delcomarValue: String) {
    NONE("Nessun veicolo", ""),
    CAR_350("Auto fino a 3,50 m", "Auto fino a mt. 3,50"),
    CAR_400("Auto fino a 4 m", "Auto fino a mt. 4"),
    CAR_450("Auto fino a 4,5 m", "Auto fino a mt. 4,5"),
    CAR_OVER_450("Auto oltre 4,5 m", "Auto oltre mt. 4,5"),
    MOTO_250("Moto fino a 250 cc", "Moto fino a 250 cc"),
    MOTO_OVER_250("Moto oltre 250 cc", "Moto oltre a 250 cc"),
    BICYCLE("Bicicletta", "Bicicletta"),
    CAMPER("Camper", "camper"),
}

data class BookingRequest(
    val company: Company,
    val from: Port,
    val date: LocalDate,
    /** Orario della corsa scelta; null = nessuna preselezione. */
    val time: LocalTime?,
    val adults: Int = 1,
    val children: Int = 0,
    val vehicle: Vehicle = Vehicle.NONE,
    val resident: Boolean = false,
)

/**
 * Costruisce i "deep link" verso i motori di prenotazione ufficiali.
 *
 * Nessuna delle due compagnie espone API pubbliche d'acquisto, ma entrambi i siti accettano
 * parametri in query string:
 *  - Delcomar: il form di ricerca è una GET verso /booking, quindi tratta, data, passeggeri,
 *    veicolo e tariffa si possono precompilare tutti.
 *  - Caronte & Tourist (Maddalena Lines): /it/isole/start accetta route/from/to e apre la
 *    procedura guidata con la tratta già scelta.
 */
object BookingLinks {
    const val DELCOMAR_HOST = "booking.delcomar.it"
    const val CARONTE_HOST = "booking.carontetourist.it"

    fun forRequest(r: BookingRequest): String = when (r.company) {
        Company.DELCOMAR -> delcomar(r)
        Company.MADDALENA_LINES -> caronte(r.from)
    }

    fun delcomar(r: BookingRequest): String {
        val hasVehicle = r.vehicle != Vehicle.NONE
        val params = listOf(
            "src" to "delcomar",
            "tratta" to "${r.from.delcomarName}|${r.from.opposite.delcomarName}",
            "depDate" to r.date.toString(),
            "adt" to r.adults.coerceIn(0, 20).toString(),
            "chd" to r.children.coerceIn(0, 20).toString(),
            "eyfs" to "0",
            "inf" to "0",
            "vei" to if (hasVehicle) "1" else "0",
            "vehicle" to r.vehicle.delcomarValue,
            "animaleqta" to "0",
            "animale" to "",
            "faresType" to if (r.resident) "CodPrezzoResLocali" else "CodPrezzoStandard",
            "depTime" to "",
            "retTime" to "",
            "source" to "booking-search",
            "action" to "search",
            "vehiclelength" to "1",
        )
        return "https://$DELCOMAR_HOST/booking?" + params.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    fun caronte(from: Port): String =
        "https://$CARONTE_HOST/it/isole/start?route=SARD&from=${from.caronteCode}&to=${from.opposite.caronteCode}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
