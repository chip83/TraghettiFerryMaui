package it.traghetti.maddalena.data

import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.Trip
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

/** Voce di cache: corse Delcomar di un giorno/direzione e istante (epoch ms) del download. */
data class CachedDay(val trips: List<Trip>, val fetchedAtMillis: Long)

/**
 * Cache a due livelli (memoria + file di testo) degli orari scaricati.
 * Formato file: prima riga = timestamp, poi una riga per corsa `HH:mm|minuti|nave`.
 */
class TripCache(private val dir: File?) {
    private val memory = ConcurrentHashMap<String, CachedDay>()

    private fun key(date: LocalDate, from: Port) = "delcomar_${from.name}_$date"

    fun get(date: LocalDate, from: Port): CachedDay? {
        val k = key(date, from)
        memory[k]?.let { return it }
        val file = dir?.resolve("$k.txt")?.takeIf { it.isFile } ?: return null
        return runCatching { decode(file.readText()) }.getOrNull()?.also { memory[k] = it }
    }

    fun put(date: LocalDate, from: Port, trips: List<Trip>, nowMillis: Long) {
        val entry = CachedDay(trips, nowMillis)
        val k = key(date, from)
        memory[k] = entry
        runCatching {
            dir?.mkdirs()
            dir?.resolve("$k.txt")?.writeText(encode(entry))
        }
    }

    /** Elimina i file dei giorni passati per non far crescere la cache. */
    fun prune(today: LocalDate) {
        dir?.listFiles()?.forEach { f ->
            val date = runCatching { LocalDate.parse(f.nameWithoutExtension.substringAfterLast('_')) }.getOrNull()
            if (date == null || date.isBefore(today.minusDays(1))) f.delete()
        }
        memory.keys.removeAll { k ->
            runCatching { LocalDate.parse(k.substringAfterLast('_')) }.getOrNull()
                ?.isBefore(today.minusDays(1)) ?: true
        }
    }

    companion object {
        fun encode(day: CachedDay): String = buildString {
            append(day.fetchedAtMillis).append('\n')
            day.trips.forEach { append(it.departure).append('|').append(it.durationMinutes).append('|').append(it.ship.orEmpty()).append('\n') }
        }

        fun decode(text: String): CachedDay {
            val lines = text.lines().filter { it.isNotBlank() }
            val trips = lines.drop(1).map { line ->
                val p = line.split('|')
                Trip(Company.DELCOMAR, LocalTime.parse(p[0]), p[1].toInt(), p.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
            return CachedDay(trips, lines.first().toLong())
        }
    }
}
