# Report: riscrittura in Kotlin, acquisto biglietti, test

Data delle verifiche: 10 novembre 2026.

## 1. In sintesi

- L'app è stata riscritta da .NET MAUI a **Android nativo in Kotlin con Jetpack Compose**.
  Tutte le funzioni precedenti sono state mantenute e migliorate.
- L'acquisto avviene **sul sito ufficiale della compagnia, aperto dentro l'app** già impostato sulla
  corsa scelta. La carta dell'utente è custodita cifrata sul telefono e inserita nel modulo di
  pagamento con un tocco.
- **Non è un acquisto completamente automatico "in un tocco"**: l'utente conferma i passaggi del
  sito, digita il CVV e approva il pagamento con la propria banca (3-D Secure). Il motivo è spiegato
  al punto 3.4: non è una scelta di comodo, è un limite che non si può aggirare in modo lecito.
- **38 test automatici su 38 superati**, più **9 controlli su 9** eseguiti in un vero browser
  contro i siti reali. APK debug e release compilati, lint senza errori.
- **Non verificato**: l'app non è stata eseguita su un telefono o emulatore (l'ambiente di lavoro
  non ne ha uno). Vedi punto 5 per cosa resta da provare a mano.

## 2. Cosa è cambiato rispetto alla versione MAUI

| Funzione | Prima (MAUI) | Ora (Kotlin) |
|---|---|---|
| Porto di partenza | GPS oppure interruttore "manuale" | GPS (porto più vicino), scelta manuale, pulsante di inversione; la scelta viene ricordata |
| Ultimi 3 partiti / prossimi 6 | Sì, ma confronto fra stringhe nello stesso giorno | Sì, su date complete, più "Mostra tutte" |
| Dopo le 23:30 | **Nessuna partenza mostrata** (bug) | Mostra le corse dopo mezzanotte, indicate come "domani" |
| Scelta data/ora | Due caselle di testo `gg/mm/aaaa` e `hh:mm` | Calendario e orologio Material 3, pulsante "Adesso" |
| Orari Delcomar | Fissi nel codice | Letti dal sito ufficiale per il giorno scelto, con nome della nave; cache e ripiego offline |
| Orari seconda compagnia | Un solo orario tutto l'anno | Tre fasce stagionali ufficiali; fuori stagione (nov–mar) la compagnia non compare |
| Durata traversata Delcomar | 20 minuti | 15 minuti (valore pubblicato dal sito) |
| Aggiornamento dell'ora | Solo all'apertura | Ogni 15 secondi e al ritorno in primo piano; conto alla rovescia |
| Fuso orario | Quello del telefono | Sempre Europe/Rome |
| Acquisto biglietto | Assente | Dentro l'app, vedi punto 3 |
| Grafica | Schede bianche, pulsanti blu/arancio | Intestazione sfumata "mare", scheda "Prossima partenza", colori per compagnia, tema scuro, schermo intero |
| Piattaforme | Android + Windows | Solo Android (8.0 o superiore) |
| Dimensione | Runtime .NET incluso | APK release 2,2 MB |

Due cose da sapere:

- **Windows non è più supportato.** Una riscrittura in Kotlin con interfaccia nativa Android non
  gira su Windows. Se serve, si può valutare Compose Multiplatform in un secondo momento.
- **Android minimo 8.0** (la versione MAUI dichiarava 5.0). Scelta fatta dopo che il lint ha
  segnalato che le API di data/ora usate non esistono su Android 7.

Gli orari incorporati sono stati sostituiti con quelli pubblicati oggi dalle due compagnie:
Delcomar "in vigore dal 1° novembre al 31 marzo 2027" (34 corse per direzione) e Maddalena Lines
(10 / 19 / 22 corse per direzione secondo la fascia). La durata della traversata Maddalena Lines
(20 minuti) è una stima: il sito non la pubblica.

## 3. Acquisto del biglietto: il sistema trovato

### 3.1 Cosa offrono davvero i due siti

Confermo che **nessuno dei due ha API pubbliche d'acquisto**. Analizzando le pagine ho però trovato
che entrambi accettano parametri nell'indirizzo, e questo basta per aprirli già "a metà strada".

**Delcomar** (`booking.delcomar.it`)

- Il modulo di ricerca del sito è una normale richiesta GET verso `/booking`. Tutti i campi si
  possono quindi scrivere nell'indirizzo:
  `tratta=PALAU|LA MADDALENA`, `depDate=2026-11-14`, `adt` (adulti), `chd` (bambini 6-11),
  `vei` + `vehicle` (veicolo), `faresType` (standard o residenti), più alcuni campi fissi.
  Servono tutti: con i soli tratta/data la pagina torna vuota.
- La pagina dei risultati elenca le corse del giorno come schede con l'attributo `data-hdep="18:30"`.
  Toccandone una, il sito registra la corsa e calcola il totale.
