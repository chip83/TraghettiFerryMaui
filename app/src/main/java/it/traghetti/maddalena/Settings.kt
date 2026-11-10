package it.traghetti.maddalena

import android.content.SharedPreferences
import it.traghetti.maddalena.domain.Port

/** Preferenze utente persistenti. */
class Settings(private val prefs: SharedPreferences) {
    var autoLocation: Boolean
        get() = prefs.getBoolean("auto_location", true)
        set(v) = prefs.edit().putBoolean("auto_location", v).apply()

    var lastPort: Port
        get() = runCatching { Port.valueOf(prefs.getString("port", null)!!) }.getOrDefault(Port.LA_MADDALENA)
        set(v) = prefs.edit().putString("port", v.name).apply()

    var resident: Boolean
        get() = prefs.getBoolean("resident", false)
        set(v) = prefs.edit().putBoolean("resident", v).apply()
}
