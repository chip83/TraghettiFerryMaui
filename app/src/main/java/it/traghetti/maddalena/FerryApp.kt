package it.traghetti.maddalena

import android.app.Application
import it.traghetti.maddalena.card.CardVault
import it.traghetti.maddalena.data.DelcomarRemote
import it.traghetti.maddalena.data.ScheduleRepository
import it.traghetti.maddalena.data.TripCache
import it.traghetti.maddalena.location.AndroidPortLocator

/** Contenitore minimale delle dipendenze (niente framework di DI: l'app è piccola). */
class FerryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }

    val repository by lazy { ScheduleRepository(DelcomarRemote(), TripCache(cacheDir.resolve("orari"))) }
    val locator by lazy { AndroidPortLocator(this) }
    val vault by lazy { CardVault(this) }
    val settings by lazy { Settings(getSharedPreferences("settings", MODE_PRIVATE)) }
}
