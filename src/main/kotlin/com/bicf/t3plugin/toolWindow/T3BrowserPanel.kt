package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.T3Exception
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
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
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
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The T3 Code web UI in a [JBCefBrowser] (steps 2–7 of the flow in PLAN.md).
 *
 * Rules:
 * - The panel shows a status text until the browser has a page to show. The user never sees the session JSON.
 * - Local server: the panel creates a pairing link only when `GET /api/auth/session` says `"authenticated": false`,
 *   registers the project with `t3 project add`, and opens the newest thread of the project.
 * - Remote server (Settings): the panel never runs the CLI. Without a session, the user pastes a link from `t3 pair`.
 * - After a pairing, the panel waits until the page has a session, then opens the project thread.
 * - Links outside the server origin open in the system browser (see [T3LinkPolicy]).
 * - The IDE theme goes to the page at each page load and at each Look and Feel change (see [T3ThemeSync]).
 * - The panel never shows or logs a pairing link. All state changes run on the EDT.
 */
internal class T3BrowserPanel(private val project: Project) : Disposable {

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

    /** Carries text from the page to the plugin: the session JSON, or a pairing result. */
    private val pageQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

    private var phase = Phase.CONNECTING

    /** Origin of the server in use. The CEF threads read it in the link handlers. */
    @Volatile
    private var origin: String? = null
    private var remote = false
    private var environmentId: String? = null
    private var pendingPath: String? = null
    private var job: Job? = null

    @Volatile
    private var disposed = false

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
        val bus = ApplicationManager.getApplication().messageBus.connect(this)
        bus.subscribe(LafManagerListener.TOPIC, LafManagerListener { onEdt(::applyTheme) })
        bus.subscribe(T3SettingsListener.TOPIC, T3SettingsListener { onEdt(::connect) })
        project.service<T3ProjectScope>().browserPanel = this
        connect()
    }

    /** Finds the server and checks the session. Settings decide between the local and the remote server. Call it on the EDT. */
    fun connect() {
        job?.cancel()
        origin = null
        environmentId = null
        pendingPath = null
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
        if (value.startsWith(OPEN_PREFIX)) {
            val url = value.removePrefix(OPEN_PREFIX)
            handleLink(T3LinkPolicy.forPopup(origin, url), url)
            return
        }
        when (phase) {
            Phase.CHECKING_SESSION -> onSessionBody(value)
            Phase.PAIRING_PAGE -> when (value) {
                PAIRED -> onPaired()
                PAIRING_TIMEOUT -> fail(T3Bundle["toolWindow.pairingTimeout"])
            }
            else -> Unit
        }
    }

    private fun onSessionBody(body: String) {
        val serverOrigin = origin ?: return
        val authenticated = T3Session.isAuthenticated(body)
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
                if (link == null) open(serverOrigin + binding.path) else openPairingPage(link.url, binding.path)
            } catch (e: T3Exception) {
                fail(e.message)
            }
        }
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
            else -> applyTheme()
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
            Phase.READY -> {
                execute(linkBridgeScript())
                applyTheme()
            }
            else -> Unit
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
        execute(T3ThemeSync.script(T3ThemeSync.currentMode()))
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

    /** Runs on a CEF thread. Passes only the main frame to the EDT. Never passes the failed URL: it can contain a token. */
    private inner class LoadHandler : CefLoadHandlerAdapter() {
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
