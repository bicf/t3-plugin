package com.bicf.t3plugin.toolWindow

/**
 * Reports the page path and the thread title of the T3 web UI to the plugin.
 *
 * The web UI is a single-page app: a click on a thread changes `location.pathname` without a page load,
 * so CEF reports no navigation. The plugin uses the path to open each thread in its own editor tab.
 *
 * Rules:
 * - Every 250 ms the script reads `location.pathname`. A new value goes to the plugin as [ROUTE_PREFIX] + path.
 * - The thread title is the label of the breadcrumb button "Thread actions for <title>". A new title goes to the plugin
 *   as [TITLE_PREFIX] + title, at most 200 characters.
 * - [homeScript] puts a path into the history and sends `popstate`: the T3 router changes the page without a page load.
 */
internal object T3RouteWatch {

    const val ROUTE_PREFIX = "route:"
    const val TITLE_PREFIX = "title:"
    private const val TITLE_LABEL = "Thread actions for "

    fun script(report: (String) -> String): String = """
        (function(){if(window.__t3RouteWatch)return;window.__t3RouteWatch=true;
        var path=null,title=null;
        function tick(){var p=location.pathname;if(p!==path){path=p;title=null;var r='$ROUTE_PREFIX'+p;${report("r")};}
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
