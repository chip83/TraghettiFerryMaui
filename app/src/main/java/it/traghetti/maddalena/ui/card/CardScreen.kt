package it.traghetti.maddalena.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import it.traghetti.maddalena.card.Authenticator
import it.traghetti.maddalena.card.CardValidator
import it.traghetti.maddalena.card.CardVault
import it.traghetti.maddalena.card.PaymentCard
import it.traghetti.maddalena.ui.theme.Sea
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(vault: CardVault, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun say(m: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(m) } }

    var summary by remember { mutableStateOf(vault.summary()) }
    var revealed by remember { mutableStateOf<PaymentCard?>(null) }
    var editing by remember { mutableStateOf(summary == null) }

    var holder by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("La mia carta") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val preview = revealed
            CardPreview(
                number = when {
                    editing -> CardValidator.formatNumber(number).ifEmpty { "•••• •••• •••• ••••" }
                    preview != null -> preview.formattedNumber
                    else -> summary?.let { "•••• •••• •••• " + it.takeLast(4) } ?: "•••• •••• •••• ••••"
                },
                holder = when {
                    editing -> holder.ifBlank { "NOME COGNOME" }
                    preview != null -> preview.holder
                    else -> "••••••••"
                }.uppercase(),
                expiry = when {
                    editing -> formatExpiry(expiry).ifEmpty { "MM/AA" }
                    preview != null -> preview.expiryLabel
                    else -> "••/••"
                },
                brand = when {
                    editing -> CardValidator.brandOf(number).label
                    preview != null -> preview.brand.label
                    else -> summary?.substringBefore(" •").orEmpty()
                },
            )

            if (editing) {
                OutlinedTextField(
                    value = holder, onValueChange = { holder = it.take(40); error = null },
                    label = { Text("Intestatario") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth().testTag("holder"),
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = CardValidator.digits(it).take(19); error = null },
                    label = { Text("Numero carta") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = GroupingTransformation(4, ' '),
                    modifier = Modifier.fillMaxWidth().testTag("number"),
                )
                OutlinedTextField(
                    value = expiry, onValueChange = { expiry = CardValidator.digits(it).take(4); error = null },
                    label = { Text("Scadenza (MM/AA)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = GroupingTransformation(2, '/'),
                    modifier = Modifier.fillMaxWidth().testTag("expiry"),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("card_error")) }
                Button(
                    onClick = {
                        error = CardValidator.validate(holder, number, expiry)
                        if (error == null) {
                            if (!Authenticator.isDeviceSecure(context)) {
                                error = "Imposta prima un blocco schermo (PIN, sequenza o impronta): serve a proteggere la carta."
                            } else {
                                val (m, y) = CardValidator.parseExpiry(expiry)!!
                                runCatching { vault.save(PaymentCard(holder.trim(), number, m, y)) }
                                    .onSuccess {
                                        summary = vault.summary(); editing = false; revealed = null
                                        holder = ""; number = ""; expiry = ""
                                        say("Carta salvata e cifrata su questo dispositivo")
                                    }
                                    .onFailure { error = "Impossibile salvare la carta su questo dispositivo" }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_card"),
                ) { Text("Salva carta") }
                if (summary != null) TextButton(onClick = { editing = false; error = null }) { Text("Annulla") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (revealed != null) revealed = null
                            else (context as? FragmentActivity)?.let { activity ->
                                Authenticator.authenticate(activity, "Mostra i dati della carta") { ok, message ->
                                    if (ok) revealed = vault.load() else message?.let(::say)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (revealed != null) "Nascondi" else "Mostra dati") }
                    OutlinedButton(onClick = { editing = true; revealed = null }, modifier = Modifier.weight(1f)) { Text("Sostituisci") }
                }
                TextButton(onClick = {
                    vault.clear(); summary = null; revealed = null; editing = true
                    say("Carta eliminata dal dispositivo")
                }) { Text("Elimina carta", color = MaterialTheme.colorScheme.error) }
            }

            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.Shield, null, tint = Sea.Live)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "La carta resta solo su questo telefono, cifrata con AES-256 e una chiave custodita " +
                            "dall'Android Keystore. Per usarla serve impronta, volto o PIN del dispositivo. " +
                            "Il CVV non viene mai salvato: lo digiti tu a ogni acquisto. " +
                            "I dati vengono inseriti soltanto nei siti ufficiali delle compagnie e nei loro circuiti di pagamento.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardPreview(number: String, holder: String, expiry: String, brand: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Sea.Deep, Sea.Ocean, Sea.Lagoon)))
            .padding(22.dp)
            .testTag("card_preview"),
    ) {
        Text(brand, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.TopEnd))
        Box(Modifier.align(Alignment.TopStart).size(width = 42.dp, height = 30.dp).clip(MaterialTheme.shapes.small).background(Color(0xFFE7C873)))
        Text(number, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterStart).padding(top = 16.dp))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(holder, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(expiry, color = Color.White.copy(alpha = 0.9f), fontFamily = FontFamily.Monospace)
        }
    }
}

private fun formatExpiry(digits: String) = if (digits.length > 2) digits.take(2) + "/" + digits.drop(2) else digits

/** Inserisce [separator] ogni [group] caratteri solo a video (il valore resta di sole cifre). */
private class GroupingTransformation(private val group: Int, private val separator: Char) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = raw.chunked(group).joinToString(separator.toString())
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset == 0) 0 else offset + (offset - 1) / group
            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / (group + 1)).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
