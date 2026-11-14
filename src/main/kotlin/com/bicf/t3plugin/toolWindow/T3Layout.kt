package com.bicf.t3plugin.toolWindow

/**
 * The layout of the T3 web UI in the IDE, with "Open conversations in editor tabs" (verified with T3 0.1.9).
 *
 * Rules:
 * - [Mode.NAVIGATOR], the "T3 Code" tool window: only the T3 sidebar (projects, threads, search, settings), at the full
 *   width of the tool window. The page is [T3Routes.NAVIGATOR_PATH], where T3 shows no conversation. T3 shows a narrow
 *   page with sheets: the sidebar under 768 px, the right panel under 981 px. In both tool windows the script answers
 *   `false` to the media queries `(max-width: 767px)` and `(max-width: 980px)` of T3, so T3 shows the desktop layout.
 * - [Mode.CONVERSATION], an editor tab: no left sidebar and no right panel ("Workspace panel"). A click on
 *   "Toggle right panel" goes to the plugin ([PANEL_TOGGLE]), which shows the right panel in the "T3 Workspace" tool
 *   window. When T3 opens its right panel in the tab (a keyboard shortcut, an agent), the script sends [PANEL_OPENED]
 *   and closes the panel of the tab: the plugin shows the panel in the tool window, which reloads to read the new state.
 *   A panel that is open when the page loads (state saved by the tool window) closes without a message.
 * - [Mode.PANEL], the "T3 Workspace" tool window: only the right panel of the thread, at the full width. The script
 *   opens the panel when it is closed. T3 keeps the panel state per thread in `localStorage`, so the tab and the tool
 *   window show the same surfaces after a page load.
 * - The style has the id `t3-ide-layout`. The scripts run when the page starts to load and again after the load.
 */
internal object T3Layout {

    enum class Mode { NAVIGATOR, CONVERSATION, PANEL }

    const val PREFIX = "layout:"
    const val PANEL_TOGGLE = "panel-toggle"
    const val PANEL_OPENED = "panel-opened"

    // T3 0.1.9: under 768 px the sidebar is a sheet; under 981 px the right panel is a sheet.
    private val NARROW_QUERIES = listOf("(max-width: 767px)", "(max-width: 980px)")
    // The label is "Workspace panel" without a surface and "Workspace panel: <surface>" with a surface.
    private const val PANEL = "[aria-label^=\"Workspace panel\"]"
    private const val RIGHT_TOGGLE = "button[aria-label=\"Toggle right panel\"]"

    private const val COMMON_CSS = "button[aria-label=\"Toggle main sidebar\"]{display:none!important}"

    private fun css(mode: Mode): String = COMMON_CSS + when (mode) {
        Mode.NAVIGATOR ->
            "[data-slot=sidebar-wrapper]{--sidebar-width:100vw!important}" +
                // The conversation area, and the "Not found" page that T3 shows for NAVIGATOR_PATH.
                "[data-slot=sidebar-inset],main{display:none!important}" +
                "[data-slot=sidebar]{display:block!important}" +
                "[data-slot=sidebar-container]{display:flex!important;left:0!important;right:auto!important;width:100vw!important}" +
                "[data-slot=sidebar-rail]{display:none!important}"
        Mode.CONVERSATION ->
            "[data-slot=sidebar]{display:none!important}" +
                "$PANEL{display:none!important}" +
                "button[aria-label=\"Maximize panel\"],button[aria-label=\"Restore panel size\"]{display:none!important}"
        Mode.PANEL ->
            "[data-slot=sidebar]{display:none!important}" +
                "[data-slot=sidebar-inset]:not(:has($PANEL)){visibility:hidden!important}" +
                "div:has(> $PANEL) > :not($PANEL){display:none!important}" +
                "$PANEL{width:100%!important;max-width:none!important;flex:1 1 auto!important;border-left-width:0!important}" +
                "$PANEL > div > div{width:100%!important;flex:1 1 auto!important}"
    }

    /** The style, and for the tool windows the media query answer. Runs before the scripts of the page. */
    fun earlyScript(mode: Mode): String {
        val media = if (mode == Mode.CONVERSATION) "" else """
            if(!window.__t3MatchMedia){window.__t3MatchMedia=true;var mm=window.matchMedia.bind(window),
              Q=${NARROW_QUERIES.joinToString(",", "[", "]") { "'$it'" }};
              window.matchMedia=function(q){var r=mm(q);if(Q.indexOf(String(q).replace(/\s+/g,' ').trim())<0)return r;
                return{matches:false,media:r.media,onchange:null,addEventListener:function(){},removeEventListener:function(){},
                  addListener:function(){},removeListener:function(){},dispatchEvent:function(){return false;}};};}
        """.trimIndent()
        return """
            (function(){$media
            function add(){if(document.getElementById('t3-ide-layout'))return true;var p=document.head||document.documentElement;
              if(!p)return false;var s=document.createElement('style');s.id='t3-ide-layout';s.textContent='${css(mode)}';
              p.appendChild(s);return true;}
            if(!add())document.addEventListener('DOMContentLoaded',add);})();
        """.trimIndent()
    }

    /** The style again, and the panel handling of [mode]. [report] works as in [T3RouteWatch.script]. */
    fun script(mode: Mode, report: (String) -> String): String = earlyScript(mode) + when (mode) {
        Mode.NAVIGATOR -> ""
        Mode.CONVERSATION -> """
            (function(){if(window.__t3Layout)return;window.__t3Layout=true;var busy=false,seen=false;
            window.addEventListener('click',function(e){if(!e.isTrusted)return;
              var b=e.target&&e.target.closest?e.target.closest('$RIGHT_TOGGLE'):null;if(!b)return;
              e.preventDefault();e.stopImmediatePropagation();var m='$PREFIX$PANEL_TOGGLE';${report("m")};},true);
            setInterval(function(){var b=document.querySelector('$RIGHT_TOGGLE');if(!b)return;
              var open=b.getAttribute('aria-pressed')==='true',first=!seen;seen=true;
              if(!open){busy=false;return;}if(busy)return;busy=true;
              if(!first){var m='$PREFIX$PANEL_OPENED';${report("m")};}
              try{b.click();}catch(x){}},250);})();
        """.trimIndent()
        Mode.PANEL -> """
            (function(){if(window.__t3Layout)return;window.__t3Layout=true;
            setInterval(function(){if(document.querySelector('$PANEL'))return;var b=document.querySelector('$RIGHT_TOGGLE');
              if(b&&b.getAttribute('aria-pressed')!=='true'){try{b.click();}catch(x){}}},300);})();
        """.trimIndent()
    }
}
