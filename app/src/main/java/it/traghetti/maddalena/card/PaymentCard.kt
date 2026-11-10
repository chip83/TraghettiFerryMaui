package it.traghetti.maddalena.card

import java.time.YearMonth

/**
 * Dati carta conservati dall'app. Il CVV/CVC non è previsto di proposito: le regole PCI DSS
 * vietano di memorizzarlo e va digitato a ogni acquisto.
 */
data class PaymentCard(
    val holder: String,
    /** Solo cifre. */
    val number: String,
    val expMonth: Int,
    /** Anno a 4 cifre. */
    val expYear: Int,
) {
    val brand: CardBrand get() = CardValidator.brandOf(number)
    val last4: String get() = number.takeLast(4)
    val masked: String get() = "•••• ".repeat(3) + last4
    val expiryLabel: String get() = "%02d/%02d".format(expMonth, expYear % 100)
    val formattedNumber: String get() = CardValidator.formatNumber(number)

    // Evita che il numero finisca per errore in log o crash report.
    override fun toString(): String = "PaymentCard(${brand.label} ••••$last4)"
}

enum class CardBrand(val label: String) {
    VISA("Visa"), MASTERCARD("Mastercard"), AMEX("American Express"), MAESTRO("Maestro"), UNKNOWN("Carta")
}

object CardValidator {
    fun digits(input: String): String = input.filter(Char::isDigit)

    fun luhn(number: String): Boolean {
        if (number.isEmpty() || !number.all(Char::isDigit)) return false
        var sum = 0
        number.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    fun brandOf(number: String): CardBrand {
        val two = number.take(2).toIntOrNull() ?: return CardBrand.UNKNOWN
        val four = number.take(4).toIntOrNull() ?: 0
        return when {
            number.startsWith("4") -> CardBrand.VISA
            two in 51..55 || four in 2221..2720 -> CardBrand.MASTERCARD
            two == 34 || two == 37 -> CardBrand.AMEX
            two == 50 || two in 56..69 -> CardBrand.MAESTRO
            else -> CardBrand.UNKNOWN
        }
    }

    fun isValidNumber(number: String): Boolean = number.length in 12..19 && luhn(number)

    fun isValidExpiry(month: Int, year: Int, now: YearMonth = YearMonth.now()): Boolean =
        month in 1..12 && year in now.year..now.year + 25 && !YearMonth.of(year, month).isBefore(now)

    /** "MM/AA" o "MMAA" → (mese, anno a 4 cifre). */
    fun parseExpiry(input: String): Pair<Int, Int>? {
        val d = digits(input)
        if (d.length != 4) return null
        return d.take(2).toInt() to 2000 + d.drop(2).toInt()
    }

    fun formatNumber(number: String): String =
        if (brandOf(number) == CardBrand.AMEX && number.length == 15)
            "${number.take(4)} ${number.substring(4, 10)} ${number.drop(10)}"
        else number.chunked(4).joinToString(" ")

    /** Errore in italiano, o null se i dati sono validi. */
    fun validate(holder: String, number: String, expiry: String, now: YearMonth = YearMonth.now()): String? {
        if (holder.trim().length < 3) return "Inserisci l'intestatario come riportato sulla carta"
        if (!isValidNumber(digits(number))) return "Numero di carta non valido"
        val exp = parseExpiry(expiry) ?: return "Scadenza non valida (MM/AA)"
        if (!isValidExpiry(exp.first, exp.second, now)) return "Carta scaduta o scadenza non valida"
        return null
    }
}
