# Piano: plugin JetBrains che ospita T3 Code

Il plugin mostra la web UI di T3 Code in un tool window con browser JCEF.
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
| `T3ThemeSync` | progetto | Invia il tema dell'IDE alla pagina con JavaScript a ogni `LafManagerListener.lookAndFeelChanged`. |
| `T3LinkHandler` | browser | Apre nel browser di sistema gli URL esterni all'origin T3 (`onBeforeBrowse`, `onBeforePopup`). |
| `T3Settings` | applicazione | Percorso della CLI, URL di un server remoto, avvio automatico del server. |

Flusso all'apertura del tool window:
1. Il plugin chiama `JBCefApp.isSupported()`. Se il risultato è `false`, il plugin mostra il pulsante "Apri nel browser".
2. `T3ServerService` legge `server-runtime.json` e controlla il processo e la porta.
3. Se il server non risponde, `T3ServerService` avvia `t3 serve --no-browser` e aspetta un nuovo `server-runtime.json`.
4. `JBCefBrowser` carica l'origin T3, e la pagina chiama `GET /api/auth/session`. Se la sessione è valida, il flusso va al passo 7.
5. `T3PairingService` crea un token con TTL di 2 minuti e label `JetBrains <IDE> – <progetto>`.
6. `JBCefBrowser` carica il link `/pair#token=...`. La web UI scambia il token con un cookie di sessione valido 30 giorni.
7. `T3ProjectBinder` registra la root del progetto e apre il thread più recente del progetto, oppure `/`.

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
- Ogni comando `t3` riceve `--base-dir <home>`: il plugin e la CLI usano sempre la stessa home.
- `T3CliIntegrationTest` usa la CLI reale su una home isolata e una porta libera: avvia `t3 serve`, crea un link di pairing e ferma il server.
  Esito: verde in 3,6 s. Comando: `T3_CLI_IT=1 ./gradlew test --tests com.bicf.t3plugin.T3CliIntegrationTest`.
- `runIde` (IU 2026.2.3, Linux, KDE Wayland) con il server dell'app desktop su 3773:
  1. Primo avvio: il plugin crea un pairing, la web UI scambia il token e il tool window mostra `http://127.0.0.1:3773/` ("T3 Code (Beta)").
     `t3 auth session list` mostra 1 sessione `JetBrains IntelliJ IDEA – demo-project`, valida 30 giorni.
  2. Secondo avvio: il tool window mostra la UI senza un nuovo pairing. Il numero di sessioni resta 1.
- Not verified: `T3FallbackPanel` (questo IDE ha JCEF), gli errori mostrati nel tool window, i link esterni e i popup della web UI.
- Prossimo passo: `T3LinkHandler`, poi `T3ProjectBinder` (passo 7) e `T3ThemeSync`.

### Fase 3 – Integrazione con l'IDE

- Azione editor "Ask T3 about selection": esegue `t3 thread start` e `t3 thread send` con `file:line` e il testo selezionato.
- Widget nella status bar con lo stato del server: attivo, fermo, errore.
- Server remoto: l'utente incolla un link `/pair#token=...`, creato con `t3 pair` sull'host remoto.
- Rifinitura di `T3ThemeSync`: il tema cambia dal vivo, senza ricaricare la pagina.

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
3. Imposta `ORG_GRADLE_PROJECT_intellijPlatformPublishingToken` per `publishPlugin`.
4. Pubblica prima nel canale `beta`, poi nel canale `default`.

## Rischi

- La CLI `t3` non è un'API pubblica stabile. Una nuova versione può cambiare flag o output.
  Il plugin controlla `t3 --version` e richiede una versione minima.
- Ogni pairing crea una sessione sul server. Senza revoca, le sessioni si accumulano in `t3 auth session list`.
- Se il plugin avvia `t3 serve` su `~/.t3`, l'app desktop aperta dopo trova la home occupata. Il comportamento dell'app desktop in questo caso è not verified.
- Il link `/pair#token=...` contiene un segreto. Il plugin non scrive il link nei log. Il token è monouso e scade dopo 2 minuti.
- Ogni progetto aperto crea un `JBCefBrowser`, e ogni browser usa memoria.
- JCEF cancella la cache, e quindi la sessione T3, in 4 casi (spike 1). Il plugin rifà il pairing quando `GET /api/auth/session` fallisce.

## Fonti

- [Embedded Browser (JCEF)](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html)
- [Incompatible Changes 2026.*](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)
- [IntelliJ Platform Gradle Plugin (2.x)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
- [intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template)
