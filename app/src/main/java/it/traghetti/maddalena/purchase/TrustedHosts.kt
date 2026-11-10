package it.traghetti.maddalena.purchase

import android.net.Uri

/**
 * Domini sui quali l'app accetta di inserire automaticamente i dati della carta.
 * Su qualunque altro dominio (es. pagina 3-D Secure della banca) la compilazione automatica è
 * disattivata: resta solo il copia-incolla manuale deciso dall'utente.
 */
object TrustedHosts {
    private val exact = setOf(
        BookingLinks.DELCOMAR_HOST,
        BookingLinks.CARONTE_HOST,
    )

    /** Gateway di pagamento usati dai due siti (Stripe/PayPal per Delcomar, Nexi per Caronte). */
    private val suffixes = listOf(
        "stripe.com", "paypal.com", "nexi.it", "nexigroup.com", "xpay.nexigroup.com",
    )

    fun hostOf(url: String?): String? = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }?.lowercase()

    fun isHttps(url: String?): Boolean = url?.startsWith("https://", ignoreCase = true) == true

    fun isTrusted(url: String?): Boolean {
        if (!isHttps(url)) return false
        return isTrustedHost(hostOf(url))
    }

    fun isTrustedHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h in exact || suffixes.any { h == it || h.endsWith(".$it") }
    }
}
