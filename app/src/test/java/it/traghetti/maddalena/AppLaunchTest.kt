package it.traghetti.maddalena

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Avvia l'app vera (MainActivity + FerryApp), come fa il telefono: è il test che mancava quando
 * l'app si chiudeva all'apertura per la richiesta del permesso di posizione.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class AppLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `first launch without location permission asks for it and shows the timetable`() {
        compose.onNodeWithTag("hero").assertIsDisplayed()
        compose.onNodeWithText("Traghetti").assertIsDisplayed()
        // la richiesta di permesso è partita davvero
        val asked = shadowOf(compose.activity).lastRequestedPermission
        check(asked != null && Manifest.permission.ACCESS_FINE_LOCATION in asked.requestedPermissions) { "permesso non richiesto: $asked" }
    }

    @Test fun `launch with permission already granted, then open card screen and come back`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("hero").assertIsDisplayed()
        compose.onNodeWithTag("gps_chip").performClick()   // GPS spento
        compose.onNodeWithTag("gps_chip").performClick()   // GPS di nuovo acceso
        compose.onNodeWithTag("open_card").performClick()
        compose.onNodeWithText("La mia carta").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("hero").assertIsDisplayed()
    }

    @Test fun `buy flow opens the purchase screen on the official site`() {
        // gli orari arrivano dal sito (o, senza rete, dall'orario incorporato dopo i tentativi falliti)
        compose.waitUntil(120_000) { compose.onAllNodesWithTag("hero_buy").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hero_buy").performClick()
        compose.onNodeWithTag("sheet_continue").performClick()
        compose.onNodeWithTag("host_row", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("booking.", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }
}
