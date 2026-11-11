# Spike fase 0 – risposte ai 6 punti

Data: 2026-11-11. Versioni: T3 Code 0.1.9, IntelliJ Platform branch `262`, WebStorm 2026.3 (263.7307.202) installato in locale.

Metodo:
- Lato T3 (punti 1, 2, 3): test su un server T3 isolato, avviato con `t3 serve --base-dir <scratch> --port 3899`.
  Il server T3 dell'app desktop (porta 3773) non è cambiato.
- Lato IDE (punti 1, 4, 5, 6): lettura del codice di `intellij-community` (master `190b4a6`, branch `262`) e dei jar di WebStorm 2026.3.
  Nessun test con un IDE avviato.

## Riepilogo

| # | Domanda | Risposta | Verifica |
|---|---|---|---|
| 1 | Il cookie di sessione resta dopo il riavvio dell'IDE? | Sì, con 4 eccezioni | Server: test. JCEF: codice. Riavvio reale: verificato con `runIde` (fase 2). |
| 2 | `t3 project add` è idempotente? Formato di `$projectKey`? | No. `$projectKey` è una chiave interna del client web. | Test e codice |
| 3 | Un secondo `t3 serve` sulla stessa home va in conflitto? | No. Il secondo server si ferma con exit 78. | Test su home isolata |
| 4 | Esc, Ctrl+K, Ctrl+Enter arrivano alla pagina JCEF? | No. L'IDE esegue prima le azioni del keymap. | Codice |
| 5 | La pagina riceve `prefers-color-scheme` dall'IDE? | No. JCEF non riceve il tema dell'IDE. | Codice |
| 6 | Remote Development (split mode) e Android Studio? | Parziale / sperimentale | Codice e forum |

## 1. Persistenza della sessione

Fatti:
- `POST /api/auth/browser-session` con `{"credential": "<token>"}` restituisce 200 e questo cookie:
  `t3_session_3899_<hash>=…; Path=/; Expires=<oggi + 30 giorni>; HttpOnly; SameSite=Lax`.
- Il token di pairing è monouso. Il secondo scambio con lo stesso token restituisce `401` con `reason: invalid_credential`.
- Il nome del cookie dipende dal modo del server: `t3_session_<port>` in modo `desktop`, `t3_session_<port>_<hash>` in modo `web` (`resolveSessionCookieName`).
- `t3 auth session list` mostra la sessione nuova: `method: browser-session-cookie`, `client: <label> | bot | 127.0.0.1`, scadenza a 30 giorni.
- JCEF salva cookie e localStorage in `<system>/jcef_cache/` (`JBCefAppCache.kt`). La proprietà `ide.browser.jcef.cache.path` cambia la cartella.
- Un `JBCefBrowser` di default usa il contesto globale su disco (request context `null` in `JBCefBrowserBase.java`).

JCEF cancella la cache in 4 casi:
1. Una nuova versione major di CEF.
2. "Invalidate Caches" con la casella JCEF selezionata. La casella è vuota di default.
3. L'azione interna "Restart JCEF with new cache".
4. Una cartella di cache bloccata in modo out-of-process (attivo di default). `cef_server` usa allora una cache temporanea.

Conseguenze per il piano:
- Il plugin crea un pairing solo se la sessione manca. Per controllare la sessione, la pagina chiama `GET /api/auth/session`.
- Il plugin non può leggere il cookie da JavaScript, perché il cookie è `HttpOnly`.

