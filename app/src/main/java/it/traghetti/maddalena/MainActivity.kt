package it.traghetti.maddalena

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import it.traghetti.maddalena.purchase.BookingRequest
import it.traghetti.maddalena.ui.card.CardScreen
import it.traghetti.maddalena.ui.home.HomeScreen
import it.traghetti.maddalena.ui.home.HomeViewModel
import it.traghetti.maddalena.ui.purchase.PurchaseScreen
import it.traghetti.maddalena.ui.theme.TraghettiTheme

private sealed interface Screen {
    data object Home : Screen
    data object Card : Screen
    data class Purchase(val request: BookingRequest) : Screen
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as FerryApp
        setContent {
            TraghettiTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))

                // Le schermate con dati di pagamento non compaiono in screenshot e app recenti.
                val sensitive = screen != Screen.Home
                DisposableEffect(sensitive) {
                    if (sensitive) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    onDispose { }
                }
                BackHandler(enabled = screen == Screen.Card) { screen = Screen.Home }

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen",
                ) { current ->
                    when (current) {
                        Screen.Home -> HomeScreen(
                            viewModel = homeVm,
                            locator = app.locator,
                            settings = app.settings,
                            onOpenCard = { screen = Screen.Card },
                            onPurchase = { screen = Screen.Purchase(it) },
                        )
                        Screen.Card -> CardScreen(vault = app.vault, onBack = { screen = Screen.Home })
                        is Screen.Purchase -> PurchaseScreen(
                            request = current.request,
                            vault = app.vault,
                            onClose = { screen = Screen.Home },
                        )
                    }
                }
            }
        }
    }
}
