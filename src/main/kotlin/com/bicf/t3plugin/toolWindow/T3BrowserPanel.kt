package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.editor.T3ThreadTabs
import com.bicf.t3plugin.ide.T3IdeBridge
import com.bicf.t3plugin.ide.T3IdeRequest
import com.bicf.t3plugin.ide.T3IdeTools
import com.bicf.t3plugin.pairing.PairingLink
import com.bicf.t3plugin.pairing.T3PairingService
import com.bicf.t3plugin.project.T3ProjectBinder
import com.bicf.t3plugin.server.T3Cli
import com.bicf.t3plugin.server.T3Home
import com.bicf.t3plugin.server.T3ServerService
import com.bicf.t3plugin.settings.T3Settings
import com.bicf.t3plugin.settings.T3SettingsListener
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.CardLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The T3 Code web UI in a [JBCefBrowser] (steps 2–7 of the flow in PLAN.md), in the tool window or in an editor tab.
 *
 * Rules:
 * - The panel shows a status text until the browser has a page to show. The user never sees the session JSON.
 * - Tool window ([T3PageTarget.Navigator]), local server: the panel creates a pairing link only when
 *   `GET /api/auth/session` says `"authenticated": false`, and registers the project with `t3 project add`.
 *   With "Open conversations in editor tabs", it shows the T3 home page, opens the newest thread of the project in a tab
 *   (when no T3 tab is open), and opens each thread that the user selects in a tab, then goes back to the home page.
 *   Without that setting, it opens the newest thread of the project, as before.
 * - Editor tab ([T3PageTarget.Thread]): the panel shows its thread. A click on another thread opens that thread in its
 *   own tab, and the panel goes back to its thread. Without a session, the panel asks the user to open the tool window.
 * - Remote server (Settings): the panel never runs the CLI. Without a session, the user pastes a link from `t3 pair`.
 * - After a pairing, the panel waits until the page has a session, then opens the project page.
 * - Links outside the server origin open in the system browser (see [T3LinkPolicy]).
 * - The IDE colors go to the page at each page load, Look and Feel change and color scheme change (see [T3ThemeSync]).
 * - Copy and paste in the page use the IDE clipboard through the plugin (see [T3ClipboardBridge]).
 * - In a narrow tool window, the left sidebar of the page gets a drag handle (see [T3SidebarResize]).
 * - With "Use IDE tools", file links, "Open in …", diffs, the terminal and Git use the IDE (see [T3IdeBridge]).
 *   With a remote server, only the diff uses the IDE: the files are on another machine.
 * - Each mouse press in the page and each page load with the browser focused give the keyboard focus to Chromium.
 *   Without it, the page has no text caret and rejects clipboard writes.
 * - The panel never shows or logs a pairing link. All state changes run on the EDT.
 */