Verificato dopo lo spike (2026-11-11, `runIde` della fase 2, server dell'app desktop su 3773):
- Il plugin crea un pairing e la web UI lo scambia: 1 sessione `JetBrains IntelliJ IDEA – demo-project`.
- Dopo un riavvio dell'IDE, la UI si apre senza un nuovo pairing: la sessione resta 1.

## 2. `t3 project add` e `$projectKey`

Fatti:
- Prima chiamata: exit 0, `Added project <uuid> (proj-a) at <path>.`
- Seconda chiamata sullo stesso path: exit 1, `ProjectAlreadyExistsError: An active project already exists for '<path>'.` T3 non crea un duplicato.
- `$projectKey` è una chiave di gruppo che il client web calcola (gruppi di progetti per ambiente e checkout). Non è l'id del progetto.
- `t3 thread list --project <path> --json` accetta un path e restituisce un array JSON. Un progetto senza thread restituisce `[]`.
- La route `/$environmentId/$threadId` usa id stabili. Il file `userdata/environment-id` contiene `environmentId`.

Conseguenze per il piano:
- `T3ProjectBinder` tratta `ProjectAlreadyExistsError` come successo.
- `T3ProjectBinder` apre il thread più recente del progetto (`/$environmentId/$threadId`). Se il progetto non ha thread, apre `/`.

## 3. Due server sulla stessa home

Fatti:
- Il secondo `t3 serve` sulla stessa home termina con exit 78 e questo messaggio:
  `ServerAlreadyRunningError: A T3 Code server already owns <home>/userdata. … No server was stopped.`
- Il primo server risponde ancora (HTTP 200). `server-runtime.json` non cambia.
- Il file `server-owner.sqlite` è il lock di proprietà.
- All'avvio, `t3 serve` stampa `Connection string`, `Token`, `Pairing URL` e un QR code.

Conseguenze per il piano:
- `T3ServerService` può avviare `t3 serve` senza rischio per un server attivo.
- Con exit 78, `T3ServerService` rilegge `server-runtime.json` e si collega al server esistente.

Not verified: il test con la home dell'app desktop (`~/.t3`). Il lock è lo stesso codice, ma il test usa una home isolata.

## 4. Tastiera nel tool window

Fatti:
- Il rendering off-screen (OSR) è il default (`ide.browser.jcef.osr.enabled=true`). Il modo out-of-process forza OSR.
- `IdeKeyEventDispatcher` confronta il tasto con il keymap prima di `JBCefOsrComponent`.
  Il dispatcher salta il keymap solo se il focus owner implementa `KeyboardAwareFocusOwner`. `JBCefOsrComponent` non lo implementa.
- Keymap di default:
  - Ctrl+Shift+A = `GotoAction`. L'azione è sempre attiva, quindi Chromium non riceve mai il tasto.
  - Ctrl+K = `CheckinProject`.
  - Esc = `EditorEscape`, `FocusEditor`.
  - Ctrl+Enter = `ViewSource`, `EditorSplitLine`, `Console.Execute.Multiline`. Il risultato dipende dal contesto.
- `KeyboardAwareContainer` (`@ApiStatus.Experimental`) permette di saltare il keymap. Questa API esiste in 263, ma non nel branch `262`.

Conseguenze per il piano:
- Su 262, le scorciatoie di T3 in conflitto con il keymap dell'IDE non arrivano alla pagina.
- Decisione (2026-11-11): since-build `262`. I tasti in conflitto sono un limite noto, descritto in `PLAN.md` (sezione "Limiti noti").

Not verified: un test manuale con l'IDE avviato; un workaround su 262.

## 5. Tema chiaro e scuro

Fatti:
- Il codice di `ui.jcef` e i jar locali non passano il tema dell'IDE a Chromium. Non contengono `--force-dark-mode` né un listener del Look and Feel.
- `-Dide.browser.jcef.extra.args=--force-dark-mode` vale per tutti i browser JCEF dell'IDE e non cambia dal vivo.
- La web UI di T3 ha 3 modi: Dark, Light e System. Il client salva il modo in localStorage (`t3code:theme-appearance-mode`).
- `t3 theme set` cambia il tema di tutto l'ambiente T3: anche l'app desktop cambia tema. Quindi non serve per un singolo IDE.

Conseguenze per il piano:
- La sincronizzazione del tema è obbligatoria. Il plugin usa un `LafManagerListener` e invia il tema alla pagina con JavaScript.

Not verified: il valore di default di `prefers-color-scheme` in JCEF su Linux; come il modo System legge il tema (codice minificato).

## 6. Remote Development e Android Studio

Fatti:
- Split mode: WebStorm 2026.3 contiene i moduli `intellij.platform.backend.split.jcef` e `intellij.platform.frontend.split.jcef`.
  Il browser appare nel frontend. Le richieste vanno al backend (`BackendDefaultCefResourceHandler` con `HttpURLConnection`).
- La pagina ufficiale sullo split mode non parla di JCEF.
- Android Studio: un post JetBrains del 2026-04-20 dichiara l'API JCEF "experimentally available" in Android Studio 2026.1 (Nightly 2026.1.4).

Conseguenze per il piano:
- L'MVP (fase 2) supporta solo un IDE locale. Remote Development e Android Studio restano fuori dalla fase 2.

Not verified: se `127.0.0.1:3773` punta al client o all'host in split mode; quale versione di Android Studio usa la piattaforma 262.

## Fonti

- [JBCefAppCache.kt](https://github.com/JetBrains/intellij-community/blob/190b4a60841dd871fa9c161c5f9a3b1129286541/platform/ui.jcef/jcef/JBCefAppCache.kt#L33-L34)
- [JBCefBrowserBase.java](https://github.com/JetBrains/intellij-community/blob/190b4a60841dd871fa9c161c5f9a3b1129286541/platform/ui.jcef/jcef/JBCefBrowserBase.java#L224)
- [JBCefApp.java (cancellazione cache)](https://github.com/JetBrains/intellij-community/blob/190b4a60841dd871fa9c161c5f9a3b1129286541/platform/ui.jcef/jcef/JBCefApp.java#L223-L250)
- [JCEF ServerStarter.java (cache temporanea)](https://github.com/JetBrains/jcef/blob/8efdfa12e8ed0c00146237a4100d113f5c82c63d/java/com/jetbrains/cef/remote/ServerStarter.java#L88-L96)
- [IdeKeyEventDispatcher.kt](https://github.com/JetBrains/intellij-community/blob/190b4a60841dd871fa9c161c5f9a3b1129286541/platform/platform-impl/src/com/intellij/openapi/keymap/impl/IdeKeyEventDispatcher.kt#L261-L274)
- [KeyboardAwareContainer.kt](https://github.com/JetBrains/intellij-community/blob/190b4a60841dd871fa9c161c5f9a3b1129286541/platform/platform-impl/src/com/intellij/ide/KeyboardAwareContainer.kt)
- [SettingsHelper.java (extra args)](https://github.com/JetBrains/intellij-community/blob/190b4a60841dd871fa9c161c5f9a3b1129286541/platform/ui.jcef/jcef/SettingsHelper.java#L191)
- [Split mode and Remote Development](https://plugins.jetbrains.com/docs/intellij/split-mode-and-remote-development.html)
- [JCEF in Android Studio (forum)](https://platform.jetbrains.com/t/experimental-jcef-web-browser-api-support-for-android-studio/4117)
