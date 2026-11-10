package it.traghetti.maddalena.data

import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.Trip
import it.traghetti.maddalena.purchase.BookingLinks
import it.traghetti.maddalena.purchase.BookingRequest
import it.traghetti.maddalena.domain.Company
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Sorgente remota degli orari Delcomar di un giorno. */
fun interface DelcomarSource {
    /** @throws IOException se il sito non risponde o non pubblica corse per quella data. */
    suspend fun fetch(date: LocalDate, from: Port): List<Trip>
}

/**
 * Legge l'orario del giorno dalla pagina risultati del motore di prenotazione Delcomar.
 * È una semplice GET pubblica (la stessa che fa il form del sito): non serve login e non crea
 * alcuna prenotazione. Il sito ogni tanto chiude la connessione: si riprova fino a [attempts] volte.
 */
class DelcomarRemote(
    private val attempts: Int = 3,
    private val timeoutMs: Int = 12_000,
) : DelcomarSource {

    override suspend fun fetch(date: LocalDate, from: Port): List<Trip> = withContext(Dispatchers.IO) {
        val url = BookingLinks.delcomar(BookingRequest(Company.DELCOMAR, from, date, time = null))
        var last: IOException? = null
        repeat(attempts) { attempt ->
            try {
                val trips = DelcomarParser.parse(download(url))
                if (trips.isEmpty()) throw IOException("Nessuna corsa pubblicata per $date")
                return@withContext trips
            } catch (e: IOException) {
                last = e
                if (attempt < attempts - 1) delay(600L * (attempt + 1))
            }
        }
        throw last ?: IOException("Errore sconosciuto")
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html")
            conn.setRequestProperty("Accept-Language", "it-IT,it;q=0.9")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
