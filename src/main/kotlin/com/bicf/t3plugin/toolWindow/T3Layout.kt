package com.bicf.t3plugin.toolWindow

/**
 * The layout of the T3 web UI in the IDE, with "Open conversations in editor tabs" (verified with T3 0.1.9).
 *
 * Rules:
 * - [Mode.NAVIGATOR], the "T3 Code" tool window: only the T3 sidebar (projects, threads, search, settings), at the full
 *   width of the tool window. The page is [T3Routes.NAVIGATOR_PATH], where T3 shows no conversation. T3 shows a narrow
 *   page with sheets: the sidebar under 768 px, the right panel under 981 px. In the tool windows the script answers
 *   `false` to the media queries `(max-width: 767px)` and `(max-width: 980px)` of T3, so T3 shows the desktop layout.
 * - [Mode.CONVERSATION], an editor tab: no left sidebar, no right panel ("Workspace panel") and no "Thread details"
 *   card. A click on "Toggle right panel" goes to the plugin ([PANEL_TOGGLE]), which shows the right panel in the
 *   "T3 Workspace" tool window. When T3 opens its right panel in the tab (a keyboard shortcut, an agent), the script
 *   sends [PANEL_OPENED] and closes the panel of the tab: the plugin shows the panel in the tool window, which reloads
 *   to read the new state. A panel that is open when the page loads (state saved by the tool window) closes without a
 *   message. A click on "Toggle thread details panel" goes to the plugin ([DETAILS_TOGGLE]), which shows the card in
 *   the "T3 Details" tool window. When T3 shows the card in the tab (the T3 default, a keyboard shortcut), the script
 *   closes it without a message.
 * - [Mode.PANEL], the "T3 Workspace" tool window: only the right panel of the thread, at the full width. The script
 *   opens the panel when it is closed. T3 keeps the panel state per thread in `localStorage`, so the tab and the tool
 *   window show the same surfaces after a page load.
 * - [Mode.DETAILS], the "T3 Details" tool window: only the "Thread details" card of the thread, at the full width and
 *   height. The script opens the card when it is closed. T3 shows the card inline (`aside[data-workspace-area=details]`)
 *   when the page is 1000 px wide or more, otherwise in a popover: the style works for both. When an item of the card
 *   opens the right panel of T3, the script sends [PANEL_OPENED] and closes the panel, as in an editor tab.
 * - The style has the id `t3-ide-layout`. The scripts run when the page starts to load and again after the load.
 */
internal object T3Layout {

    enum class Mode { NAVIGATOR, CONVERSATION, PANEL, DETAILS }

    const val PREFIX = "layout:"
    const val PANEL_TOGGLE = "panel-toggle"
    const val PANEL_OPENED = "panel-opened"
    const val DETAILS_TOGGLE = "details-toggle"