- La stessa pagina è anche la **fonte degli orari aggiornati**: l'app la scarica (senza login e
  senza creare prenotazioni) e ne legge orario di partenza, arrivo e nave.
- Pagamento: **Stripe** oppure **PayPal** (si ricava dallo script del sito).
- Il sito pubblica le corse solo per le date già a calendario: per luglio 2027 oggi non restituisce
  nulla, e l'app in quel caso usa l'orario incorporato.

**Caronte & Tourist / Maddalena Lines** (`booking.carontetourist.it`)

- È un'applicazione a 6 passaggi. L'indirizzo
  `/it/isole/start?route=SARD&from=PALA&to=MADD` la apre con regione, partenza e arrivo già scelti.
- Data, corsa e passeggeri non si possono passare nell'indirizzo: si scelgono nei passaggi successivi.
- Pagamento: circuito **Nexi** (ricavato dal codice del sito; non ho percorso il pagamento).
- Il servizio è stagionale (1° aprile – 31 ottobre): oggi non ci sono corse acquistabili.

### 3.2 Come funziona nell'app

1. L'utente tocca **Acquista** su una corsa. Un pannello chiede adulti, bambini, veicolo e tariffa
   residenti (per Delcomar).
2. L'app apre il sito ufficiale in una **WebView interna**, all'indirizzo costruito con quei dati.
3. **Delcomar**: a pagina caricata l'app esegue un piccolo script che fa click sulla scheda
   dell'orario scelto. L'utente si ritrova con la corsa selezionata, il totale calcolato e il
   pulsante "Procedi". **Caronte**: l'utente si ritrova al passaggio 2 di 6 con la tratta impostata.
4. L'utente prosegue nel sito (dati passeggero, condizioni) fino alla pagina di pagamento.
5. In basso c'è la **barra della carta**: "Usa Visa •••• 4242" chiede impronta/volto/PIN, poi offre
   **Compila carta** (riempie numero, scadenza e intestatario) e tre pulsanti **Copia**.
6. L'utente digita il **CVV**, conferma e approva con la banca. Il biglietto arriva per email dalla
   compagnia, come da sito.

La barra in alto mostra sempre il dominio in cui ci si trova, con un lucchetto verde se è uno dei
domini ufficiali.

### 3.3 Come è protetta la carta

- Salvata **solo sul telefono**, cifrata con AES-256-GCM; la chiave sta nell'**Android Keystore**
  e non è esportabile. Esclusa dai backup.
- Per mostrarla o usarla serve **impronta, volto o PIN del dispositivo**. Senza blocco schermo
  l'app non permette di salvarla.
- Il **CVV non viene mai salvato** (le regole del circuito carte lo vietano).
- "Compila carta" funziona **solo su domini ufficiali in HTTPS**: i due siti di prenotazione e
  Stripe, PayPal, Nexi. Altrove (per esempio la pagina della banca) è disattivato.
- La pagina web **non può leggere la carta**: l'app non espone alcun ponte verso il codice del sito,
  scrive nei campi solo quando l'utente tocca il pulsante.
- Le schermate di acquisto e carta sono escluse da screenshot e anteprima delle app recenti.
- Il copia-incolla è marcato come dato sensibile e viene cancellato dagli appunti dopo 60 secondi.
- La WebView rifiuta pagine non cifrate, certificati non validi, accesso ai file e finestre popup.

Nota: la protezione biometrica è un controllo dell'app prima di decifrare. La chiave non è legata
all'autenticazione a livello hardware; è un possibile irrobustimento futuro.

### 3.4 Perché non un acquisto del tutto automatico

Ho valutato di far completare l'acquisto all'app da sola (o da un server) con i dati della carta.
L'ho scartato per tre motivi concreti:

- **Autenticazione forte obbligatoria (PSD2 / 3-D Secure).** Per i pagamenti online in Europa la
  banca chiede quasi sempre una conferma al titolare (notifica nell'app della banca o codice SMS).
  Nessun software può darla al posto suo.
- **CVV.** Serve a ogni pagamento e non può essere conservato.
- **Moduli di pagamento isolati.** Stripe e gli altri circuiti mostrano spesso i campi carta in
  riquadri di un altro dominio, che il browser impedisce di toccare dall'esterno.

Conseguenza pratica per "Compila carta": funziona quando i campi sono nella pagina principale
(tipico delle pagine di pagamento a tutto schermo). Quando sono in un riquadro isolato l'app lo
segnala e si usano i pulsanti **Copia**, oppure il riempimento automatico di Android (Google),
che la WebView supporta.

Rischio da tenere presente: tutto questo dipende dalla struttura attuale dei due siti. Se Delcomar
cambia i parametri o il markup, la preselezione della corsa e gli orari aggiornati smettono di
funzionare; l'app è scritta per degradare senza bloccarsi (si apre comunque il sito, gli orari
tornano a quelli incorporati), ma andrà aggiornata.

