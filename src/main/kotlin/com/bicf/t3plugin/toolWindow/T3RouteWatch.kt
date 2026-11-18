package com.bicf.t3plugin.toolWindow

/**
 * Reports the page path and the thread title of the T3 web UI to the plugin.
 *
 * The web UI is a single-page app: a click on a thread changes `location.pathname` without a page load,
 * so CEF reports no navigation. The plugin uses the path to open each thread in its own editor tab.
 *
 * Rules:
 * - Every 250 ms the script reads `location.pathname`. A new value goes to the plugin as
 *   [ROUTE_PREFIX] + history index + `:` + path (see [parseRoute]). The history index is `__TSR_index` of the T3 router:
 *   a click pushes a history entry (index 1 or more); a redirect of T3 replaces the entry (index 0 on a new page).
 * - A draft (`/draft/<id>`) goes to the plugin only when T3 saved the draft in `localStorage`, or after [DRAFT_WAIT_MS].
 *   Reason: T3 0.1.9 writes its drafts (`t3code:composer-drafts:v1`) 300 ms after a change. An editor tab that loads the
 *   draft before that write does not find the draft, and T3 opens a new draft in its place.
 * - The thread title is the label of the breadcrumb button "Thread actions for <title>". A new title goes to the plugin
 *   as [TITLE_PREFIX] + title, at most 200 characters.
 * - [homeScript] puts a path into the history and sends `popstate`: the T3 router changes the page without a page load.
 */
internal object T3RouteWatch {

    const val ROUTE_PREFIX = "route:"
    const val TITLE_PREFIX = "title:"
    const val DRAFT_WAIT_MS = 1500
    private const val TITLE_LABEL = "Thread actions for "
    private const val DRAFTS_KEY_PREFIX = "t3code:composer-drafts"

    /** A page path of the web UI, and the index of its history entry in the T3 router (0 when T3 has none). */
    data class Route(val index: Int, val path: String) {
        /** True when a click pushed this page. False on the first history entry: the page loaded or T3 replaced it. */
        val pushed: Boolean get() = index > 0
    }

    /** Reads `<index>:<path>`, the text after [ROUTE_PREFIX]. Null for any other text. */
    fun parseRoute(value: String): Route? {
        val index = value.substringBefore(':', "").toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val path = value.substringAfter(':')
        return if (path.startsWith("/")) Route(index, path) else null
    }

    fun script(report: (String) -> String): String = """
        (function(){if(window.__t3RouteWatch)return;window.__t3RouteWatch=true;
        var path=null,title=null,seen=null,since=0,timer=0;
        function saved(p){var m=/^\/draft\/([^\/]+)/.exec(p);if(!m)return true;
          try{var id='"'+decodeURIComponent(m[1])+'"';
            for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);
              if(k&&k.indexOf('$DRAFTS_KEY_PREFIX')===0&&String(localStorage.getItem(k)).indexOf(id)>=0)return true;}
          }catch(e){return true;}return false;}
        function tick(){var p=location.pathname;
          if(p===path){seen=null;}else{if(p!==seen){seen=p;since=Date.now();}
            if(saved(p)||Date.now()-since>=$DRAFT_WAIT_MS){path=p;seen=null;title=null;var h=history.state;
              var n=h&&typeof h.__TSR_index==='number'&&h.__TSR_index>0?h.__TSR_index:0;
              var r='$ROUTE_PREFIX'+n+':'+p;${report("r")};}
            else if(!timer){timer=setTimeout(function(){timer=0;tick();},50);}}
          var b=document.querySelector('[aria-label^="$TITLE_LABEL"]');
          var t=b?b.getAttribute('aria-label').substring(${TITLE_LABEL.length}).trim().substring(0,200):null;
          if(t&&t!==title){title=t;var s='$TITLE_PREFIX'+t;${report("s")};}}
        setInterval(tick,250);tick();
        })();
    """.trimIndent()

    /**
     * Shows [path] with the router of the page, without a page load. The tool window uses it to go back to
     * [T3Routes.NAVIGATOR_PATH] after it opened a page in an editor tab. [path] must be a safe path.
     */
    fun homeScript(path: String): String {
        require(T3Routes.isSafePath(path)) { "Not a safe path" }
        return "(function(){if(location.pathname!=='$path'){history.pushState({},'','$path');" +
            "dispatchEvent(new PopStateEvent('popstate',{state:history.state}));}})();"
    }

    /**
     * Goes back to the previous page of the router (the user left the page of the tab with a click, which the router
     * pushes). On the first history entry of the router, the page loads [path] instead. [path] must be a tab page.
     */
    fun backScript(path: String): String {
        require(T3Routes.tabPage(path) != null) { "Not a tab page" }
        return "(function(){var s=history.state;if(s&&s.__TSR_index>0){history.back();}else{location.assign('$path');}})();"
    }
}
