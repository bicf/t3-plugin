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
 * - [goHomeScript] clicks the "T3 Code home" link: the T3 router changes the page without a page load.
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

    /** Goes to the T3 home page with the router of the page. Without the home link, the page loads `/`. */
    fun goHomeScript(): String =
        "(function(){var a=document.querySelector('a[aria-label=\"T3 Code home\"]');if(a){a.click();}" +
            "else{location.assign('/');}})();"

    /**
     * Goes back to the previous page of the router (the user left the thread of the tab with a click, which the router
     * pushes). On the first history entry of the router, the page loads [path] instead. [path] must be a thread path.
     */
    fun backScript(path: String): String {
        require(T3Routes.threadRoute(path)?.path == path) { "Not a thread path" }
        return "(function(){var s=history.state;if(s&&s.__TSR_index>0){history.back();}else{location.assign('$path');}})();"
    }
}
