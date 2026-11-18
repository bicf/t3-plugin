# Piano: plugin JetBrains che ospita T3 Code

Il plugin mostra la web UI di T3 Code con browser JCEF: la sidebar di T3 nel tool window "T3 Code", ogni conversazione in una tab editor senza sidebar, il pannello destro di T3 nel tool window "T3 Workspace".
Dove l'IDE ha lo strumento, file, diff, terminale e Git di T3 usano l'IDE.
Il server T3 resta un processo esterno. Il plugin non riscrive la UI di T3.

## Decisioni

| Tema | Decisione |
|---|---|
| Versione minima | since-build `262` (IntelliJ Platform 2026.2+), Java 25, come il template ufficiale JetBrains. I tasti in conflitto con il keymap sono un limite noto (sezione "Limiti noti"). |
| Server T3 | Il plugin si collega a un server T3 attivo. Se il server manca, il plugin avvia `t3 serve --no-browser`. |
| Linguaggio e build | Kotlin, IntelliJ Platform Gradle Plugin 2.x, base `JetBrains/intellij-platform-plugin-template`. |
| Identità | ID `com.bicf.t3plugin` (anche package Kotlin e `group`), nome "T3 Code Host (Unofficial)". L'ID non cambia dopo la prima pubblicazione sul Marketplace. |
| Ambito dell'MVP | Solo IDE locale. Remote Development e Android Studio restano fuori dalla fase 2 (spike 6). |

## Fatti verificati

Fatti verificati su T3 Code 0.1.9, installato su questa macchina:
- Il server T3 ascolta su `127.0.0.1:3773`. Il file `~/.t3/userdata/server-runtime.json` contiene `origin`, `port` e `pid`.
- La CLI `~/.t3/bin/t3` ha i comandi `serve`, `project add <path>`, `thread start`, `thread send` e `auth pairing create`.
- `t3 auth pairing create --json --ttl … --label … --base-url …` crea un token monouso e stampa un link `/pair#token=...`.
- La web UI ha le route `/pair`, `/projects/$projectKey` e `/$environmentId/$threadId`.
- `/readyz` restituisce l'HTML della pagina web. Quindi non serve come health check HTTP.
- T3 contiene già gli editor JetBrains (`idea`, `webstorm`, `pycharm` e altri), con `launchStyle: "line-column"`.
  "Open in editor" apre il file nell'IDE tramite il launcher CLI dell'IDE. Questa funzione non richiede un bridge.

Fatti dalla documentazione JetBrains:
- Dalla 2026.2, un plugin che usa JCEF deve dichiarare `<depends>com.intellij.modules.jcef</depends>`.
- Il template ufficiale ha come target la 2026.2, con since-build `262` e Java 25.
- La 2026.3 toglie OkHttp dal bundle. Il plugin usa `java.net.http.HttpClient`.
- La 2026.3 richiede una dipendenza esplicita per JNA. Il plugin non usa JNA.
- La prima pubblicazione sul Marketplace è manuale. Le versioni successive usano `publishPlugin` con la firma `signPlugin`.

## Architettura

```
IDE JetBrains
 ├─ Tool window "T3 Code"
 │   └─ JBCefBrowser ──HTTP/WebSocket──► server T3 (127.0.0.1:3773)
 └─ T3ServerService ──legge──► ~/.t3/userdata/server-runtime.json
                    ──esegue──► t3 serve | t3 auth pairing create | t3 project add
```