    // T3 0.1.9: under 768 px the sidebar is a sheet; under 981 px the right panel is a sheet.
    private val NARROW_QUERIES = listOf("(max-width: 767px)", "(max-width: 980px)")
    // The label is "Workspace panel" without a surface and "Workspace panel: <surface>" with a surface.
    private const val PANEL = "[aria-label^=\"Workspace panel\"]"
    private const val RIGHT_TOGGLE = "button[aria-label=\"Toggle right panel\"]"
    // T3 0.1.9: the "Thread details" card is in an element with `data-thread-details-panel="inline"` (the aside) or
    // `data-thread-details-panel="popover"` (in the popup of a popover).
    private const val DETAILS = "[data-thread-details-panel]"
    private const val DETAILS_POPOVER = "[data-thread-details-panel=popover]"
    private const val DETAILS_BUTTON = "button[aria-label=\"Toggle thread details panel\"]"
    // The ancestors of the card in the "T3 Details" tool window: no transform, so the fixed card covers the page.
    private const val DETAILS_CHAIN = "data-t3plugin-details-chain"

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
                "button[aria-label=\"Maximize panel\"],button[aria-label=\"Restore panel size\"]{display:none!important}" +
                "$DETAILS,:has(> $DETAILS_POPOVER){display:none!important}"
        Mode.PANEL ->
            "[data-slot=sidebar]{display:none!important}" +
                "[data-slot=sidebar-inset]:not(:has($PANEL)){visibility:hidden!important}" +
                "div:has(> $PANEL) > :not($PANEL){display:none!important}" +
                "$PANEL{width:100%!important;max-width:none!important;flex:1 1 auto!important;border-left-width:0!important}" +
                "$PANEL > div > div{width:100%!important;flex:1 1 auto!important}"
        Mode.DETAILS ->
            // Only the card is visible; the elements in the card keep their own visibility.
            "body *:not($DETAILS):not($DETAILS *){visibility:hidden}" +
                "$DETAILS{visibility:visible!important;position:fixed!important;inset:0!important;width:auto!important;" +
                "max-width:none!important;max-height:none!important;margin:0!important;z-index:2147483646!important;" +
                "display:flex!important;flex-direction:column!important}" +
                "$DETAILS > [data-thread-details-card]{flex:1 1 auto!important;height:100%!important;max-height:none!important;" +
                "border-radius:0!important;border-width:0!important}" +
                "[$DETAILS_CHAIN]{transform:none!important;filter:none!important;backdrop-filter:none!important;" +
                "contain:none!important;perspective:none!important;will-change:auto!important}"
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
            (function(){if(window.__t3Layout)return;window.__t3Layout=true;var detailsAt=0;
            window.addEventListener('click',function(e){if(!e.isTrusted||!e.target||!e.target.closest)return;var m;
              if(e.target.closest('$RIGHT_TOGGLE'))m='$PREFIX$PANEL_TOGGLE';
              else if(e.target.closest('$DETAILS_BUTTON'))m='$PREFIX$DETAILS_TOGGLE';
              else return;
              e.preventDefault();e.stopImmediatePropagation();${report("m")};},true);
            // The popover trigger of the card can open on a mouse press: the press never reaches T3.
            ['pointerdown','mousedown'].forEach(function(t){window.addEventListener(t,function(e){
              if(e.isTrusted&&e.target&&e.target.closest&&e.target.closest('$DETAILS_BUTTON')){e.preventDefault();e.stopImmediatePropagation();}},true);});
            ${panelWatch(report)}
            setInterval(function(){if(!document.querySelector('$DETAILS'))return;var b=document.querySelector('$DETAILS_BUTTON');
              if(!b||Date.now()-detailsAt<1000)return;detailsAt=Date.now();try{b.click();}catch(x){}},250);})();
        """.trimIndent()
        Mode.PANEL -> """
            (function(){if(window.__t3Layout)return;window.__t3Layout=true;
            setInterval(function(){if(document.querySelector('$PANEL'))return;var b=document.querySelector('$RIGHT_TOGGLE');
              if(b&&b.getAttribute('aria-pressed')!=='true'){try{b.click();}catch(x){}}},300);})();
        """.trimIndent()
        Mode.DETAILS -> """
            (function(){if(window.__t3Layout)return;window.__t3Layout=true;var openAt=0;
            ${panelWatch(report)}
            setInterval(function(){var p=document.querySelector('$DETAILS');
              if(p){for(var e=p.parentElement;e&&e!==document.body;e=e.parentElement){
                if(!e.hasAttribute('$DETAILS_CHAIN'))e.setAttribute('$DETAILS_CHAIN','');}return;}
              var b=document.querySelector('$DETAILS_BUTTON');if(!b||Date.now()-openAt<1000)return;
              openAt=Date.now();try{b.click();}catch(x){}},300);})();
        """.trimIndent()
    }

    /**
     * Closes the right panel of T3 when it opens. After the first check, an open panel sends [PANEL_OPENED]: the plugin
     * shows the panel in the "T3 Workspace" tool window. A panel that is open at the first check closes without a message.
     */
    private fun panelWatch(report: (String) -> String): String = """
        (function(){var busy=false,seen=false;
        setInterval(function(){var b=document.querySelector('$RIGHT_TOGGLE');if(!b)return;
          var open=b.getAttribute('aria-pressed')==='true',first=!seen;seen=true;
          if(!open){busy=false;return;}if(busy)return;busy=true;
          if(!first){var m='$PREFIX$PANEL_OPENED';${report("m")};}
          try{b.click();}catch(x){}},250);})();
    """.trimIndent()
}
