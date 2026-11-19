package com.bicf.t3plugin.toolWindow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * The "Thread actions" menu of a T3 conversation, for the menu of its editor tab.
 *
 * T3 0.1.9 (verified in the web bundle): the breadcrumb button "Thread actions for <title>" builds the menu items
 * (`{id, label, disabled, destructive, separatorBefore, checked, children}`) and calls `contextMenu.show(items)` of
 * the native API of T3, at once in the click handler. `show` uses `window.desktopBridge.showContextMenu` when it
 * exists (the T3 desktop app), otherwise a menu in the page. T3 then runs the action of the id that `show` returns,
 * or nothing for null.
 *
 * Rules:
 * - [listScript]: the script sets a `desktopBridge` with only `showContextMenu`, clicks the button, and removes the
 *   bridge before the click returns. `showContextMenu` sends the items to the plugin and returns null: T3 shows no menu
 *   and runs no action. Without the button (a draft, a rename in progress), the script sends `null`.
 * - [chooseScript]: the same click, but `showContextMenu` returns the id when the fresh items have an enabled item
 *   with this id and without children. So T3 runs its own action (rename, pin, archive, delete, …) with its own checks
 *   and confirmations.
 * - Items: at most [MAX_ITEMS] per level and 2 levels. An id has only `A-Z a-z 0-9 : _ . -`; a label is at most 120
 *   characters. Other items are skipped.
 */
object T3ThreadMenu {

    const val PREFIX = "tmenu:"
    private const val MAX_ITEMS = 60
    private const val MAX_LABEL = 120
    private val ID = Regex("[A-Za-z0-9:_.-]{1,80}")
    private const val BUTTON = "button[aria-label^=\"Thread actions for \"]"

    data class Item(
        val id: String,
        val label: String,
        val enabled: Boolean = true,
        val destructive: Boolean = false,
        val separatorBefore: Boolean = false,
        val checked: Boolean? = null,
        val children: List<Item> = emptyList(),
    )

    fun isId(value: String): Boolean = ID.matches(value)

    private fun common(): String = """
        var B=document.querySelector('$BUTTON');
        function open(h){if(!B)return false;var had=Object.prototype.hasOwnProperty.call(window,'desktopBridge'),
          prev=window.desktopBridge,called=false;
          window.desktopBridge={showContextMenu:function(items){called=true;return Promise.resolve(h(items||[]));}};
          try{B.click();}finally{if(had)window.desktopBridge=prev;else{try{delete window.desktopBridge;}catch(x){window.desktopBridge=undefined;}}}
          return called;}
        function clean(a,d){return (a||[]).filter(function(i){return i&&i.id!=null;}).slice(0,$MAX_ITEMS).map(function(i){
          var o={id:String(i.id),label:String(i.label==null?i.id:i.label)};
          if(i.disabled)o.disabled=true;if(i.destructive)o.destructive=true;if(i.separatorBefore)o.separatorBefore=true;
          if(typeof i.checked==='boolean')o.checked=i.checked;
          if(d<1&&i.children&&i.children.length)o.children=clean(i.children,d+1);return o;});}
    """.trimIndent()

    /** Sends [PREFIX] + [requestId] + `:` + the items as JSON (or `null`) through [report]. */
    fun listScript(requestId: Long, report: (String) -> String): String {
        require(requestId > 0) { "Invalid request id" }
        return "(function(){${common()}\nvar items=null;open(function(a){items=clean(a,0);return null;});" +
            "var r='$PREFIX$requestId:'+JSON.stringify(items);${report("r")};})();"
    }

    /** Runs the action [id] of the thread menu, when the fresh menu has it. */
    fun chooseScript(id: String): String {
        require(isId(id)) { "Invalid item id" }
        return "(function(){${common()}\nfunction has(a){return (a||[]).some(function(i){return i&&(" +
            "(String(i.id)==='$id'&&!i.disabled&&!(i.children&&i.children.length))||has(i.children));});}" +
            "open(function(a){return has(a)?'$id':null;});})();"
    }

    /** Reads `<requestId>:<json>`, the text after [PREFIX]. Null items for `null` or invalid JSON. */
    fun parseReply(value: String): Pair<Long, List<Item>?>? {
        val id = value.substringBefore(':', "").toLongOrNull()?.takeIf { it > 0 } ?: return null
        val json = value.substringAfter(':')
        val array = runCatching { Json.parseToJsonElement(json) as? JsonArray }.getOrNull()
        return id to array?.let { items(it, 0) }
    }

    private fun items(array: JsonArray, depth: Int): List<Item> = array.take(MAX_ITEMS).mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.string("id")?.takeIf(::isId) ?: return@mapNotNull null
        val label = obj.string("label")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LABEL) ?: id
        val children = if (depth < 1) (obj["children"] as? JsonArray)?.let { items(it, depth + 1) }.orEmpty() else emptyList()
        Item(
            id = id,
            label = label,
            enabled = obj.flag("disabled") != true,
            destructive = obj.flag("destructive") == true,
            separatorBefore = obj.flag("separatorBefore") == true,
            checked = obj.flag("checked"),
            children = children,
        )
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.flag(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull

    /** The thread menu of one page, for the menu of its editor tab. Each function can run on any thread. */
    interface Host {
        /** The fresh items, or the last items when the page does not answer within [timeoutMs]. Null: no thread menu. */
        fun items(timeoutMs: Long): List<Item>?

        /** Runs the action [id] in the page. */
        fun run(id: String)
    }

    /**
     * The [Host] of a browser panel. [execute] runs a script in the page; [report] builds the code that sends a value to
     * the plugin; [ready] tells whether the page shows a thread. The panel passes each [PREFIX] message to [onReply].
     */
    internal class Client(
        private val execute: (String) -> Unit,
        private val report: (String) -> String,
        private val ready: () -> Boolean,
    ) : Host {
        private val nextId = AtomicLong()
        private val pending = ConcurrentHashMap<Long, CompletableFuture<List<Item>?>>()

        @Volatile
        private var last: List<Item>? = null

        override fun items(timeoutMs: Long): List<Item>? {
            if (!ready()) return null
            val id = nextId.incrementAndGet()
            val future = CompletableFuture<List<Item>?>()
            pending[id] = future
            return try {
                execute(listScript(id, report))
                future.get(timeoutMs, TimeUnit.MILLISECONDS).also { last = it }
            } catch (e: TimeoutException) {
                last
            } catch (e: ExecutionException) {
                last
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                last
            } finally {
                pending.remove(id)
            }
        }

        override fun run(id: String) {
            if (ready() && isId(id)) execute(chooseScript(id))
        }

        fun onReply(value: String) {
            val (id, items) = parseReply(value) ?: return
            pending[id]?.complete(items)
        }
    }
}