| Componente | Livello | Compito |
|---|---|---|
| `T3ToolWindowFactory` | progetto | Crea il tool window. Mostra `JBCefBrowser` oppure il fallback. |
| `T3ServerService` | applicazione | Trova il server (`server-runtime.json`, `T3CODE_HOME`). Controlla `pid` e porta. Se il server manca, avvia `t3 serve --no-browser`. Con exit 78 (`ServerAlreadyRunningError`), rilegge `server-runtime.json`. Scarta l'output di `t3 serve`, perché contiene un token. Alla chiusura dell'IDE, ferma solo il server che ha avviato. |
| `T3PairingService` | applicazione | Esegue `t3 auth pairing create --json` e restituisce il link `/pair#token=...`. Accetta solo un link sull'origin del server. Non scrive mai il link o lo stdout nei messaggi di errore. |
| `T3ProjectBinder` | progetto | Esegue `t3 project add <basePath>`. Tratta `ProjectAlreadyExistsError` (exit 1) come successo. Apre il thread più recente (`/$environmentId/$threadId`), oppure `/`. |
| `T3ThemeSync` + `T3IdePalette` | progetto | Crea in localStorage il tema T3 `jetbrains-ide` (`t3code:themes:v1`) con i colori dell'IDE, lo seleziona (`t3code:theme`, `t3code:theme-appearance-mode`), rimuove `t3code:theme-halves:v1` e invia un evento `storage` per chiave: la pagina cambia colori senza ricaricarsi. Gira a ogni caricamento, a ogni `LafManagerListener.lookAndFeelChanged` e a ogni cambio di schema colori dell'editor. Lo sfondo dell'editor va su composer, codice e terminale solo se editor e IDE sono entrambi chiari o entrambi scuri. |
| `T3ClipboardBridge` | browser | Copia: `navigator.clipboard.writeText`, `write` (parte `text/plain`), Ctrl+C e Taglia vanno al plugin, che scrive negli appunti dell'IDE (`CopyPasteManager`). Paste: lo script annulla l'evento `paste` nativo, chiede al plugin gli appunti dell'IDE (testo o immagine PNG) e invia un nuovo evento `paste` allo stesso elemento. Su Linux (`CopyPasteManager.isSystemSelectionSupported`), una selezione nella pagina va nella selezione primaria dell'IDE dopo 200 ms, e il paste col tasto centrale legge la selezione primaria dell'IDE (solo testo). Su Windows e macOS il paste col tasto centrale resta nativo. Motivo: su Wayland l'IDE scrive negli appunti Wayland, ma Chromium gira su XWayland e legge gli appunti X11, che il desktop aggiorna solo per una finestra X11 con il focus. Lo stesso vale per la selezione primaria. |
| `T3MessageActions` | browser | Pulsanti "Disable line wrap" / "Wrap lines" e "Edit in composer" alla fine del gruppo di pulsanti di T3 che contiene "Copy message", nella riga azioni sotto il box di ogni messaggio utente (`data-message-role="user"`): orario, "Edit from here", "Copy message", "Copy prompt link". La riga si vede con il mouse sul messaggio o con il focus. I pulsanti prendono le classi del "Copy message" di T3. Il box non ha pulsanti del plugin. Il wrap spento mostra ogni riga su una riga, con scorrimento orizzontale; lo stato vale per messaggio fino al ricaricamento. "Edit in composer" mette nel composer (TipTap, `[data-composer-rich-text="true"]`, proprietà `editor`) il testo originale del messaggio (prop `text` del componente React del "Copy message", 9 livelli sopra il pulsante; la ricerca si ferma al primo elemento DOM sopra il pulsante), con un `hardBreak` per ogni a capo, dopo la bozza e una riga vuota; non invia niente. "Edit from here" di T3 è diverso: riporta la conversazione a quel turno. |
| `T3VisitGate` | browser | Tab editor e "T3 Workspace". Ogni pagina con un thread montato invia `thread.visit` (`orchestration.dispatchCommand`) dopo un aggiornamento del thread: subito quando una run finisce, altrimenti entro 10 s; il server toglie lo stato non letto ("Done", "Failed") dalla sidebar. Lo script entra a `onLoadStart`. Mentre la pagina non è sullo schermo, tiene l'ultima richiesta di ogni thread e risponde alla precedente con un `Exit` di successo (`sequence` 0), mai fra 2 parti di un messaggio. Mentre la pagina è nascosta, un salvataggio di `t3code:ui-state:v1` mantiene i tempi di visita salvati. Quando la tab diventa visibile (`HierarchyEvent.SHOWING_CHANGED`), invia le richieste senza modifiche e salva i tempi locali trattenuti. La pagina di "T3 Workspace" non invia mai la visita. `window.__t3VisitLog` tiene gli ultimi 20 eventi. |
| `T3SidebarMark` | browser | Tool window con tab editor. La riga `li[data-thread-item="<environmentId>:<threadId>"]` del thread dell'ultima tab T3 selezionata (ancora aperta) ha lo sfondo `--sidebar-row-active` e una barra di 2 px a sinistra. Il confronto usa la chiave completa. Una tab non T3 lascia il segno; la chiusura della tab segnata lo toglie. |
| `T3SidebarResize` | browser | Solo senza tab editor. Sotto i 768 px T3 mostra la sidebar come pannello sovrapposto largo quasi tutta la pagina, senza resize. Lo script aggiunge un bordo trascinabile di 6 px (minimo 200 px), salva la larghezza in `t3plugin:mobile-sidebar-width` e la ripristina con un doppio clic. |
| Focus di Chromium (pannello) | browser | A ogni pressione del mouse nella pagina, e a ogni caricamento con il browser già a fuoco, il pannello chiama `CefBrowser.setFocus(true)`. Motivo: `JBCefBrowser` dà il focus a Chromium solo a un cambio di focus Swing; senza focus Chromium non mostra il cursore di testo. |
| `T3LinkHandler` (`T3LinkPolicy` + handler del pannello) | browser | Apre nel browser di sistema gli URL esterni all'origin T3. Navigazioni: `onBeforeBrowse`. Popup e link `target="_blank"`: un ponte JavaScript nella pagina, perché con JCEF out-of-process `onBeforePopup` non arriva al plugin. |
| `T3PageTabs` + `T3PageEditorProvider` | progetto | Ogni pagina T3 è una tab editor (`T3PageFile`, file solo in memoria) con un proprio `T3BrowserPanel`: thread, bozza (`/draft/<id>`) o pagina app (`settings`, `projects`, `pull-requests`, `usage`). Una tab per chiave (`T3Routes.tabPage`); il titolo della tab è il titolo del thread nella pagina. Una bozza diventa la tab del suo thread al primo messaggio. La tab dei settings si chiude quando l'utente chiude i settings. L'IDE non riapre le tab dopo un riavvio. |
| `T3Layout` | browser | Layout con tab editor (sezione "Layout nell'IDE"). Lo script entra a `onLoadStart` e di nuovo dopo il caricamento. |
| `T3WorkspacePanels` + `T3WorkspaceToolWindowFactory` | progetto | Tool window "T3 Workspace" (a destra): un pannello per tab, creato solo su richiesta, con il pannello destro di T3 della pagina della tab. Il pannello segue la tab: selezione, titolo, nuova pagina, chiusura. |
| `T3RouteWatch` | browser | Ogni 250 ms legge `location.pathname` e il titolo del thread (`Thread actions for <titolo>`). Tool window: una pagina scelta si apre in una tab, e il tool window torna a `/ide-navigator` (`history.pushState` + `popstate`). Tab: un'altra pagina si apre nella sua tab, e la tab torna alla sua pagina (`history.back()`). Il router scrive `mcp:…` come `mcp%3A…`. Il messaggio porta l'indice di cronologia del router (`__TSR_index`): con indice 0 T3 ha sostituito la pagina senza clic (redirect), e la tab segue T3 senza ricaricare la sua pagina. Una bozza va al plugin solo quando T3 l'ha salvata in `localStorage` (`t3code:composer-drafts:v1`, scritta 300 ms dopo la modifica), al massimo dopo 1,5 s. |
| `T3IdeBridge` + `T3IdeTools` | browser + progetto | Strumenti T3 → strumenti IDE (dettagli nella sezione "Strumenti IDE"). Lo script entra a `onLoadStart`, prima degli script della pagina. |
| `T3Settings` | applicazione | Settings → Tools → T3 Code: percorso della CLI, URL di un server remoto, avvio automatico del server, conversazioni in tab editor, strumenti IDE. |
| `T3AskAboutSelectionAction` | editor | "Ask T3 About Selection…": `t3 thread send` al thread aperto, oppure `t3 thread start` nel progetto. |
| `T3StatusWidgetFactory` | progetto | Widget della status bar: `T3: running`, `stopped`, `error`, `remote`. Un controllo ogni 10 s. |

Flusso all'apertura del tool window:
1. Il plugin chiama `JBCefApp.isSupported()`. Se il risultato è `false`, il plugin mostra il pulsante "Apri nel browser".
2. `T3ServerService` legge `server-runtime.json` e controlla il processo e la porta.
3. Se il server non risponde, `T3ServerService` avvia `t3 serve --no-browser` e aspetta un nuovo `server-runtime.json`.
4. `JBCefBrowser` carica l'origin T3, e la pagina chiama `GET /api/auth/session`. Se la sessione è valida, il flusso va al passo 7.
5. `T3PairingService` revoca le sessioni con la stessa label, poi crea un token con TTL di 2 minuti e label `JetBrains <IDE> (<hash>)`.
   L'hash viene dalla cartella system dell'IDE: tutti i progetti di un IDE usano lo stesso archivio di cookie JCEF.
6. `JBCefBrowser` carica il link `/pair#token=...`. La web UI scambia il token con un cookie di sessione valido 30 giorni.
7. `T3ProjectBinder` registra la root del progetto. Con le tab editor, il tool window apre `/ide-navigator` e il thread più recente del progetto in una tab (se nessuna tab T3 è aperta). Senza tab editor, il tool window apre il thread più recente, oppure `/`.

## Strumenti IDE

Fatti verificati su T3 0.1.9 (DevTools sulla web UI):
- Il client parla con il server in Effect RPC su `/ws`, in JSON senza framing. Richiesta: `{"_tag":"Request","id":<numero>,"tag":"…","payload":{…},"headers":[]}`. Risposta: `{"_tag":"Exit","requestId":<id>,"exit":{"_tag":"Success","value":…}}`.
- Con `frameParts=1`, un frame che inizia con U+001E è una parte di un messaggio lungo; il frame senza U+001E chiude il messaggio.
- Il client tiene la funzione `send` del socket alla creazione: un patch di `WebSocket.prototype.send` dopo il caricamento non vede nessun messaggio.
- Una risposta `Exit/Success` sintetica a `shell.openInEditor` chiude la richiesta senza errori nella UI.
- Il client ignora un `Exit` con un `requestId` che non conosce.

