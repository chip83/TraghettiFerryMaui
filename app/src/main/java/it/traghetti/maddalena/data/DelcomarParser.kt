package it.traghetti.maddalena.data

import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Trip
import java.time.Duration
import java.time.LocalTime

/**
 * Estrae le corse dalla pagina dei risultati di booking.delcomar.it.
 *
 * Ogni corsa è una "card" di questo tipo (struttura verificata sul sito reale):
 * ```
 * <div class="select-orario select-andata ...">
 *   <div class="card" data-ref="select-andata" data-hdep="06:30" ...>
 *     ... <span class="d-block pt-1"> i.maddalena </span>
 *     ... <span class="time">06:30</span> ... <span class="time alt">06:45</span>
 * ```
 * Il parser è volutamente tollerante: se manca l'arrivo usa la durata standard.
 */
object DelcomarParser {
    private val cardStart = Regex("""data-ref="select-andata"[^>]*?data-hdep="(\d{1,2}:\d{2})"""")
    private val cardStartAlt = Regex("""data-hdep="(\d{1,2}:\d{2})"[^>]*?data-ref="select-andata"""")
    private val arrival = Regex("""class="time alt"\s*>\s*(\d{1,2}:\d{2})\s*<""")
    private val ship = Regex("""<span class="d-block[^"]*"\s*>\s*([^<]+?)\s*</span>""")

    fun parse(html: String): List<Trip> {
        val starts = (cardStart.findAll(html) + cardStartAlt.findAll(html))
            .sortedBy { it.range.first }
            .toList()
        return starts.mapIndexedNotNull { i, m ->
            val dep = parseTime(m.groupValues[1]) ?: return@mapIndexedNotNull null
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else minOf(html.length, m.range.last + 4000)
            val body = html.substring(m.range.last, end)
            val arr = arrival.find(body)?.groupValues?.get(1)?.let(::parseTime)
            val minutes = arr?.let { Duration.between(dep, it).toMinutes().let { d -> if (d < 0) d + 24 * 60 else d } }
                ?.takeIf { it in 5..90 }?.toInt()
                ?: StaticTimetable.DELCOMAR_MINUTES
            Trip(
                company = Company.DELCOMAR,
                departure = dep,
                durationMinutes = minutes,
                ship = ship.find(body)?.groupValues?.get(1)?.let(::prettyShip),
            )
        }.distinctBy { it.departure }.sortedBy { it.departure }
    }

    private fun parseTime(s: String): LocalTime? = runCatching {
        val (h, m) = s.split(":").map(String::toInt)
        LocalTime.of(h, m)
    }.getOrNull()

    /** "i.maddalena" → "I. Maddalena", "enzo d" → "Enzo D". */
    internal fun prettyShip(raw: String): String? {
        val clean = raw.replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty() || clean.length > 40) return null
        return clean.replace(Regex("\\.(?=\\S)"), ". ")
            .split(" ")
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
