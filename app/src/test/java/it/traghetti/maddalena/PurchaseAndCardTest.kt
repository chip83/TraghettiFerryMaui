package it.traghetti.maddalena

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.traghetti.maddalena.card.AesGcmBox
import it.traghetti.maddalena.card.CardBrand
import it.traghetti.maddalena.card.CardValidator
import it.traghetti.maddalena.card.CardVault
import it.traghetti.maddalena.card.PaymentCard
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.purchase.BookingLinks
import it.traghetti.maddalena.purchase.BookingRequest
import it.traghetti.maddalena.purchase.InjectionScripts
import it.traghetti.maddalena.purchase.TrustedHosts
import it.traghetti.maddalena.purchase.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.crypto.KeyGenerator

@RunWith(AndroidJUnit4::class)
class PurchaseAndCardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val card = PaymentCard("Mario Rossi", "4242424242424242", 7, 2029)

    // ---- deep link ----

    @Test fun `delcomar deep link reproduces the site's search form`() {
        val url = BookingLinks.delcomar(
            BookingRequest(Company.DELCOMAR, Port.PALAU, LocalDate.of(2026, 11, 14), LocalTime.of(18, 30), adults = 2, children = 1, vehicle = Vehicle.CAR_400, resident = true)
        )
        assertEquals(
            "https://booking.delcomar.it/booking?src=delcomar&tratta=PALAU%7CLA+MADDALENA&depDate=2026-11-14&adt=2&chd=1&eyfs=0&inf=0" +
                "&vei=1&vehicle=Auto+fino+a+mt.+4&animaleqta=0&animale=&faresType=CodPrezzoResLocali&depTime=&retTime=" +
                "&source=booking-search&action=search&vehiclelength=1",
            url,
        )
        val plain = BookingLinks.delcomar(BookingRequest(Company.DELCOMAR, Port.LA_MADDALENA, LocalDate.of(2026, 12, 1), null))
        assertTrue("tratta=LA+MADDALENA%7CPALAU" in plain && "vei=0&vehicle=&" in plain && "CodPrezzoStandard" in plain)
    }

    @Test fun `caronte deep link preselects the route`() {
        assertEquals("https://booking.carontetourist.it/it/isole/start?route=SARD&from=PALA&to=MADD", BookingLinks.caronte(Port.PALAU))
        assertEquals(
            "https://booking.carontetourist.it/it/isole/start?route=SARD&from=MADD&to=PALA",
            BookingLinks.forRequest(BookingRequest(Company.MADDALENA_LINES, Port.LA_MADDALENA, LocalDate.of(2027, 7, 1), null)),
        )
    }

    // ---- domini fidati per la compilazione della carta ----

    @Test fun `card autofill is allowed only on official https hosts`() {
        assertTrue(TrustedHosts.isTrusted("https://booking.delcomar.it/booking?x=1"))
        assertTrue(TrustedHosts.isTrusted("https://booking.carontetourist.it/it/isole/checkout"))
        assertTrue(TrustedHosts.isTrusted("https://checkout.stripe.com/c/pay/abc"))
        assertTrue(TrustedHosts.isTrusted("https://ecommerce.nexi.it/ecomm/DispatcherServlet"))
        assertFalse(TrustedHosts.isTrusted("http://booking.delcomar.it/booking"))            // non cifrato
        assertFalse(TrustedHosts.isTrusted("https://booking.delcomar.it.evil.example/booking")) // dominio simile
        assertFalse(TrustedHosts.isTrusted("https://evilstripe.com/pay"))
        assertFalse(TrustedHosts.isTrusted("https://stripe.com.phish.io/"))
        assertFalse(TrustedHosts.isTrusted(null))
        assertFalse(TrustedHosts.isTrusted("javascript:alert(1)"))
    }

    // ---- script iniettati ----

    @Test fun `js strings are escaped`() {
        assertEquals("'O\\'Brien \\u003c/script> \\\\ a\\nb'", InjectionScripts.jsString("O'Brien </script> \\ a\nb"))
        val evil = InjectionScripts.fillCard(card.copy(holder = "x');alert(1);//"))
        assertTrue("name:'x\\');alert(1);//'" in evil)
    }

    @Test fun `scripts never contain a cvv and are exported for the browser tests`() {
        val fill = InjectionScripts.fillCard(card)
        assertTrue("4242424242424242" in fill && "mm:'07'" in fill && "yy:'29'" in fill && "yyyy:'2029'" in fill)
        val select = InjectionScripts.delcomarSelectDeparture(LocalTime.of(8, 0))
        assertTrue("var t='08:00'" in select)
        // usati da tools/web-check per provarli in un vero Chromium
        File("build/injection").apply { mkdirs() }.also {
            it.resolve("fillCard.js").writeText(fill)
            it.resolve("selectDeparture.js").writeText(InjectionScripts.delcomarSelectDeparture(LocalTime.of(18, 30)))
        }
    }

    // ---- carta ----

    @Test fun `card number validation`() {
        assertTrue(CardValidator.isValidNumber("4242424242424242"))
        assertTrue(CardValidator.isValidNumber("5555555555554444"))
        assertTrue(CardValidator.isValidNumber("378282246310005"))
        assertFalse(CardValidator.isValidNumber("4242424242424241"))
        assertFalse(CardValidator.isValidNumber("42"))
        assertEquals(CardBrand.VISA, CardValidator.brandOf("4242")); assertEquals(CardBrand.MASTERCARD, CardValidator.brandOf("5555"))
        assertEquals(CardBrand.MASTERCARD, CardValidator.brandOf("2223")); assertEquals(CardBrand.AMEX, CardValidator.brandOf("37"))
        assertEquals(CardBrand.MAESTRO, CardValidator.brandOf("6759")); assertEquals(CardBrand.UNKNOWN, CardValidator.brandOf(""))
        assertEquals("4242 4242 4242 4242", CardValidator.formatNumber("4242424242424242"))
        assertEquals("3782 822463 10005", CardValidator.formatNumber("378282246310005"))
    }

    @Test fun `expiry and form validation`() {
        val now = YearMonth.of(2026, 11)
        assertEquals(7 to 2029, CardValidator.parseExpiry("07/29"))
        assertNull(CardValidator.parseExpiry("7/29"))
        assertTrue(CardValidator.isValidExpiry(11, 2026, now)); assertFalse(CardValidator.isValidExpiry(10, 2026, now))
        assertFalse(CardValidator.isValidExpiry(13, 2027, now))
        assertNull(CardValidator.validate("Mario Rossi", "4242 4242 4242 4242", "0729", now))
        assertEquals("Numero di carta non valido", CardValidator.validate("Mario Rossi", "1234", "0729", now))
        assertEquals("Carta scaduta o scadenza non valida", CardValidator.validate("Mario Rossi", "4242424242424242", "0125", now))
        assertNotNull(CardValidator.validate("", "4242424242424242", "0729", now))
        assertFalse("il numero non deve finire nei log", "4242424242424242" in card.toString())
    }

    private fun testBox() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().let { key -> AesGcmBox { key } }

    @Test fun `vault stores the card encrypted and never in clear`() {
        val prefs = context.getSharedPreferences("vault_test", Context.MODE_PRIVATE)
        val box = testBox()
        val vault = CardVault(prefs, box)
        assertFalse(vault.hasCard()); assertNull(vault.load())
        vault.save(card)
        assertEquals(card, CardVault(prefs, box).load())
        assertEquals("Visa •••• 4242", vault.summary())
        val stored = prefs.all.values.joinToString("|")
        assertFalse(stored.contains("4242424242424242")); assertFalse(stored.contains("Mario"))
        // stessa carta, due cifrature diverse (IV casuale)
        val first = prefs.getString("card", null); vault.save(card)
        assertTrue(first != prefs.getString("card", null))
        // chiave diversa (altro dispositivo) o dato manomesso → illeggibile
        assertNull(CardVault(prefs, testBox()).load())
        prefs.edit().putString("card", prefs.getString("card", "")!!.reversed()).apply()
        assertNull(vault.load())
        vault.clear()
        assertFalse(vault.hasCard()); assertNull(vault.summary())
    }
}
