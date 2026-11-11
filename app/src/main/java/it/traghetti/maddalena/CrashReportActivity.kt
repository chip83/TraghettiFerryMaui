package it.traghetti.maddalena

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Mostra l'errore che ha chiuso l'app all'avvio precedente.
 * Usa solo View classiche e nessuna libreria: deve funzionare anche se il problema è in Compose
 * o in una dipendenza.
 */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReporter.read(this) ?: "Nessun errore registrato."
        val pad = (16 * resources.displayMetrics.density).toInt()

        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            fitsSystemWindows = true
            addView(TextView(context).apply { text = "L'app si è chiusa per un errore"; textSize = 20f; setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(context).apply {
                text = "Qui sotto c'è il dettaglio tecnico. Copialo o condividilo con chi sviluppa l'app, poi riprova ad aprirla."
                setPadding(0, pad / 2, 0, pad / 2)
            })
            addView(button("Copia il dettaglio") {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Errore", report))
                Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
            })
            addView(button("Condividi") {
                runCatching {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Condividi errore"))
                }
            })
            addView(button("Apri l'app") {
                CrashReporter.clear(context)
                startActivity(Intent(context, MainActivity::class.java))
                finish()
            })
            addView(ScrollView(context).apply {
                addView(TextView(context).apply { text = report; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = pad / 2 })
        }
        setContentView(root)
    }
}
