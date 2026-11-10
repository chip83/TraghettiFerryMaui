package it.traghetti.maddalena.ui.purchase

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import it.traghetti.maddalena.card.Authenticator
import it.traghetti.maddalena.card.CardVault
import it.traghetti.maddalena.card.PaymentCard
import it.traghetti.maddalena.domain.Company
import it.traghetti.maddalena.purchase.BookingLinks
import it.traghetti.maddalena.purchase.BookingRequest
import it.traghetti.maddalena.purchase.InjectionScripts
import it.traghetti.maddalena.purchase.TrustedHosts
import it.traghetti.maddalena.ui.theme.Sea
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Acquisto dentro l'app: il sito ufficiale della compagnia in una WebView "blindata",
 * aperto già sulla tratta/data/passeggeri scelti, con la corsa preselezionata (Delcomar)
 * e una barra per inserire i dati della carta custodita nel portafoglio cifrato.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(request: BookingRequest, vault: CardVault, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val startUrl = remember(request) { BookingLinks.forRequest(request) }

    var currentUrl by remember { mutableStateOf(startUrl) }
    var progress by remember { mutableIntStateOf(0) }
    var confirmClose by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<PaymentCard?>(null) }
    var departureSelected by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun say(message: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else confirmClose = true
    }
    DisposableEffect(Unit) {
        onDispose {
            card = null
            webView?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
            webView = null
        }
    }

    val host = TrustedHosts.hostOf(currentUrl).orEmpty()
    val trusted = TrustedHosts.isTrusted(currentUrl)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Acquisto ${request.company.displayName}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("host_row")) {
                                Icon(
                                    if (trusted) Icons.Rounded.Lock else Icons.Rounded.Warning, null, Modifier.size(13.dp),
                                    tint = if (trusted) Sea.Live else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = { confirmClose = true }) { Icon(Icons.Rounded.Close, "Chiudi") } },
                    actions = {
                        IconButton(onClick = { webView?.reload() }) { Icon(Icons.Rounded.Refresh, "Ricarica") }
                        IconButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                        }) { Icon(Icons.Rounded.OpenInBrowser, "Apri nel browser") }
                    },
                )
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            CardBar(
                summary = vault.summary(),
                card = card,
                autofillAllowed = trusted,
                onUnlock = {
                    val activity = context as? FragmentActivity
                    if (activity == null) say("Autenticazione non disponibile")
                    else Authenticator.authenticate(activity, "Usa la carta salvata") { ok, message ->
                        if (ok) card = vault.load().also { if (it == null) say("Impossibile leggere la carta: salvala di nuovo") }
                        else message?.let(::say)
                    }
                },
                onFill = {
                    val c = card ?: return@CardBar
                    // Ricontrollo sull'URL reale della WebView al momento del tocco.
                    if (!TrustedHosts.isTrusted(webView?.url)) {
                        say("Compilazione automatica disattivata su questo dominio: usa Copia")
                    } else webView?.evaluateJavascript(InjectionScripts.fillCard(c)) { result ->
                        val n = result?.trim('"')?.toIntOrNull() ?: 0
                        say(
                            if (n > 0) "Compilati $n campi. Inserisci tu il CVV e conferma."
                            else "Nessun campo carta compilabile qui: tocca il campo e usa i pulsanti Copia."
                        )
                    }
                },
                onCopy = { label, value ->
                    copySensitive(context, label, value)
                    say("$label copiato: incollalo nel campo. Verrà rimosso dagli appunti tra 60 secondi.")
                    scope.launch { delay(60_000); clearClipboard(context) }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("webview"),
            factory = { ctx ->
                createWebView(
                    ctx,
                    onUrl = { currentUrl = it },
                    onProgress = { progress = it },
                    onPageFinished = { view, url ->
                        val time = request.time
                        if (request.company == Company.DELCOMAR && time != null && !departureSelected &&
                            TrustedHosts.hostOf(url) == BookingLinks.DELCOMAR_HOST && Uri.parse(url).path?.startsWith("/booking") == true
                        ) {
                            view.evaluateJavascript(InjectionScripts.delcomarSelectDeparture(time)) { result ->
                                when (result?.trim('"')) {
                                    "selected", "already" -> { departureSelected = true; say("Corsa delle %02d:%02d selezionata".format(time.hour, time.minute)) }
                                    "notfound" -> { departureSelected = true; say("Corsa non trovata sul sito: scegli l'orario dall'elenco") }
                                }
                            }
                        }
                    },
                    onBlocked = { say("Collegamento non sicuro bloccato") },
                ).also { webView = it; it.loadUrl(startUrl) }
            },
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Chiudere l'acquisto?") },
            text = { Text("Se non hai completato il pagamento, la prenotazione in corso andrà persa.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("Chiudi") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Resta") } },
        )
    }
}

@Composable
private fun CardBar(
    summary: String?,
    card: PaymentCard?,
    autofillAllowed: Boolean,
    onUnlock: () -> Unit,
    onFill: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            when {
                summary == null -> Text(
                    "Nessuna carta salvata. Puoi inserirla a mano qui sopra, oppure salvarla dalla schermata «La mia carta» per i prossimi acquisti.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                card == null -> FilledTonalButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth().testTag("unlock_card")) {
                    Icon(Icons.Rounded.CreditCard, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Usa $summary")
                }
                else -> Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onFill, enabled = autofillAllowed, modifier = Modifier.testTag("fill_card")) { Text("Compila carta") }
                    CopyChip("Numero") { onCopy("Numero carta", card.number) }
                    CopyChip("Scadenza ${card.expiryLabel}") { onCopy("Scadenza", card.expiryLabel) }
                    CopyChip("Intestatario") { onCopy("Intestatario", card.holder) }
                }
            }
        }
    }
}

@Composable
private fun CopyChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)) })
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    onUrl: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onPageFinished: (WebView, String) -> Unit,
    onBlocked: () -> Unit,
): WebView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = true           // i due siti sono applicazioni JS
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        safeBrowsingEnabled = true
        setGeolocationEnabled(false)
    }
    // I moduli di pagamento (Stripe, PayPal, Nexi, 3-D Secure) vivono in iframe di terze parti.
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "https" -> false
                "tel", "mailto" -> { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }; true }
                // http in chiaro, intent:, file:, javascript: … mai dentro la pagina di pagamento
                else -> { if (request.isForMainFrame) onBlocked(); true }
            }
        }
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onUrl(url)
        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = onUrl(url)
        override fun onPageFinished(view: WebView, url: String) { onUrl(url); onPageFinished(view, url) }
        // onReceivedSslError non è ridefinito: con certificato non valido il caricamento viene annullato.
    }
}

private fun copySensitive(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    // Android 13+: nasconde il contenuto nell'anteprima degli appunti.
    clip.description.extras = PersistableBundle().apply {
        putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
}

private fun clearClipboard(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
