package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.pairing.T3PairingService
import com.bicf.t3plugin.server.T3Runtime
import com.bicf.t3plugin.server.T3ServerService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The T3 Code web UI in a [JBCefBrowser] (steps 2–6 of the flow in PLAN.md).
 *
 * Rules:
 * - The panel shows a status text until the browser has a page to show. The user never sees the session JSON.
 * - The panel creates a pairing link only when `GET /api/auth/session` says `"authenticated": false`.
 * - The panel never shows or logs the pairing link.
 * - All state changes run on the EDT.
 */
internal class T3BrowserPanel(private val project: Project) : Disposable {

    private enum class Phase { CONNECTING, CHECKING_SESSION, PAIRING, READY, FAILED }

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

    private val bodyQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

    private var phase = Phase.CONNECTING
    private var runtime: T3Runtime? = null
    private var job: Job? = null
    private var disposed = false

    init {
        // Disposal order is reverse: query, browser, then client.
        Disposer.register(this, client)
        Disposer.register(this, browser)
        Disposer.register(this, bodyQuery)
        component.add(status.component, STATUS_CARD)
        component.add(browser.component, BROWSER_CARD)
        bodyQuery.addHandler { body ->
            onEdt { onSessionBody(body) }
            null
        }
        browser.jbCefClient.addLoadHandler(LoadHandler(), browser.cefBrowser)
        connect()
    }

    /** Finds or starts the T3 server, then checks the session. Call it on the EDT. */
    fun connect() {
        job?.cancel()
        runtime = null
        phase = Phase.CONNECTING
        showStatus(T3Bundle["toolWindow.connecting"])
        job = launchOnEdt {
            try {
                val found = runInterruptible(Dispatchers.IO) { service<T3ServerService>().ensureServer() }
                runtime = found
                phase = Phase.CHECKING_SESSION
                browser.loadURL(found.origin + T3Session.PATH)
            } catch (e: T3Exception) {
                fail(e.message)
            }
        }
    }

    private fun onPageLoaded(url: String) {
        val found = runtime ?: return
        if (phase == Phase.CHECKING_SESSION && url.startsWith(found.origin + T3Session.PATH)) {
            val script = bodyQuery.inject("document.body ? document.body.innerText : ''")
            browser.cefBrowser.executeJavaScript(script, url, 0)
        }
    }

    private fun onPageError(errorText: String) {
        val found = runtime ?: return
        if (phase == Phase.CHECKING_SESSION) {
            fail(T3Bundle.message("toolWindow.serverNotAnswering", found.origin, errorText))
        }
    }

    private fun onSessionBody(body: String) {
        val found = runtime ?: return
        if (phase != Phase.CHECKING_SESSION) return
        if (T3Session.isAuthenticated(body)) {
            open(found.origin + "/")
            return
        }
        phase = Phase.PAIRING
        showStatus(T3Bundle["toolWindow.pairing"])
        job = launchOnEdt {
            try {
                val link = runInterruptible(Dispatchers.IO) {
                    service<T3PairingService>().createPairingLink(found, T3PairingService.labelFor(project.name))
                }
                open(link.url)
            } catch (e: T3Exception) {
                fail(e.message)
            }
        }
    }

    private fun open(url: String) {
        phase = Phase.READY
        browser.loadURL(url)
        cards.show(component, BROWSER_CARD)
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

    override fun dispose() {
        disposed = true
        job?.cancel()
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

    private companion object {
        const val STATUS_CARD = "status"
        const val BROWSER_CARD = "browser"
    }
}