## 4. Test eseguiti

### 4.1 Test automatici: 38 superati, 0 falliti

| Gruppo | Test | Cosa verificano |
|---|---|---|
| Logica orari | 8 | Ultimi 3 / prossimi 6, corsa all'ora esatta, corse dopo mezzanotte, filtri, conto alla rovescia, calendario stagionale, numero di corse pubblicate, porto più vicino da GPS |
| Dati | 8 | Lettura di **due pagine reali salvate** da booking.delcomar.it (34 e 33 corse, navi, durata 15 min), pagine anomale, cache su disco e file rovinati, rete → cache → orario incorporato |
| Sito reale | 1 | Scarica davvero gli orari di domani da booking.delcomar.it nelle due direzioni: 33 e 34 corse, navi Enzo D, I. Maddalena, S. Stefano |
| Stato schermata | 6 | GPS e scelta manuale, GPS non disponibile, data/ora a scelta, scorrere dell'orologio e cambio giorno, filtri |
| Acquisto e carta | 8 | Indirizzi di prenotazione identici a quelli del sito, domini fidati e domini-trappola, protezione degli script, validazione carta, portafoglio cifrato (nessun dato in chiaro, dato manomesso o chiave diversa → illeggibile) |
| Interfaccia | 7 | Schermate Compose reali: prossima partenza, liste, inversione tratta, GPS, cambio ora, tema scuro invernale, pannello d'acquisto, modulo carta con errore, carta salvata mascherata |

Le schermate in `docs/screenshots/01…07` sono prodotte da questi test.

### 4.2 Controlli in un vero browser sui siti reali: 9 superati su 9

Eseguiti con Chromium in formato telefono, usando **gli stessi script generati dall'app**:

- Delcomar: l'indirizzo costruito dall'app apre 34 corse per la data richiesta.
- Delcomar: lo script seleziona la corsa delle 18:30; il sito la evidenzia e calcola
  **9,20 € per 2 adulti**, con il pulsante "Procedi" (`08_web_delcomar_corsa_selezionata.png`).
- Caronte: il sito riceve regione, partenza e arrivo e mostra "Palau → La Maddalena" al passaggio
  2 di 6 (`09_web_caronte_tratta.png`).
- Compilazione carta su moduli di prova: numero, intestatario, scadenza (campo unico, menu a
  tendina, anno a 2 o 4 cifre) compilati; CVV, email e campi nascosti lasciati intatti.

Mi sono fermato alla selezione della corsa: **non ho effettuato alcun acquisto né inserito dati di
pagamento reali**.

### 4.3 Compilazione e analisi

- APK debug: 17,7 MB. APK release ottimizzato: 2,2 MB (**non firmato**).
- Lint Android: 0 errori. Ha individuato un problema reale (API di data/ora assenti su Android 7),
  risolto alzando la versione minima a 8.0.

## 5. Cosa non è stato verificato

L'ambiente in cui ho lavorato non ha un telefono né un emulatore Android. Restano quindi da provare
su un dispositivo reale:

1. Avvio dell'app e richiesta del permesso di posizione; rilevamento GPS sul posto.
2. La WebView vera di Android con i due siti (i controlli del punto 4.2 usano Chromium desktop in
   formato telefono: stesso motore, ma non la stessa app).
3. Salvataggio della carta con l'Android Keystore e sblocco con impronta/PIN.
4. **Un acquisto reale completo**, in particolare "Compila carta" sulla pagina di pagamento di
   Stripe (Delcomar) e di Nexi (Caronte): non conosco la struttura esatta di quelle pagine e mi
   aspetto che in almeno un caso servano i pulsanti Copia.
5. Il percorso Caronte oltre il passaggio 2: verificabile solo da aprile, a servizio attivo.

Altri punti aperti:

- **Firma e pubblicazione**: per installare l'APK release o pubblicarlo serve una chiave di firma.
- **Librerie**: ho usato versioni stabili che conosco bene (Compose BOM 2024.12, AGP 8.7). Il lint
  segnala versioni più recenti disponibili; l'aggiornamento è consigliabile ma non urgente.
- **Note dell'orario Delcomar**: il sito indica variazioni in alcuni giorni (martedì/venerdì e
  martedì/mercoledì/sabato non festivi). L'orario incorporato non le riporta; quello scaricato dal
  sito per il giorno scelto sì, quando è disponibile.
- **Condizioni d'uso dei siti**: l'app legge una pagina pubblica di Delcomar per gli orari, con
  cache di 6 ore. È un uso leggero, ma per una pubblicazione sugli store conviene chiedere il
  consenso alle due compagnie (anche per l'uso dei nomi).