internal class T3BrowserPanel(
    private val project: Project,
    private val target: T3PageTarget = T3PageTarget.Navigator,
    private val onThreadTitle: (String) -> Unit = {},
) : Disposable {

    private enum class Phase { CONNECTING, CHECKING_SESSION, PREPARING, WAITING_FOR_LINK, PAIRING_PAGE, READY, FAILED }

    private val cards = CardLayout()
    val component: JComponent = JPanel(cards)

    private val status = T3StatusView()

    // A query created after the native browser needs a slot in the query pool of the client.
    private val client = JBCefApp.getInstance().createClient().apply {
        setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 1)
    }

    // The browser card stays hidden during the session check.
    // Without "create immediately", CEF creates the native browser only when the component is shown, and no page loads.
    private val browser = JBCefBrowser.createBuilder().setClient(client).setCreateImmediately(true).build()

    /** The component that takes the keyboard focus in an editor tab. */
    val focusComponent: JComponent get() = browser.component

    /** Carries text from the page to the plugin: the session JSON, a pairing result, or a bridge message. */
    private val pageQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

    private var phase = Phase.CONNECTING

    /** Origin of the server in use. The CEF threads read it in the link handlers. */
    @Volatile
    private var origin: String? = null
    @Volatile
    private var remote = false
    private var environmentId: String? = null
    private var pendingPath: String? = null
    private var pagePath: String? = null
    private var initialTab: T3Routes.ThreadRoute? = null
    private var job: Job? = null

    @Volatile
    private var disposed = false

    private val navigator: Boolean get() = target == T3PageTarget.Navigator
    private val threadRoute: T3Routes.ThreadRoute? get() = (target as? T3PageTarget.Thread)?.route

    init {
        // Disposal order is reverse: query, browser, then client.
        Disposer.register(this, client)
        Disposer.register(this, browser)
        Disposer.register(this, pageQuery)
        component.add(status.component, STATUS_CARD)
        component.add(browser.component, BROWSER_CARD)
        pageQuery.addHandler { value ->
            onEdt { onPageMessage(value) }
            null
        }
        browser.jbCefClient.addLoadHandler(LoadHandler(), browser.cefBrowser)
        browser.jbCefClient.addRequestHandler(LinkHandler(), browser.cefBrowser)
        browser.jbCefClient.addLifeSpanHandler(PopupHandler(), browser.cefBrowser)
        // JBCefBrowser gives the focus to Chromium only on a Swing focus change. When the browser component keeps the
        // Swing focus but Chromium lost it (or never got it), a click does not change the Swing focus: no caret.
        browser.cefBrowser.uiComponent.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = browser.cefBrowser.setFocus(true)
        })
        val bus = ApplicationManager.getApplication().messageBus.connect(this)
        bus.subscribe(LafManagerListener.TOPIC, LafManagerListener { onEdt(::applyTheme) })
        bus.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { onEdt(::applyTheme) })
        bus.subscribe(T3SettingsListener.TOPIC, T3SettingsListener { onEdt(::connect) })
        if (navigator) project.service<T3ProjectScope>().browserPanel = this
        connect()
    }

    /** Finds the server and checks the session. Settings decide between the local and the remote server. Call it on the EDT. */
    fun connect() {
        job?.cancel()
        origin = null
        environmentId = threadRoute?.environmentId
        pendingPath = null
        pagePath = null
        initialTab = null
        phase = Phase.CONNECTING
        showStatus(T3Bundle["toolWindow.connecting"])
        val remoteOrigin = T3Settings.getInstance().remoteOrigin
        remote = remoteOrigin != null
        if (remoteOrigin != null) {
            checkSession(remoteOrigin)
            return
        }
        job = launchOnEdt {
            try {
                val runtime = runInterruptible(Dispatchers.IO) { service<T3ServerService>().ensureServer() }
                checkSession(runtime.origin)
            } catch (e: T3Exception) {
                fail(e.message)
            }
        }
    }

    /** The id of the thread on screen, or null. Call it on the EDT. */
    fun currentThreadId(): String? {
        val serverOrigin = origin ?: return null
        val environment = environmentId ?: return null
        if (phase != Phase.READY) return null
        return T3Routes.threadIdIn(browser.cefBrowser.url ?: return null, serverOrigin, environment)
    }

    /**
     * Shows [threadId] when the panel is ready. Call it on the EDT.
     * When the panel is not ready, the next connection opens the newest thread of the project, which is this thread.
     */
    fun openThread(threadId: String) {
        val serverOrigin = origin ?: return
        val path = environmentId?.let { T3Routes.threadPath(it, threadId) } ?: return
        if (phase == Phase.READY) browser.loadURL(serverOrigin + path)
    }

    private fun checkSession(serverOrigin: String) {
        origin = serverOrigin
        phase = Phase.CHECKING_SESSION
        browser.loadURL(serverOrigin + T3Session.PATH)
    }

    private fun onPageMessage(value: String) {
        when {
            value.startsWith(OPEN_PREFIX) -> {
                val url = value.removePrefix(OPEN_PREFIX)
                handleLink(T3LinkPolicy.forPopup(origin, url), url)
            }
            value.startsWith(T3ClipboardBridge.COPY_PREFIX) -> if (phase == Phase.READY) {
                CopyPasteManager.getInstance().setContents(StringSelection(value.removePrefix(T3ClipboardBridge.COPY_PREFIX)))
            }
            value.startsWith(T3ClipboardBridge.PASTE_PREFIX) -> {
                val id = value.removePrefix(T3ClipboardBridge.PASTE_PREFIX)
                if (phase == Phase.READY && T3ClipboardBridge.isRequestId(id)) {
                    val payload = T3ClipboardBridge.payload(CopyPasteManager.getInstance().contents)
                    execute(T3ClipboardBridge.replyScript(id, payload))
                }
            }
            value.startsWith(T3RouteWatch.ROUTE_PREFIX) -> if (phase == Phase.READY) onRoute(value.removePrefix(T3RouteWatch.ROUTE_PREFIX))
            value.startsWith(T3RouteWatch.TITLE_PREFIX) -> if (phase == Phase.READY) onTitle(value.removePrefix(T3RouteWatch.TITLE_PREFIX))
            value.startsWith(T3IdeRequest.PREFIX) -> if (phase == Phase.READY) onIdeRequest(value.removePrefix(T3IdeRequest.PREFIX))
            else -> when (phase) {
                Phase.CHECKING_SESSION -> onSessionBody(value)
                Phase.PAIRING_PAGE -> when (value) {
                    PAIRED -> onPaired()
                    PAIRING_TIMEOUT -> fail(T3Bundle["toolWindow.pairingTimeout"])
                }
                else -> Unit
            }
        }
    }

    private fun onSessionBody(body: String) {
        val serverOrigin = origin ?: return
        val authenticated = T3Session.isAuthenticated(body)
        val route = threadRoute
        if (route != null) {
            if (authenticated) open(serverOrigin + route.path) else askToPairInToolWindow()
            return
        }
        if (remote) {
            if (authenticated) open(serverOrigin + "/") else askForPairingLink(serverOrigin)
            return
        }
        phase = Phase.PREPARING
        showStatus(if (authenticated) T3Bundle["toolWindow.opening"] else T3Bundle["toolWindow.pairing"])
        val basePath = project.basePath
        job = launchOnEdt {
            try {
                val (link, binding) = runInterruptible(Dispatchers.IO) {
                    val link = if (authenticated) {
                        null
                    } else {
                        service<T3PairingService>().createPairingLink(serverOrigin, T3PairingService.sessionLabel())
                    }
                    link to bindProject(basePath)
                }
                environmentId = binding.environmentId
                val path = startPath(binding)
                if (link == null) open(serverOrigin + path) else openPairingPage(link.url, path)
            } catch (e: T3Exception) {
                fail(e.message)
            }
        }
    }

    /**
     * The first page of the tool window. With editor tabs: `/`, and the newest thread of the project opens in a tab
     * when the page is ready (after a pairing, the tab finds a session). Without editor tabs: the newest thread.
     */
    private fun startPath(binding: T3ProjectBinder.Binding): String {
        if (!T3Settings.getInstance().openThreadsInEditor) return binding.path
        val environment = binding.environmentId
        val thread = binding.threadId
        initialTab = if (environment != null && thread != null) T3Routes.threadRoute("/$environment/$thread") else null
        return "/"
    }

    /** Step 7. A binding error never blocks the web UI: the panel opens `/` instead. Runs on a background thread. */
    private fun bindProject(basePath: String?): T3ProjectBinder.Binding {
        val home = T3Home.detect()
        if (basePath == null) return T3ProjectBinder.Binding("/", T3ProjectBinder.environmentId(home))
        return try {
            T3ProjectBinder.bind(T3Cli.require(home), basePath)
        } catch (e: T3Exception) {
            LOG.warn("T3 project binding failed: ${e.message}")
            T3ProjectBinder.Binding("/", T3ProjectBinder.environmentId(home))
        }
    }

    private fun askForPairingLink(serverOrigin: String) {
        phase = Phase.WAITING_FOR_LINK
        status.show(T3Bundle.message("toolWindow.remote.notPaired", serverOrigin), T3Bundle["toolWindow.remote.paste"]) {
            pastePairingLink(serverOrigin)
        }
        cards.show(component, STATUS_CARD)
    }

    /**
     * Editor tab without a session. Only the tool window pairs the IDE: a new pairing revokes the sessions with the same
     * label, so 2 panels that pair at the same time would revoke each other.
     */
    private fun askToPairInToolWindow() {
        phase = Phase.FAILED
        status.show(T3Bundle["editor.notPaired"], T3Bundle["editor.openToolWindow"]) {
            ToolWindowManager.getInstance(project).getToolWindow(T3ToolWindowFactory.ID)?.activate(null)
            connect()
        }
        cards.show(component, STATUS_CARD)
    }

    private fun pastePairingLink(serverOrigin: String) {
        val clipboard = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        val validator = object : InputValidator {
            override fun checkInput(inputString: String?): Boolean = PairingLink.fromPasted(inputString, serverOrigin) != null
            override fun canClose(inputString: String?): Boolean = checkInput(inputString)
        }
        val text = Messages.showInputDialog(
            project,
            T3Bundle.message("toolWindow.remote.pastePrompt", serverOrigin),
            T3Bundle["toolWindow.remote.pasteTitle"],
            null,
            clipboard?.takeIf { PairingLink.fromPasted(it, serverOrigin) != null },
            validator,
        ) ?: return
        val link = PairingLink.fromPasted(text, serverOrigin) ?: return
        openPairingPage(link.url, null)
    }

    private fun openPairingPage(url: String, pathAfterPairing: String?) {
        pendingPath = pathAfterPairing?.takeIf { it != "/" }
        phase = Phase.PAIRING_PAGE
        browser.loadURL(url)
        cards.show(component, BROWSER_CARD)
    }

    private fun onPaired() {
        val serverOrigin = origin ?: return
        phase = Phase.READY
        val target = pendingPath
        pendingPath = null
        when {
            target != null -> browser.loadURL(serverOrigin + target)
            browser.cefBrowser.url.orEmpty().startsWith("$serverOrigin/pair") -> browser.loadURL("$serverOrigin/")
            // The web UI left the pairing page without a page load, so onPageLoaded does not run for the app.
            else -> preparePage()
        }
    }

    private fun open(url: String) {
        phase = Phase.READY
        browser.loadURL(url)
        cards.show(component, BROWSER_CARD)
    }

    private fun onPageLoaded(url: String) {
        val serverOrigin = origin ?: return
        if (!url.startsWith(serverOrigin)) return
        when (phase) {
            Phase.CHECKING_SESSION ->
                if (url.startsWith(serverOrigin + T3Session.PATH)) execute(pageQuery.inject("document.body ? document.body.innerText : ''"))
            Phase.PAIRING_PAGE -> if (url.startsWith("$serverOrigin/pair")) execute(pairingWatchScript()) else onPaired()
            Phase.READY -> preparePage()
            else -> Unit
        }
    }

    /** Installs the bridges, the route watch, the sidebar handle and the IDE colors in the T3 page on screen. */
    private fun preparePage() {
        if (browser.cefBrowser.uiComponent.isFocusOwner) browser.cefBrowser.setFocus(true)
        val report = { expression: String -> pageQuery.inject(expression) }
        execute(linkBridgeScript())
        execute(T3ClipboardBridge.script(report))
        execute(T3SidebarResize.script())
        execute(T3RouteWatch.script(report))
        val features = ideFeatures()
        if (features.any) execute(T3IdeBridge.script(features, report))
        applyTheme()
        val tab = initialTab
        initialTab = null
        val tabs = project.service<T3ThreadTabs>()
        if (tab != null && !tabs.hasOpenTabs()) tabs.open(tab, focus = false)
    }

    /** The T3 tools that the IDE takes over. A remote server keeps its files, terminal and Git on its own machine. */
    private fun ideFeatures(): T3IdeBridge.Features {
        val on = T3Settings.getInstance().useIdeTools
        val local = on && !remote
        return T3IdeBridge.Features(files = local, diff = on, terminal = local, git = local)
    }

    /** A new page path of the single-page app (see [T3RouteWatch]). */
    private fun onRoute(path: String) {
        pagePath = path
        val route = T3Routes.threadRoute(path) ?: return
        val own = threadRoute
        val tabs = project.service<T3ThreadTabs>()
        when {
            own == null -> if (T3Settings.getInstance().openThreadsInEditor) {
                tabs.open(route)
                execute(T3RouteWatch.goHomeScript())
            }
            route != own -> {
                tabs.open(route)
                execute(T3RouteWatch.backScript(own.path))
            }
        }
    }

    private fun onTitle(title: String) {
        val own = threadRoute ?: return
        if (pagePath?.let(T3Routes::threadRoute) == own && title.isNotBlank()) onThreadTitle(title)
    }

    private fun onIdeRequest(json: String) {
        val request = T3IdeRequest.parse(json) ?: return
        project.service<T3IdeTools>().handle(request, browser.component) { handled ->
            if (!disposed) execute(T3IdeBridge.replyScript(request.id, handled))
        }
    }

    /**
     * Sends `window.open` calls and links with `target="_blank"` or another origin to the plugin, which applies [T3LinkPolicy].
     * Reason: with out-of-process JCEF (the default), CEF blocks popups and never calls `onBeforePopup` (verified with runIde).
     */
    private fun linkBridgeScript(): String {
        val report = pageQuery.inject("'$OPEN_PREFIX' + u")
        return "(function(){if(window.__t3LinkBridge)return;window.__t3LinkBridge=true;" +
            "function send(u){try{u=new URL(String(u),location.href).href;}catch(e){return;}$report}" +
            "window.open=function(u){if(u){send(u);}return null;};" +
            "document.addEventListener('click',function(e){" +
            "var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;if(!a)return;" +
            "var t=(a.getAttribute('target')||'').toLowerCase();" +
            "if(t==='_blank'||t==='_new'||a.origin!==location.origin){e.preventDefault();send(a.href);}" +
            "},true);})();"
    }

    private fun onPageError(errorText: String) {
        val serverOrigin = origin ?: return
        if (phase == Phase.CHECKING_SESSION) fail(T3Bundle.message("toolWindow.serverNotAnswering", serverOrigin, errorText))
    }

    /** Asks the server every 500 ms whether the token exchange created a session. Stops after 20 s. */
    private fun pairingWatchScript(): String {
        fun report(value: String) = pageQuery.inject("'$value'")
        return "(function(){var n=0;" +
            "function retry(){if(++n<40){setTimeout(check,500);}else{${report(PAIRING_TIMEOUT)}}}" +
            "function check(){fetch('${T3Session.PATH}',{credentials:'include'})" +
            ".then(function(r){return r.json();})" +
            ".then(function(s){if(s&&s.authenticated===true){${report(PAIRED)}}else{retry();}})" +
            ".catch(function(){retry();});}" +
            "check();})();"
    }

    private fun applyTheme() {
        val serverOrigin = origin ?: return
        if (phase != Phase.READY || !browser.cefBrowser.url.orEmpty().startsWith(serverOrigin)) return
        execute(T3ThemeSync.script(T3IdePalette.current()))
    }

    private fun execute(script: String) {
        browser.cefBrowser.executeJavaScript(script, origin ?: "about:blank", 0)
    }

    private fun fail(message: String?) {
        phase = Phase.FAILED
        status.show(T3Bundle.message("toolWindow.failed", message.orEmpty()), T3Bundle["toolWindow.retry"]) { connect() }
        cards.show(component, STATUS_CARD)
    }

    private fun showStatus(text: String) {
        status.show(text)
        cards.show(component, STATUS_CARD)
    }

    private fun launchOnEdt(block: suspend () -> Unit): Job =
        project.service<T3ProjectScope>().coroutineScope.launch(Dispatchers.EDT) { block() }

    private fun onEdt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({ if (!disposed) block() }, ModalityState.any())
    }

    /** Runs on a CEF thread. Returns true when CEF must cancel the navigation or the popup. */
    private fun handleLink(action: T3LinkPolicy.Action, url: String): Boolean = when (action) {
        T3LinkPolicy.Action.ALLOW -> false
        T3LinkPolicy.Action.OPEN_EXTERNALLY -> {
            BrowserUtil.browse(url)
            true
        }
        T3LinkPolicy.Action.LOAD_IN_PLACE -> {
            onEdt { browser.loadURL(url) }
            true
        }
        T3LinkPolicy.Action.BLOCK -> true
    }

    override fun dispose() {
        disposed = true
        job?.cancel()
        val scope = project.serviceIfCreated<T3ProjectScope>()
        if (scope?.browserPanel === this) scope.browserPanel = null
    }

    /**
     * Installs the IDE bridge before the scripts of the page run. Reason: the T3 client keeps the `send` function of its
     * WebSocket when it creates the socket, so a patch of `WebSocket.prototype.send` after the page load never sees
     * an RPC message (verified with runIde). Runs on a CEF thread; [ideFeatures] reads only settings and [remote].
     */
    private fun installBridgeEarly(url: String) {
        val serverOrigin = origin ?: return
        if (!url.startsWith("$serverOrigin/") || url.startsWith(serverOrigin + T3Session.PATH)) return
        val features = ideFeatures()
        if (features.any) execute(T3IdeBridge.script(features) { expression -> pageQuery.inject(expression) })
    }

    /** Runs on a CEF thread. Passes only the main frame to the EDT. Never passes the failed URL: it can contain a token. */
    private inner class LoadHandler : CefLoadHandlerAdapter() {
        override fun onLoadStart(cefBrowser: CefBrowser, frame: CefFrame, transitionType: CefRequest.TransitionType) {
            if (frame.isMain && !disposed) installBridgeEarly(frame.url ?: return)
        }

        override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
            if (!frame.isMain) return
            val url = frame.url
            onEdt { onPageLoaded(url) }
        }

        override fun onLoadError(
            cefBrowser: CefBrowser,
            frame: CefFrame,
            errorCode: CefLoadHandler.ErrorCode,
            errorText: String,
            failedUrl: String,
        ) {
            if (!frame.isMain || errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
            onEdt { onPageError(errorText) }
        }
    }

    /** Main-frame navigations (T3LinkHandler in PLAN.md). */
    private inner class LinkHandler : CefRequestHandlerAdapter() {
        override fun onBeforeBrowse(
            cefBrowser: CefBrowser,
            frame: CefFrame,
            request: CefRequest,
            userGesture: Boolean,
            isRedirect: Boolean,
        ): Boolean {
            if (!frame.isMain) return false
            val url = request.url ?: return false
            return handleLink(T3LinkPolicy.forNavigation(origin, url), url)
        }
    }

    /** Popups, for example links with `target="_blank"` (T3LinkHandler in PLAN.md). */
    private inner class PopupHandler : CefLifeSpanHandlerAdapter() {
        override fun onBeforePopup(cefBrowser: CefBrowser, frame: CefFrame, targetUrl: String?, targetFrameName: String?): Boolean {
            val url = targetUrl ?: return true
            return handleLink(T3LinkPolicy.forPopup(origin, url), url)
        }
    }

    private companion object {
        val LOG = logger<T3BrowserPanel>()
        const val STATUS_CARD = "status"
        const val BROWSER_CARD = "browser"
        const val PAIRED = "paired"
        const val PAIRING_TIMEOUT = "pairing-timeout"
        const val OPEN_PREFIX = "open:"
    }
}
