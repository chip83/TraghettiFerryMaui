package it.traghetti.maddalena

import it.traghetti.maddalena.data.DelcomarRemote
import it.traghetti.maddalena.domain.Port
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Test d'integrazione contro il sito reale. Si attiva con `./gradlew testDebugUnitTest -PliveTests=true`. */
class LiveDelcomarTest {
    @Test fun `downloads today's timetable from booking_delcomar_it in both directions`() = runBlocking {
        assumeTrue(System.getProperty("liveTests") == "true")
        val date = LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(1)
        Port.entries.forEach { port ->
            val trips = DelcomarRemote(attempts = 5).fetch(date, port)
            println("LIVE ${port.displayName} $date: ${trips.size} corse, prima ${trips.first().departure}, ultima ${trips.last().departure}, navi ${trips.mapNotNull { it.ship }.distinct()}")
            assertTrue(trips.size >= 15)
            assertTrue(trips.all { it.durationMinutes in 10..30 })
        }
    }
}