| Strumento T3 | Intercettazione | Strumento IDE | Senza lo strumento IDE |
|---|---|---|---|
| Link a file nella chat (`data-markdown-copy`, `file:`), con `:riga[:colonna]` o `#L…` | clic | Editor, alla riga | T3 apre il file nel pannello "Files" |
| "Open in …" (`shell.openInEditor`, non `file-manager`) | RPC; risposta `Exit/Success` sintetica | Editor (file) o Project view (cartella del progetto) | la richiesta va al server |
| "Open diff" e righe file del riquadro "changed files" | clic; turno da `turnSummary.checkpointTurnCount` nelle props React | `orchestration.getTurnDiff` (turno n-1 → n) sul socket di T3, con id da 1 000 000 001; diff viewer (`PatchDiffRequest`) | T3 apre il pannello "Diff" |
| "Toggle terminal drawer" (chiuso), superficie "Terminal" | clic | Terminale IDE nella cartella del thread (`cwd` dell'ultimo `subscribeVcsStatus`) | terminale T3 |
| Superficie "Files" | clic | Project view sulla cartella del thread | pannello "Files" di T3 |
| Superficie "Diff" | clic | `GitBrancher.showDiffWithLocal(HEAD)` | pannello "Diff" di T3 |
| Superfici "Browser", "Pull request", "Linked pull requests", "Device", "Side chat" | — | restano a T3, nel tool window "T3 Workspace" | — |
| Selettore di branch (regione "Version Control") | `pointerdown` | Popup "Branches" di Git | lista di T3 |
| "Changes vs <ref>" / "Changes Uncommitted" | clic | `GitBrancher.showDiffWithLocal(<ref> o HEAD)` | pannello di T3 |
| Pulsante che inizia con "Commit" | clic | Azione "Commit…" (`CheckinProject`) | dialog di T3 |

Una superficie si apre dal menu "Add panel surface" o dalla lista "Open a surface" del pannello vuoto: il bridge intercetta entrambi.

Regole:
- Terminale e Git solo con i plugin Terminal e Git dell'IDE (`<depends optional>`), e Git solo per un repository registrato nell'IDE.
- Server remoto: solo il diff usa l'IDE; file, terminale e Git restano a T3.
- Un'azione dell'IDE con il componente della pagina non visibile (tab in secondo piano) usa il frame dell'IDE come contesto.
- `window.__t3IdeLog` tiene gli ultimi 50 eventi del bridge (tag ed esiti, mai contenuti).
- Settings → "Use the IDE tools in T3" spegne tutto il bridge.

## Layout nell'IDE

Con "Open conversations in editor tabs" (`T3Layout`). Fatti verificati su T3 0.1.9 (DevTools e sandbox IDE):
- La sidebar è `[data-slot=sidebar]` dentro `[data-slot=sidebar-wrapper]`; la conversazione è `[data-slot=sidebar-inset]`.
- Il pannello destro ha l'etichetta "Workspace panel" senza superficie e "Workspace panel: <superficie>" con una superficie attiva (selettore `[aria-label^="Workspace panel"]`). "Toggle right panel" lo apre e lo chiude (`aria-pressed`).
- T3 salva lo stato del pannello per thread in `localStorage` (`t3code:right-panel-state:v2`: `isOpen`, `activeSurfaceId`, `surfaces`, `maximized`). Una pagina legge lo stato solo al caricamento.
- Sotto i 768 px (`matchMedia("(max-width: 767px)")`) la sidebar è uno sheet che si chiude; sotto i 981 px (`(max-width: 980px)`) il pannello destro è uno sheet.
- Su un route sconosciuto, come `/ide-navigator`, T3 mostra la sidebar e una pagina "Not found". "New thread" con il progetto scelto porta a `/draft/<id>` del progetto (una bozza per progetto).
- Settings è un dialog sopra la pagina (`/settings/<sezione>`); "Close" lascia `/settings`.

| Dove | Cosa mostra | Come |
|---|---|---|
| Tool window "T3 Code" (a sinistra) | Solo la sidebar di T3, a tutta larghezza | Route `/ide-navigator`; `matchMedia` risponde `false` alle 2 query; CSS nasconde la conversazione e la pagina "Not found" |
| Tab editor | Pagina senza sidebar e senza pannello destro | CSS; "Toggle right panel" → tool window "T3 Workspace" |
| Tool window "T3 Workspace" (a destra) | Solo il pannello destro del thread della tab, a tutta larghezza | `matchMedia` come sopra; CSS; lo script apre il pannello se è chiuso |

Regole:
- Un clic su "Toggle right panel" nella tab mostra o nasconde il pannello della tab in "T3 Workspace". La pagina della tab non apre il suo pannello.
- Se T3 apre il pannello nella tab dopo il caricamento (scorciatoia, agente), la tab lo chiude e "T3 Workspace" mostra il pannello, ricaricato per leggere lo stato nuovo. Un pannello aperto al caricamento si chiude senza messaggio.
- "T3 Workspace" aperto senza pannelli mostra il pannello della tab T3 selezionata.
- Compromesso: le pagine di tab e pannello scrivono lo stato completo del pannello in `localStorage`. Se la tab apre una superficie, il pannello ricaricato perde una superficie aggiunta solo nel pannello dopo l'ultima scrittura della tab (not verified a runtime).
- Senza tab editor il tool window mostra la UI completa, come nella versione 0.0.3.

## Limiti noti

### Tasti intercettati dall'IDE (since-build 262)

Su 262, `IdeKeyEventDispatcher` esegue le azioni del keymap prima della pagina JCEF (spike 4).
Questi tasti di T3 non arrivano alla pagina, se l'azione dell'IDE è attiva nel contesto del tool window:

| Tasto | Azione dell'IDE nel keymap di default |
|---|---|
| Ctrl+Shift+A | `GotoAction`. L'azione è sempre attiva, quindi T3 non riceve mai il tasto. |
| Ctrl+K | `CheckinProject` |
| Esc | `EditorEscape`, `FocusEditor` |
| Ctrl+Enter | `ViewSource`, `EditorSplitLine`, `Console.Execute.Multiline`. Il risultato dipende dal contesto. |

Regole:
- Il plugin non cambia il keymap dell'utente.
- Workaround per l'utente: togliere il tasto dall'azione dell'IDE in Settings → Keymap. Effetto sulla pagina: not verified.
- Quando la versione minima passa a `263`, il tool window usa `KeyboardAwareContainer` (API sperimentale) e questo limite sparisce.

### JCEF sospeso da AppArmor (Ubuntu 24.04+ e derivate)

Con `kernel.apparmor_restrict_unprivileged_userns = 1`, la piattaforma sospende ogni browser JCEF senza un profilo AppArmor.
Il tool window mostra allora "Embedded Browser is suspended" con il pulsante "Enable Browser…", che offre di installare il profilo o di disattivare il sandbox.
Il plugin non può risolvere questo limite: dipende dal sistema. Il `runIde` della sandbox lo mostra sempre, perché l'IDE gira dalla cache di Gradle.
Per i test, la sandbox usa `ide.browser.jcef.sandbox.enable=false`.

### "Ask T3 About Selection…" richiede un modello di default in T3

`t3 thread start` non ha un modello se i settings T3 non hanno `defaultModelSelection`.
In questo caso l'azione mostra la notifica "T3 did not receive the selection. … Pass modelSelection: the project has no default model."
L'utente sceglie un modello di default nelle impostazioni di T3. Il plugin non sceglie un modello al posto dell'utente.

### Avvisi del Plugin Verifier

`./gradlew verifyPlugin` dà "Compatible" e 0 errori su IU-262.12573.28 e IU-263.7307.200 (2026-11-14). Il plugin mantiene questi avvisi:

| Avviso | 262 | 263 | Causa |
|---|---|---|---|
| API deprecate | 11 | 12 | Kotlin genera metodi bridge per `isApplicable` e `isDoNotActivateOnStart` di `ToolWindowFactory` (in `T3ToolWindowFactory` e `T3WorkspaceToolWindowFactory`), e per `getPresentation(PlatformType)` di `StatusBarWidget`. Su 263 si aggiunge il costruttore `DynamicBundle(String)` in `T3Bundle`. Su entrambe: `TerminalToolWindowManager.createShellWidget` in `T3TerminalSupportImpl` (l'API nuova, `TerminalToolWindowTabsManager`, è sperimentale e sta in un modulo di contenuto del plugin Terminal). |
| API sperimentali | 12 | 12 | Kotlin genera metodi bridge in `T3ToolWindowFactory` e `T3WorkspaceToolWindowFactory` per `getAnchor`, `getIcon` e `manage` di `ToolWindowFactory`. |

Il plugin non usa API "scheduled for removal". La ricerca della CLI in `PATH` non usa `PathEnvironmentVariableUtil.findInPath`, deprecato in 263.

Regole:
- Il codice sorgente non sovrascrive e non chiama questi metodi. Gli avvisi vengono solo dai metodi bridge e dal costruttore del bundle.
- Gli avvisi non bloccano `verifyPlugin` né la CI. L'effetto sulla revisione del Marketplace è not verified.
- Correzioni possibili, non applicate: l'opzione Kotlin `-jvm-default=no-compatibility` per i metodi bridge (effetto: not verified) e `DynamicBundle(T3Bundle::class.java, BUNDLE)` per il bundle.

## Fasi

### Fase 0 – Spike

Questi 6 punti erano "not verified". Le risposte sono in [`docs/spike-phase-0.md`](docs/spike-phase-0.md).
1. Il cookie di sessione resta nella cache JCEF dopo il riavvio dell'IDE?
2. `t3 project add` è idempotente? Qual è il formato di `$projectKey`?
3. Un secondo `t3 serve` sullo stesso `T3CODE_HOME` va in conflitto con l'app desktop?
4. I tasti Esc, Ctrl+K e Ctrl+Enter arrivano alla pagina JCEF, oppure l'IDE li intercetta?
5. La web UI segue `prefers-color-scheme` dentro JCEF?
6. Il tool window funziona in Remote Development (split mode) e in Android Studio?

Criterio di uscita: una nota con risposta sì o no per ogni punto.

### Fase 1 – Scaffold

Il progetto parte dal template ufficiale, con Kotlin e IntelliJ Platform Gradle Plugin 2.x.
`plugin.xml` dichiara `com.intellij.modules.platform` e `com.intellij.modules.jcef`.
Criterio di uscita: `./gradlew buildPlugin` crea lo ZIP e `./gradlew runIde` apre un tool window vuoto.

Stato (2026-11-11):
- `./gradlew check buildPlugin` è verde (1 test). Lo ZIP dichiara `since-build="262"` e `com.intellij.modules.jcef`.
- `./gradlew verifyPlugin`: "Compatible" con IU-262.11879.100 e IU-263.7307.200, 0 errori.
  Avvisi: 4 (262) e 5 (263) usi di API deprecate, 6 usi di API sperimentali.
  Cause: i metodi bridge che Kotlin genera in `T3ToolWindowFactory` per i metodi default di `ToolWindowFactory`, e il costruttore `DynamicBundle(String)` in `T3Bundle` (deprecato in 263).
- `./gradlew runIde`: il tool window `T3 Code` si apre (verificato nella fase 2).

### Fase 2 – MVP locale

Il plugin implementa i componenti della tabella e il fallback senza JCEF.
Criterio di uscita: IntelliJ IDEA e PyCharm su Linux mostrano T3 sul progetto aperto, senza login manuale.

Stato (2026-11-11):
- `T3ServerService` e `T3PairingService` sono pronti, con 32 test unitari.
- Il tool window `T3 Code` esegue i passi 1–6 del flusso: `T3BrowserPanel` con JCEF, `T3FallbackPanel` senza JCEF, azione "Reconnect to T3".
- Fatti JCEF verificati con `runIde`:
  - Il browser resta nascosto durante il controllo della sessione. Serve `setCreateImmediately(true)`, altrimenti CEF non crea il browser nativo e la pagina non si carica.
  - Un `JBCefJSQuery` creato dopo il browser nativo richiede `JBCefClient.Properties.JS_QUERY_POOL_SIZE` sul client.
- Ogni comando `t3` riceve la home in `T3CODE_HOME` (prima: `--base-dir`): così nessuna opzione segue un argomento posizionale.
- `T3CliIntegrationTest` usa la CLI reale su una home isolata e una porta libera: avvia `t3 serve`, esegue 2 volte `T3ProjectBinder.bind`,
  crea un pairing, scambia il token come la pagina `/pair`, revoca la sessione per label e ferma il server.
  Esito: verde in 8,9 s. Comando: `T3_CLI_IT=1 ./gradlew test --tests com.bicf.t3plugin.T3CliIntegrationTest`.
- `runIde` (IU 2026.2.3, Linux, KDE Wayland) con il server dell'app desktop su 3773 (2026-11-11):
  1. Primo avvio: il plugin crea un pairing e la pagina T3 si carica (DevTools). `t3 auth session list` mostra 1 sessione nuova.
  2. Secondo avvio: la pagina si carica senza un nuovo pairing. Il numero di sessioni resta 1.
  3. Correzione (2026-11-12): in queste prove il tool window mostrava l'avviso di AppArmor "Embedded Browser is suspended" sopra la pagina (sezione "Limiti noti").
- Stato (2026-11-12), con i passi 1–7, `T3LinkHandler`, `T3ThemeSync` e `T3Settings`. Prove su una home T3 isolata (porta 3898), con il sandbox JCEF spento nella sandbox IDE:

  | Prova | IDE | Risultato |
  |---|---|---|
  | Flusso completo | IU 2026.2.3 | Pairing (label `JetBrains IntelliJ IDEA (61bdad)`), progetto registrato, UI "demo-project / New thread" visibile (screenshot DevTools). Widget: `T3: running`. |
  | Flusso completo | PyCharm 2026.2.4 | UI "demo-project / New thread", progetto già presente (`ProjectAlreadyExistsError` come successo), sessione `JetBrains PyCharm (e0fe4a)`. |
  | Revoca | PyCharm 2026.2.4 | Dopo la cancellazione dei cookie, nuovo pairing: la vecchia sessione PyCharm è revocata, la sessione IntelliJ resta. |
  | Tema | IU 2026.2.3 | localStorage `dark` su IDE scuro. Lo script con `light` cambia la pagina senza ricarica. |
  | Popup | IU 2026.2.3 | `window.open` sulla stessa origin si carica nel pannello; `about:blank` è bloccato. |
  | Server fermo, avvio automatico spento | IU 2026.2.3 | "T3 Code is not available. No T3 server answers, and automatic start is off…" con "Retry". Widget: `T3: error`. |
  | JCEF spento | IU 2026.2.3 | `T3FallbackPanel` con "Open T3 Code in Browser". |

- Verifica delle azioni dell'utente (2026-11-12), IU 2026.2.3, home T3 di prova sulla porta 3898.
  Metodo: nessun clic manuale possibile. Un clic vero di DevTools (`Input.dispatchMouseEvent`) e uno script Groovy della sandbox sostituiscono l'utente.
  Il "browser di sistema" della sandbox è uno script che scrive solo l'URL.

  | Prova | Risultato |
  |---|---|
  | Clic su "View policy" in Settings → About di T3 (`https://t3.codes/privacy-policy`, `target="_blank"`) | L'URL va al browser di sistema. Il pannello resta su `/settings/about`, nessuna finestra JCEF nuova. |
  | Navigazione della pagina verso `https://example.com/…` | L'URL va al browser di sistema (`onBeforeBrowse`). Il pannello resta su T3. |
  | Tema dell'IDE: "IntelliJ Light", poi "Dark" (`LafManager`) | La pagina passa a `light`, poi a `dark`, senza ricaricarsi. |
  | "Ask T3 About Selection…" con il pannello su un thread | `t3 thread send` allo stesso thread: 2 messaggi utente, nessun thread nuovo. |
  | "Ask T3 About Selection…" con il pannello sulla bozza | `t3 thread start`: thread nuovo con domanda, `README.md:1` e codice; risposta "pong"; il pannello apre il thread. |

  Problemi trovati e risolti:
  1. Un thread avviato dalla CLI ha un id come `mcp:1c760312-…`. `T3Routes` non accettava `:`, quindi il pannello e il binder non aprivano questi thread.
     Ora un segmento accetta `[A-Za-z0-9_.:-]`, ma non `.` e `..`.
  2. Su una home T3 nuova, `t3 thread start` esce con "Pass modelSelection: the project has no default model.", e l'azione mostra questo errore in una notifica.
     Il server usa `defaultModelSelection` dei settings T3 (la home dell'app desktop ce l'ha). Per la prova, la home di prova ha `claudeAgent` / `claude-haiku-5-5`.

- Correzioni del 2026-11-12 (colori, appunti, cursore), IU 2026.2.3 su Wayland (`WLToolkit`), home T3 di prova sulla porta 3898.
  Metodo: DevTools sulla porta 9223 e uno script Groovy della sandbox, che invia eventi del mouse Swing al componente JCEF.

  | Prova | Risultato |
  |---|---|
  | Tema "Dark" (Islands) | `data-theme-id="jetbrains-ide"`, sfondo `#2b2d30`, testo `#dfe1e5`, accento `#3574f0` (prima: nero T3 `#0a0a0a`). |
  | Tema "IntelliJ Light" con schema editor scuro (sandbox) | Sfondo `#ffffff`, composer `#f2f2f2`, testo `#000000`; codice e terminale con i colori T3 chiari. |
  | `navigator.clipboard.writeText` e `write` (`text/plain` + `text/html`) | Promise risolta; i 2 testi compaiono negli appunti di sistema (cronologia Klipper). Il metodo nativo JCEF: "Document is not focused", oppure nessuna risposta in 10 s. |
  | Clic Swing sul composer, Chromium senza focus | Con il listener del pannello: `document.hasFocus()` vero e cursore visibile (screenshot). Senza il listener: falso. |
  | Pairing completato senza ricaricare la pagina | Ora il pannello installa anche i ponte link e appunti (prima applicava solo il tema). |

- Correzioni della versione 0.0.3 (2026-11-12), stessa sandbox e stesso metodo, con eventi da tastiera DevTools (`Ctrl+V` e `Ctrl+C` con i comandi `paste` e `copy`).

  | Prova | Risultato |
  |---|---|
  | Appunti IDE `t3-paste-from-ide-1`, appunti X11 con un altro testo, `Ctrl+V` nel composer | Il composer riceve `t3-paste-from-ide-1` (prima: il testo X11). |
  | Testo su 2 righe, poi `navigator.clipboard.readText()` | 2 righe nel composer; `readText` restituisce `riga A\nriga B`. |
  | Immagine 40×30 negli appunti IDE, `Ctrl+V` nel composer | Il composer mostra un allegato immagine 40×30. |
  | Selezione nella pagina, `Ctrl+C` | Gli appunti IDE contengono il testo selezionato. |
  | Sidebar sovrapposta (pagina di 461 px): trascinamento del bordo da 449 a 280 px | Larghezza 280 px, salvata; uguale dopo chiusura e riapertura. Doppio clic: di nuovo 449 px. |

- Not verified a runtime: il pulsante del fallback (aprirebbe il browser), il server remoto con un link incollato,
  il clic di un utente reale con la finestra dell'IDE attiva (Wayland non dà il focus alla sandbox avviata da script).
- Criterio di uscita della fase 2: raggiunto su Linux per IntelliJ IDEA e PyCharm, con la home T3 isolata.

### Fase 3 – Integrazione con l'IDE

- Azione editor "Ask T3 about selection": esegue `t3 thread start` e `t3 thread send` con `file:line` e il testo selezionato.
- Widget nella status bar con lo stato del server: attivo, fermo, errore.
- Server remoto: l'utente incolla un link `/pair#token=...`, creato con `t3 pair` sull'host remoto.
- Rifinitura di `T3ThemeSync`: il tema cambia dal vivo, senza ricaricare la pagina.

Stato (2026-11-12): l'azione editor, il widget, il server remoto e il tema dal vivo sono implementati, con test unitari.
L'azione e il server remoto sono not verified a runtime.

Stato (2026-11-13): conversazioni in tab editor e strumenti IDE (sezioni "Architettura" e "Strumenti IDE").
`./gradlew test`: 86 test, 0 errori, 1 saltato. `verifyPlugin`: "Compatible" su IU-262.12573.28 e IU-263.7307.200.
Prove con `runIde` (IU 2026.2.3), home T3 isolata sulla porta 3898, progetto demo con Git, clic veri di DevTools (porta 9223) e controlli con uno script Groovy della sandbox:

| Prova | Risultato |
|---|---|
| Apertura del tool window | Tool window su `/draft/…`; il thread più recente in una tab con il titolo del thread. |
| Clic su un thread nella lista del tool window | Tab nuova selezionata; il tool window torna su `/draft/…`. |
| Clic su un altro thread nella sidebar di una tab | Selezionata la tab esistente (sempre 2 tab); la prima tab torna al suo thread. |
| Link `Main.kt · L2` nella chat | `Main.kt` nell'editor, cursore alla riga 2; nessun pannello "Files" di T3. |
| "Open in VS Code" | Project view sulla cartella `demo`; VS Code non parte; nessun errore in T3. |
| "Open diff" del turno 1, riga `Main.kt +1 -1` | Tab "Diff" dell'IDE: `PatchDiffRequest` di `Main.kt`, "Before turn 1 / After turn 1"; nessun pannello "Diff" di T3. |
| "Open diff" del turno 2 | 5 file `.idea/…` nel diff dell'IDE, "Before turn 2 / After turn 2". |
| "Toggle terminal drawer", superficie "Terminal" | Tab "T3: demo" nel terminale dell'IDE; il drawer di T3 resta chiuso. |
| Superficie "Files" | Project view visibile, cartella `demo` selezionata. |
| Selettore di branch | Popup `GitBranchesPopupBase` dell'IDE; nessuna lista di T3. |
| "Changes Uncommitted" | Tab "Changes Between HEAD and Current Working Tree" nel tool window VCS. |
| "Commit" | Tool window "Commit" visibile. |

Problemi trovati e risolti durante le prove:
1. Il patch di `WebSocket.prototype.send` dopo il caricamento non vedeva messaggi: ora lo script entra a `onLoadStart`.
2. Il diff via la risposta di T3 falliva quando T3 aveva il diff in cache: ora il bridge chiede il diff da solo.
3. Il selettore di branch si apre su `pointerdown`: ora il bridge intercetta `pointerdown`.
4. Le azioni Git non partivano con la tab T3 in secondo piano ("target component is not showing"): ora il contesto è il frame dell'IDE.
5. Il menu "Add panel surface" ha `aria-labelledby`, non `aria-label`.
6. Il router scrive `mcp%3A…`: `T3Routes.threadRoute` decodifica il segmento.

Altre prove (2026-11-13), stesso metodo, versione 0.1.0:

| Prova | IDE | Risultato |
|---|---|---|
| "Ask T3 About Selection…" con una tab T3 aperta, selezione `Main.kt` riga 2 | IU 2026.2.3 | `t3 thread send` al thread della tab (messaggio con `Main.kt:2`); la tab torna in primo piano. |
| "Ask T3 About Selection…" senza tab T3 | IU 2026.2.3 | `t3 thread start`: thread nuovo in una tab nuova, con il titolo del thread. |
| "Changes vs origin/main" (branch `feature` con un commit, remoto `origin` locale) | IU 2026.2.3 | Tab "Changes Between origin/main and Current Working Tree" nel tool window VCS. |
| Apertura del progetto, clic su un thread nella lista | PyCharm 2026.2.4 | Thread più recente in una tab; il clic apre una tab nuova e il tool window torna su `/draft/…`. |
| Link `Main.kt:2`, "Open diff", "Toggle terminal drawer" | PyCharm 2026.2.4 | `Main.kt` alla riga 2; tab "Diff" con `Main.kt`; terminale "T3: demo". |
| "Commit", "Changes Uncommitted", selettore di branch | PyCharm 2026.2.4 | Tool window "Commit"; "Changes Between HEAD and Current Working Tree"; popup `GitDefaultBranchesPopup`. |

Fatti:
- Su un branch con commit davanti al remoto, T3 mostra "Push & create PR" al posto di "Commit". Il bridge non intercetta questo pulsante: T3 esegue l'azione.
- Senza `defaultModelSelection` nei settings T3, "Ask T3 About Selection…" senza tab mostra la notifica di errore (sezione "Limiti noti").

Not verified a runtime: i link `file:`, "Open in …" su un file, un worktree T3 fuori dal progetto IDE, il server remoto,
in PyCharm "Ask T3 About Selection…", la superficie "Files" e "Open in …".

Stato (2026-11-14): layout nell'IDE (sezione "Layout nell'IDE"). `./gradlew test`: 91 test, 0 errori, 1 saltato. `verifyPlugin`: "Compatible" su IU-262.12573.28 e IU-263.7307.200.
Prove con `runIde` (IU 2026.2.3), home T3 isolata sulla porta 3898, stesso metodo:

| Prova | Risultato |
|---|---|
| Apertura del progetto | "T3 Code" a sinistra su `/ide-navigator`, solo la sidebar a 461 px; thread più recente in una tab senza sidebar e senza pannello destro. |
| Clic su un thread nella sidebar | Tab nuova selezionata; il tool window torna su `/ide-navigator`. |
| "New thread", progetto `demo` | Tab "New T3 thread" su `/draft/c319…`. |
| Primo messaggio nella tab della bozza | La tab diventa la tab del thread `c0099f00…`, con il titolo del thread. |
| Settings nella sidebar | Tab "T3 Settings" su `/settings/general`; "Close" chiude la tab. |
| "Toggle right panel" nella tab, 2 clic | "T3 Workspace" con il pannello a tutta larghezza (632 px), poi nascosto; nella tab `aria-pressed` resta `false`. |
| "Terminal", "Diff" nella lista "Open a surface" | Terminale IDE "T3: demo"; tab "Changes Between HEAD and Current Working Tree" nel tool window VCS. |
| "Browser" nel menu "Add panel surface" | Superficie Browser di T3 in "T3 Workspace" a tutta larghezza (461 px), con la lista dei server locali. |
| "Device" nella lista "Open a surface" | Dialog di T3 "Set up devices" in "T3 Workspace" (device hub spento nella home di prova). |
| "Side chat" nella lista "Open a surface" | Superficie Side chat in "T3 Workspace" a tutta larghezza (461 px), con il composer. |
| T3 apre il pannello nella tab (clic da script) | La tab chiude il pannello; "T3 Workspace" mostra il pannello del thread. |
| Ricaricamento della tab con `isOpen: true` salvato | Nessun pannello e "T3 Workspace" resta nascosto. |
| Chiusura delle tab | "T3 Workspace" chiude i pannelli e si nasconde. |
| "Open conversations in editor tabs" spento | Il tool window mostra il thread più recente con la UI completa e la maniglia della sidebar. |

Problemi trovati e risolti durante le prove:
1. Sotto i 981 px T3 mostrava il pannello destro come sheet di 384 px: lo script risponde `false` anche a `(max-width: 980px)`.
2. La pagina "Not found" di `/ide-navigator` usciva dal bordo destro della sidebar: il CSS nasconde `main`.
3. "T3 Workspace" si apriva a ogni caricamento di una tab, per lo stato salvato dal pannello: la tab chiude in silenzio un pannello aperto al caricamento.
4. Con una superficie attiva, T3 chiama il pannello "Workspace panel: <superficie>". Il CSS cercava solo "Workspace panel": "T3 Workspace" mostrava la superficie Browser stretta, con uno spazio vuoto a sinistra, e la Side chat come pagina vuota. La prima prova di "Browser" non aveva visto il difetto. Il selettore ora è `[aria-label^="Workspace panel"]`.
5. "T3 Workspace" si apriva quando un codice leggeva il content manager del tool window nascosto, perché la piattaforma crea allora il contenuto. Il pannello della tab selezionata si apre ora solo con il tool window visibile.

Prove con `runPyCharm` (PyCharm 2026.2.4, PY-262.11879.98), stessa home T3:

| Prova | Risultato |
|---|---|
| Apertura del progetto | "T3 Code" a sinistra su `/ide-navigator`, solo la sidebar a 456 px; thread più recente in una tab; "T3 Workspace" nascosto. |
| Clic su un thread, "New thread" con `demo`, Settings e "Close" | Tab del thread; tab "New T3 thread" su `/draft/7d0c…`; tab "T3 Settings" aperta e poi chiusa. |
| "Toggle right panel" nella tab, 2 clic, con la tab a 990 px e a 660 px | "T3 Workspace" mostrato, poi nascosto. |
| Tab a 660 px | Layout stretto di T3: conversazione e composer a tutta larghezza, pulsante della sidebar nascosto, nessuno sheet, nessuno scroll orizzontale. |
| "Terminal" e "Files" nel menu "Add panel surface"; "Terminal" e "Diff" nella lista "Open a surface" | Terminale IDE "T3: demo"; Project view sul progetto; "Changes Between HEAD and Current Working Tree" nel tool window VCS. Il pannello di T3 non cambia. |
| "Side chat" in "T3 Workspace" | Superficie Side chat a tutta larghezza (379 px). |
| Riavvio di PyCharm con `isOpen: true` salvato | "T3 Workspace" resta nascosto. |

Not verified a runtime: la superficie "Device" dopo la configurazione del device hub, le superfici "Pull request" e "Linked pull requests", un messaggio nella Side chat, un'apertura del pannello da un agente.

Stato (2026-11-14): regole Linux del copia e incolla (issue #6) e pulsanti nei box dei messaggi utente. `./gradlew test`: 93 test, 0 errori, 1 saltato.
Prove con `runIde` (IU 2026.2.3, KDE Wayland, `WLToolkit`), home T3 isolata sulla porta 3898. Metodo: eventi del mouse Swing sul `JBCefOsrComponent` (script Groovy della sandbox), tasti DevTools senza comando di editing (Blink sceglie il comando), `xsel` per la selezione primaria X11.

| Prova | Prima | Dopo |
|---|---|---|
| Azioni IDE di Ctrl+C, Ctrl+Insert, Ctrl+X, Shift+Delete, Ctrl+V, Shift+Insert, Ctrl+Shift+V nel contesto della pagina | Tutte disattivate (`$Copy`, `$Paste`, `PasteMultiple`, `EditorPaste`, `Terminal.Paste`, …): i tasti vanno a Chromium | Uguale |
| Ctrl+V, Shift+Insert, Ctrl+Shift+V nel composer | Testo degli appunti IDE | Uguale |
| Ctrl+C, Ctrl+Insert su una selezione; Shift+Delete nel composer | Appunti IDE con il testo selezionato | Uguale |
| Selezione con il mouse nel messaggio utente | Selezione primaria Wayland e X11 invariate | Selezione primaria dell'IDE (Wayland) = testo selezionato |
| Clic centrale nel composer, primaria X11 e Wayland diverse | Testo della primaria X11 | Testo della primaria Wayland dell'IDE; Ctrl+V incolla ancora gli appunti |
| Prova a mano (2026-11-15): selezione nel messaggio utente, poi clic centrale in Konsole | — | Konsole incolla il testo selezionato nella pagina |
| Pulsante "Copy message" nel box | — | Appunti IDE = testo originale del messaggio; per 1,2 s l'etichetta è "Copied" |
| Pulsante del wrap, riga lunga | — | Una riga per riga del messaggio, con barra di scorrimento orizzontale; etichetta "Wrap lines" |

Fatti:
- I tasti Swing creati da script non arrivano a Chromium: la finestra della sandbox non riceve il focus su Wayland. Per questo la prova dei tasti controlla le azioni IDE nel contesto della pagina, poi usa i tasti DevTools.
- Not verified: una selezione in un campo password; Windows e macOS; una sessione Linux X11.

Stato (2026-11-16), versione 0.2.2: pulsanti nel box dei messaggi utente e clic sul progetto nel breadcrumb. `./gradlew test`: 96 test, 0 errori, 1 saltato.
Prove con `runIde` (IU 2026.2.3), home T3 isolata sulla porta 3898, clic veri di DevTools (porta 9223) e script Groovy della sandbox.
Per simulare la gara, uno script di prova nella tab del thread ritarda di 3 s la scrittura di `t3code:composer-drafts:v1`.

| Prova | 0.2.1 | 0.2.2 |
|---|---|---|
| Mouse sul box del messaggio utente | Pulsanti fuori dal box, a sinistra | Pulsanti nel box, in alto a destra; la prima riga finisce a 715 px, i pulsanti iniziano a 739 px |
| Wrap spento | — | Il testo inizia sotto i pulsanti, su una riga |
| Clic su "New thread in demo" (breadcrumb), bozza salvata in tempo | 1 tab della bozza | 1 tab della bozza |
| Stesso clic, bozza salvata dopo 3 s | 2 tab "New T3 thread"; la prima si ricarica ogni 3 s senza fine | 1 tab della bozza, nessun ricaricamento |
| Tab di una bozza sconosciuta, tab della bozza del progetto aperta | La tab si ricarica ogni 3 s senza fine e seleziona l'altra tab a ogni giro | La tab si chiude; selezionata la tab della bozza del progetto |
| Tab di una bozza sconosciuta, nessuna altra tab di bozza | — | La tab diventa la tab della bozza del progetto |
| Clic su un thread nella sidebar del tool window | Tab nuova | Tab nuova selezionata con il titolo del thread; il tool window torna su `/ide-navigator` |

Fatti:
- Il pulsante del progetto nel breadcrumb è "New thread in <progetto>": apre la bozza del progetto (`/draft/<id>`), o ne crea una. Il clic destro offre "Project settings" (`/projects/<chiave>`, che T3 sostituisce con `/settings/projects`).
- Una pagina che carica `/draft/<id>` senza la bozza in `localStorage` passa alla bozza del progetto, o ne crea una nuova, con un redirect (indice di cronologia 0).
- Ogni pagina T3 scrive in `localStorage` tutte le sue bozze: l'ultima pagina che scrive cancella le bozze create in un'altra pagina dopo il suo caricamento (limite di T3, not verified con più tab aperte a lungo).
- Not verified: la gara sulla macchina dell'utente (la prova la simula con un ritardo); un redirect nel tool window "T3 Workspace".

Stato (2026-11-18), versione 0.2.3: pulsanti nella riga azioni dei messaggi utente, "Edit in composer", segno del thread nella sidebar, stato non letto dei thread nelle tab in background. `./gradlew test`: 114 test, 0 errori, 1 saltato. `T3VisitGateScriptTest` esegue lo script del gate in Node (`src/test/resources/js/visit-gate-harness.js`); senza `node` i suoi 8 test sono saltati. `T3SidebarMarkScriptTest` esegue lo script del segno in Node (`src/test/resources/js/sidebar-mark-harness.js`, 4 test).
Server T3 0.1.9 isolato (home nella scratchpad, porta 3898), progetto `demo`, modello `claude-haiku-5-5`, run avviate con `t3 thread send`.
Prove 1: Chrome headless (CDP) con gli script generati dal plugin. Prove 2: `runIde` (IU 2026.2.3, Wayland), tab e split comandati da uno script Groovy della sandbox, stato delle pagine letto con DevTools (porta 9223).

| Prova | 0.2.2 | 0.2.3 |
|---|---|---|
| Pagina del thread nascosta, run completata (headless) | La pagina invia `thread.visit`; la riga perde "Done" | La pagina trattiene `thread.visit` (0 frame inviati); la riga resta "Done" |
| Stessa pagina, poi visibile (headless) | — | 1 frame `thread.visit` inviato; la riga perde "Done" |
| IDE: tab del thread in background (selezionato `README.md`), run completata | — | Gate `held`; la riga resta "Done" |
| IDE: selezione della tab del thread | — | `SHOWING_CHANGED`, gate `sent`; la riga perde "Done" |
| IDE: split, thread a sinistra, `README.md` selezionato a destra, run completata | — | Nessuna visita trattenuta (la tab è visibile); la riga non resta "Done" |
| IDE: tab aperta con `focus=false` (tab iniziale e `T3PageTabs.open(…, false)`) | — | Tab selezionata e visibile; segno sul suo thread |
| IDE: chiusura della tab segnata, `README.md` selezionato dopo | — | Segno tolto; nessun segno sulla tab chiusa |
| Riga azioni di un messaggio utente (headless e IDE) | orario, Edit from here, Copy message, Copy prompt link | stessi pulsanti, poi Disable line wrap e Edit in composer, 24x24 px come quelli di T3; 0 pulsanti nel box |
| Wrap spento (headless) | — | `white-space: nowrap`, larghezza del testo 870 px in un box di 357 px |
| "Edit in composer", composer vuoto (headless) | — | Testo originale con `` `code` `` e un a capo; focus nel composer; nessun invio |
| "Edit in composer", bozza "my draft" (headless) | — | "my draft", riga vuota, testo del messaggio |
| Segno nella sidebar (headless e IDE) | — | Solo la riga con la chiave completa `<environmentId>:<threadId>`; sfondo della riga attiva e barra blu; "Done" resta |
| Gate (Node, WebSocket finto): 2 visite dello stesso thread nascosto | — | La prima riceve `Exit` di successo dopo l'ultima parte del messaggio in arrivo; la seconda resta trattenuta |
| Gate (Node): salvataggio di `t3code:ui-state:v1` con pagina nascosta | — | Tempi di visita salvati invariati (anche con un tempo più vecchio o un thread assente), altri campi aggiornati; alla visibilità il gate salva i tempi trattenuti; se il salvataggio fallisce, li tiene per la visibilità successiva |
| Gate (Node): pagina di nuovo nascosta, socket chiuso | — | La visita trattenuta va all'ultimo socket aperto; senza socket aperto il gate scarta la visita (`dropped`) |
| Segno sidebar (Node, sidebar finta) | — | Solo la riga con la chiave completa; lo spostamento scorre la riga una volta; una riga ricreata riprende il segno in un frame; `null` toglie il segno |

Fatti:
- La risposta del server a `thread.visit` è `{"_tag":"Exit","requestId":<id>,"exit":{"_tag":"Success","value":{"sequence":<n>}}}`.
- Durante una run, T3 invia `thread.visit` dopo ogni aggiornamento del thread, al massimo ogni 10 s.
- La chiave di una riga della sidebar è `<environmentId>:<threadId>`.
- Un messaggio inviato da un agente o dalla CLI (`data-user-message-attribution="agent"`) è chiuso: il body e la riga azioni esistono solo quando l'utente lo apre.
- Un thread avviato dalla CLI e mai aperto riceve `thread.visit` come gli altri (verificato: gate `held`). T3 scrive una visita solo locale (`t3code:ui-state:v1`) quando il thread non ha l'ora di visita del server; nelle prove questo caso non è successo e `t3code:ui-state:v1` è rimasto vuoto.
- Un thread con "Done" dalla creazione (avviato dalla CLI, pagina aperta dopo la fine della run) può restare "Done" anche con la tab visibile: T3 non invia nessuna visita. Lo stesso succede senza il plugin nella prova headless.
- Una pagina T3 aperta in un altro client (un altro browser) invia le sue visite: la riga perde "Done" anche con il gate.
- Not verified: l'IDE ridotto a icona (la tab resta visibile per Swing); l'hover del mouse in headless e nell'IDE (le prove usano il focus).

### Fase 4 – Qualità e CI

- Test unitari: parsing di `server-runtime.json`, parsing dell'output JSON della CLI, filtro degli URL.
- `verifyPlugin` sulle versioni 2026.2 e 2026.3.
- Workflow GitHub Actions del template: build, test, verify, release.
- Test manuale su Linux, macOS e Windows.

### Fase 5 – Pubblicazione

> ⚠️ **Marchio:** il nome "T3 Code" appartiene al progetto `pingdotgg/t3code`. La licenza MIT copre il codice, non il nome.
> Chiama il plugin "unofficial", oppure chiedi il permesso al proprietario prima di pubblicarlo.

1. Crea un certificato e configura `signPlugin`.
2. Carica a mano la prima versione sul Marketplace.
3. Imposta `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY` e `PRIVATE_KEY_PASSWORD` per `publishPlugin` (gli stessi secret di `release.yml`).
4. Pubblica prima nel canale `beta` (`PUBLISH_CHANNEL=beta`), poi nel canale `default` (nessun `PUBLISH_CHANNEL`).
   `release.yml` pubblica una GitHub pre-release nel canale `beta` e una release normale nel canale `default`.

Stato (2026-11-18): il punto 1 è fatto. La chiave cifrata e il certificato self-signed (10 anni) sono fuori dal repository, in `~/.config/t3-plugin-signing/`.

## Rischi

- La CLI `t3` non è un'API pubblica stabile. Una nuova versione può cambiare flag o output.
  Il plugin controlla `t3 --version` e richiede una versione minima.
- Ogni pairing crea una sessione sul server. Il plugin revoca le sessioni con la stessa label prima di un nuovo pairing.
  Il pulsante del fallback usa una label propria (`… – system browser`): un nuovo clic revoca la sessione del clic precedente.
- Se il plugin avvia `t3 serve` su `~/.t3`, l'app desktop aperta dopo non si collega a quel server.
  Il suo backend esce con codice 78 e l'app mostra il dialog "This T3 home is unavailable" (verificato nel codice del bundle 0.1.9, non con una prova reale).
  Il commento dell'opzione "Start t3 serve…" in Settings avvisa l'utente.
- Il link `/pair#token=...` contiene un segreto. Il plugin non scrive il link nei log. Il token è monouso e scade dopo 2 minuti.
- Ogni progetto aperto crea un `JBCefBrowser`, e ogni browser usa memoria.
- JCEF cancella la cache, e quindi la sessione T3, in 4 casi (spike 1). Il plugin rifà il pairing quando `GET /api/auth/session` fallisce.

## Fonti

- [Embedded Browser (JCEF)](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html)
- [Incompatible Changes 2026.*](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)
- [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
- [intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template)
