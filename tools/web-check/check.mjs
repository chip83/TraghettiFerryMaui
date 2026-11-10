// Prova in un vero Chromium (viewport da telefono) gli script che l'app inietta nella WebView.
// Gli script sono quelli generati dal codice Kotlin: li esporta il test PurchaseAndCardTest
// in app/build/injection. Uso:  node tools/web-check/check.mjs
// Non viene effettuato alcun acquisto: ci si ferma alla selezione della corsa.
import { createRequire } from 'node:module';
const { chromium, devices } = createRequire(import.meta.url)('playwright');   // funziona anche con Playwright installato globalmente (NODE_PATH)
import { readFileSync, mkdirSync } from 'node:fs';

const inj = f => readFileSync(new URL(`../../app/build/injection/${f}`, import.meta.url), 'utf8');
const shots = new URL('../../docs/screenshots/', import.meta.url).pathname;
mkdirSync(shots, { recursive: true });
const proxy = process.env.HTTPS_PROXY ? { proxy: { server: process.env.HTTPS_PROXY } } : {};
const browser = await chromium.launch(proxy);
const ctx = await browser.newContext({ ...devices['Pixel 7'], locale: 'it-IT' });
let failed = 0;
const check = (name, ok, detail = '') => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  → ' + detail : ''}`); if (!ok) failed++; };
const open = async (page, url) => { for (let i = 0; ; i++) { try { return await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 }); } catch (e) { if (i >= 4) throw e; await page.waitForTimeout(3000); } } };

// 1) Delcomar: deep link + selezione automatica della corsa delle 18:30
{
  const d = new Date(Date.now() + 3 * 864e5).toISOString().slice(0, 10);
  const url = `https://booking.delcomar.it/booking?src=delcomar&tratta=PALAU%7CLA+MADDALENA&depDate=${d}&adt=2&chd=0&eyfs=0&inf=0&vei=0&vehicle=&animaleqta=0&animale=&faresType=CodPrezzoStandard&depTime=&retTime=&source=booking-search&action=search&vehiclelength=1`;
  const page = await ctx.newPage();
  await open(page, url);
  await page.waitForSelector('.select-andata .card[data-hdep]', { timeout: 30000 });
  const n = await page.locator('.select-andata .card[data-hdep]').count();
  check('Delcomar: il deep link apre i risultati della tratta/data', n >= 15, `${n} corse per il ${d}`);
  const before = (await page.locator('body').innerText()).match(/Totale\s*([\d.,]+)\s*€/)?.[1];
  const result = await page.evaluate(inj('selectDeparture.js'));
  check('Delcomar: lo script seleziona la corsa 18:30', result === 'selected', `risultato "${result}"`);
  await page.waitForTimeout(6000);
  const active = await page.evaluate(() => [...document.querySelectorAll('.select-andata .card')].filter(c => c.classList.contains('active') || c.classList.contains('selected')).map(c => c.getAttribute('data-hdep')));
  const text = await page.locator('body').innerText();
  const after = text.match(/Totale\s*([\d.,]+)\s*€/)?.[1];
  check('Delcomar: il sito registra la corsa scelta', active.includes('18:30') || (after && after !== before && after !== '0,00'), `card attive ${JSON.stringify(active)}, totale ${before} € → ${after} €`);
  const again = await page.evaluate(inj('selectDeparture.js'));
  console.log(`INFO  seconda esecuzione dello script: "${again}"`);
  const next = await page.evaluate(() => [...document.querySelectorAll('a.btn, button.btn, input[type=submit]')].map(e => (e.innerText || e.value || '').trim()).filter(Boolean));
  console.log('INFO  pulsanti presenti dopo la selezione:', JSON.stringify([...new Set(next)].slice(0, 12)));
  await page.screenshot({ path: shots + '08_web_delcomar_corsa_selezionata.png' });
  await page.close();
}

// 2) Caronte & Tourist: deep link con tratta preimpostata
{
  const page = await ctx.newPage();
  await open(page, 'https://booking.carontetourist.it/it/isole/start?route=SARD&from=PALA&to=MADD');
  await page.waitForTimeout(6000);
  const data = await page.evaluate(() => { const p = JSON.parse(document.getElementById('__NEXT_DATA__').textContent).props.pageProps; return [p.queryRoute, p.queryDeparture, p.queryArrival]; });
  check('Caronte: il sito riceve tratta, partenza e arrivo dal link', data.join() === 'SARD,PALA,MADD', data.join(' / '));
  const text = (await page.locator('main, body').first().innerText()).toUpperCase();
  check('Caronte: la procedura mostra Palau e La Maddalena', text.includes('PALAU') && text.includes('LA MADDALENA'));
  await page.screenshot({ path: shots + '09_web_caronte_tratta.png' });
  await page.close();
}

// 3) Compilazione carta su moduli di prova (attributi standard, nomi italiani, select, React-like)
{
  const page = await ctx.newPage();
  await page.setContent(`<form>
    <input id="a" autocomplete="cc-name"><input id="b" autocomplete="cc-number" inputmode="numeric">
    <input id="c" autocomplete="cc-exp" placeholder="MM / YY"><input id="d" autocomplete="cc-csc" name="cvc">
    <input id="e" name="numeroCarta"><input id="f" name="intestatario">
    <select id="g" name="expMonth"><option value="">-</option><option value="07">07</option></select>
    <select id="h" name="expYear"><option value="">-</option><option value="2029">2029</option></select>
    <input id="i" name="exp_year" maxlength="2"><input id="j" name="email"><input id="k" name="cvv2">
    <input id="l" type="hidden" name="cardnumber"><input id="m" name="scadenza">
  </form><script>window.events=0;document.getElementById('b').addEventListener('input',()=>window.events++)</script>`);
  const n = await page.evaluate(inj('fillCard.js'));
  const v = await page.evaluate(() => Object.fromEntries([...document.querySelectorAll('input,select')].map(e => [e.id, e.value])));
  check('Carta: numero, intestatario e scadenza compilati', v.a === 'Mario Rossi' && v.b === '4242424242424242' && v.c === '07/29' && v.e === '4242424242424242' && v.f === 'Mario Rossi' && v.m === '07/29', `${n} campi`);
  check('Carta: select mese/anno e anno a 2 cifre', v.g === '07' && v.h === '2029' && v.i === '29');
  check('Carta: CVV, email e campi nascosti non toccati', v.d === '' && v.k === '' && v.j === '' && v.l === '');
  check('Carta: gli eventi input vengono emessi (compatibile con React/Vue)', await page.evaluate(() => window.events) === 1);
  await page.close();
}

await browser.close();
console.log(failed ? `\n${failed} controlli falliti` : '\nTutti i controlli web superati');
process.exit(failed ? 1 : 0);
