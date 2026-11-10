package it.traghetti.maddalena.purchase

import it.traghetti.maddalena.card.PaymentCard
import java.time.LocalTime

/**
 * JavaScript eseguito nella WebView con `evaluateJavascript` (nessun JavascriptInterface:
 * la pagina non può richiamare codice dell'app né leggere la carta di sua iniziativa).
 */
object InjectionScripts {

    /** Escape di una stringa come letterale JS tra apici singoli. */
    fun jsString(s: String): String = buildString {
        append('\'')
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '<' -> append("\\u003c")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> append(c)
            }
        }
        append('\'')
    }

    /**
     * Pagina risultati Delcomar: seleziona la corsa dell'orario scelto facendo click sulla sua
     * card (`data-hdep`). Il click è lo stesso che farebbe l'utente; lo script del sito chiama
     * poi la sua API interna e aggiorna il riepilogo. Ritorna "selected", "already" o "notfound".
     */
    fun delcomarSelectDeparture(time: LocalTime): String {
        val hhmm = "%02d:%02d".format(time.hour, time.minute)
        return """
            (function(){
              var t=${jsString(hhmm)};
              var cards=document.querySelectorAll('.select-andata .card[data-hdep]');
              for(var i=0;i<cards.length;i++){
                var c=cards[i];
                if((c.getAttribute('data-hdep')||'').trim()===t){
                  c.scrollIntoView({block:'center'});
                  if(c.classList.contains('active')) return 'already';
                  c.click();
                  return 'selected';
                }
              }
              return 'notfound';
            })();
        """.trimIndent()
    }

    /**
     * Compila i campi carta presenti nel documento principale (numero, scadenza, intestatario).
     * Riconosce i campi dagli attributi standard `autocomplete="cc-*"` e, in mancanza, da
     * name/id/placeholder. Usa il setter nativo + eventi input/change, così funziona anche con
     * React/Vue. Il CVV non viene mai compilato: l'app non lo conserva.
     * Ritorna il numero di campi compilati (i campi dentro iframe di altri domini, es. Stripe
     * Elements, non sono raggiungibili per la same-origin policy del browser).
     */
    fun fillCard(card: PaymentCard): String {
        val mm = "%02d".format(card.expMonth)
        val yy = "%02d".format(card.expYear % 100)
        val yyyy = card.expYear.toString()
        return """
            (function(){
              var d={num:${jsString(card.number)},name:${jsString(card.holder)},mm:${jsString(mm)},yy:${jsString(yy)},yyyy:${jsString(yyyy)}};
              function set(el,v){
                var p=el.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype;
                var s=Object.getOwnPropertyDescriptor(p,'value');
                el.focus(); if(s&&s.set){s.set.call(el,v);}else{el.value=v;}
                ['input','change','blur'].forEach(function(e){el.dispatchEvent(new Event(e,{bubbles:true}));});
              }
              function key(el){return ((el.getAttribute('autocomplete')||'')+' '+(el.name||'')+' '+(el.id||'')+' '+(el.getAttribute('placeholder')||'')+' '+(el.getAttribute('aria-label')||'')).toLowerCase();}
              var n=0, els=document.querySelectorAll('input,select');
              for(var i=0;i<els.length;i++){
                var el=els[i], k=key(el), ty=(el.type||'').toLowerCase();
                if(ty==='hidden'||ty==='submit'||ty==='checkbox'||ty==='radio'||el.disabled||el.readOnly) continue;
                if(/cvc|cvv|csc|security|sicurezza/.test(k)) continue;
                if(/cc-number|card.?number|cardnumber|numero.?carta|\bpan\b/.test(k)){set(el,d.num);n++;}
                else if(/cc-exp-month|exp.?month|mese/.test(k)){set(el,el.tagName==='SELECT'&&!el.querySelector('option[value="'+d.mm+'"]')?String(parseInt(d.mm,10)):d.mm);n++;}
                else if(/cc-exp-year|exp.?year|anno/.test(k)){set(el,(el.tagName==='SELECT'?!!el.querySelector('option[value="'+d.yyyy+'"]'):(el.maxLength>=4||el.maxLength<0))?d.yyyy:d.yy);n++;}
                else if(/cc-exp|expir|scadenza|mm ?\/ ?(yy|aa)/.test(k)){set(el,d.mm+'/'+d.yy);n++;}
                else if(/cc-name|card.?holder|cardholder|name.?on.?card|intestatario|titolare/.test(k)){set(el,d.name);n++;}
              }
              return n;
            })();
        """.trimIndent()
    }
}
