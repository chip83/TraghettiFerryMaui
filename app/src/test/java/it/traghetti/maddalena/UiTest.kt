package it.traghetti.maddalena

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import it.traghetti.maddalena.card.AesGcmBox
import it.traghetti.maddalena.card.CardVault
import it.traghetti.maddalena.card.PaymentCard
import it.traghetti.maddalena.data.ScheduleRepository
import it.traghetti.maddalena.data.TripCache
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.domain.Departure
import it.traghetti.maddalena.domain.Port
import it.traghetti.maddalena.domain.Trip
import it.traghetti.maddalena.purchase.BookingRequest
import it.traghetti.maddalena.ui.card.CardScreen
import it.traghetti.maddalena.ui.home.HomeContent
import it.traghetti.maddalena.ui.home.HomeViewModel
import it.traghetti.maddalena.ui.home.PurchaseSheet
import it.traghetti.maddalena.ui.theme.TraghettiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.LocalTime
import javax.crypto.KeyGenerator

/** Test dell'interfaccia reale (Compose) su Robolectric, con cattura delle schermate in docs/screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class UiTest {
    @get:Rule val compose = createComposeRule()

    private var now = LocalDateTime.of(2027, 8, 10, 10, 20)
    private val ships = listOf("I. Maddalena", "S. Stefano")

    private fun viewModel() = HomeViewModel(
        repository = ScheduleRepository(
            { _, from -> it.traghetti.maddalena.data.StaticTimetable.delcomar(from).mapIndexed { i, t -> Trip(t.company, t.departure, 15, ships[i % 2]) } },
            TripCache(null), clock = { now.atZone(HomeViewModel.ROME).toInstant().toEpochMilli() },
        ),
        locator = { Port.PALAU }, initialPort = Port.LA_MADDALENA, initialAuto = false, clock = { now }, tick = false,
    )

    private fun setHome(vm: HomeViewModel, dark: Boolean = false, onBuy: (Departure) -> Unit = {}) = compose.setContent {
        TraghettiTheme(dark = dark) {
            val state by vm.state.collectAsState()
            HomeContent(
                state, onSwap = vm::swapPorts, onSelectPort = { vm.setPort(it) }, onToggleAuto = { vm.setAutoLocation(it); if (it) vm.locate() },
                onDate = vm::setDate, onTime = vm::setTime, onNow = vm::resetToNow, onToggleCompany = vm::toggleCompany,
                onToggleShowAll = vm::toggleShowAll, onRefresh = vm::refresh, onOpenCard = {}, onBuy = onBuy,
            )
        }
    }

    @Test fun `home shows next departure, lists and live source`() {
        val vm = viewModel(); setHome(vm)
        compose.onNodeWithTag("from_port", useUnmergedTree = true).assertTextContains("La Maddalena")
        compose.onNodeWithTag("hero").assertIsDisplayed()
        compose.onNodeWithText("10:30", useUnmergedTree = true).assertIsDisplayed()       // prossima partenza
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertTextContains("tra 10 min")
        compose.onNodeWithText("Appena partiti").assertIsDisplayed()
        compose.onAllNodesWithTag("row_past").assertCountEquals(3)
        compose.onRoot().captureRoboImage("../docs/screenshots/01_home.png")
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("source"))
        compose.onNodeWithText("aggiornati dal sito ufficiale", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage("../docs/screenshots/02_home_lista.png")
    }

    @Test fun `swap, gps and time interactions update the screen`() {
        val vm = viewModel(); setHome(vm)
        compose.onNodeWithTag("swap").performClick()
        compose.onNodeWithTag("from_port", useUnmergedTree = true).assertTextContains("Palau")
        compose.onNodeWithTag("gps_chip").assertTextContains("GPS spento")
        compose.onNodeWithTag("swap").performClick()
        compose.onNodeWithTag("gps_chip").performClick()                                   // GPS → Palau (finto)
        compose.onNodeWithTag("gps_chip").assertTextContains("GPS automatico")
        compose.onNodeWithTag("from_port", useUnmergedTree = true).assertTextContains("Palau")
        compose.runOnIdle { vm.setTime(LocalTime.of(23, 40)) }
        compose.onNodeWithTag("now_chip").assertIsDisplayed()
        compose.onNodeWithText("PRIMA PARTENZA UTILE", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertTextContains("il giorno dopo", substring = true)
        compose.onRoot().captureRoboImage("../docs/screenshots/03_home_notte.png")
        compose.onNodeWithTag("now_chip").performClick()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertTextContains("tra 10 min")
    }

    @Test fun `buy button passes the chosen departure`() {
        var bought: Departure? = null
        setHome(viewModel(), onBuy = { bought = it })
        compose.onNodeWithTag("hero_buy").performClick()
        assertEquals(LocalTime.of(10, 30), bought!!.departure.toLocalTime())
        assertEquals(Company.DELCOMAR, bought!!.company)
    }

    @Test fun `dark theme and winter timetable`() {
        now = LocalDateTime.of(2026, 12, 20, 18, 5)
        setHome(viewModel(), dark = true)
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("source"))
        compose.onNodeWithText("dal 1° aprile al 31 ottobre", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage("../docs/screenshots/04_home_scuro_inverno.png")
    }

    @Test fun `purchase sheet builds the booking request`() {
        var request: BookingRequest? = null
        val dep = LocalDateTime.of(2026, 11, 14, 18, 30).let { Departure(Company.DELCOMAR, Port.PALAU, it, it.plusMinutes(15), "S. Stefano", true) }
        compose.setContent { TraghettiTheme { PurchaseSheet(dep, defaultResident = false, onDismiss = {}, onConfirm = { request = it }) } }
        compose.onNodeWithText("Palau → La Maddalena").assertIsDisplayed()
        compose.onNodeWithText("Continua sul sito Delcomar", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Più Adulti")).performClick()
        captureScreenRoboImage("../docs/screenshots/05_acquisto_opzioni.png")
        compose.onNodeWithTag("sheet_continue").performClick()
        assertNotNull(request)
        assertEquals(2, request!!.adults); assertEquals(LocalTime.of(18, 30), request!!.time); assertEquals(Port.PALAU, request!!.from)
    }

    private fun vault(name: String): CardVault {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return CardVault(ctx.getSharedPreferences(name, Context.MODE_PRIVATE), AesGcmBox { key })
    }

    @Test fun `card form validates input and shows live preview`() {
        val vault = vault("ui_empty")
        compose.setContent { TraghettiTheme { CardScreen(vault, onBack = {}) } }
        compose.onNodeWithTag("holder").performTextInput("Mario Rossi")
        compose.onNodeWithTag("number").performTextInput("4242424242424241")
        compose.onNodeWithTag("expiry").performTextInput("0729")
        compose.onNodeWithTag("save_card").performClick()
        compose.onNodeWithTag("card_error").assertTextContains("Numero di carta non valido")
        assertFalse(vault.hasCard())
        compose.onNodeWithText("MARIO ROSSI", useUnmergedTree = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage("../docs/screenshots/06_carta_modulo.png")
    }

    @Test fun `saved card is shown masked`() {
        val vault = vault("ui_saved").apply { save(PaymentCard("Mario Rossi", "4242424242424242", 7, 2029)) }
        compose.setContent { TraghettiTheme { CardScreen(vault, onBack = {}) } }
        compose.onNodeWithText("•••• •••• •••• 4242", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Mostra dati").assertIsDisplayed()
        compose.onAllNodesWithTag("number").assertCountEquals(0)
        compose.onRoot().captureRoboImage("../docs/screenshots/07_carta_salvata.png")
    }
}
