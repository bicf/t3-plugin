package com.bicf.t3plugin.toolWindow

/**
 * Makes the left sidebar (projects and threads) of the T3 web UI resizable in a narrow tool window.
 *
 * T3 resizes its sidebar only when the page is at least 768 px wide. A narrower page (the usual tool window)
 * shows the sidebar as an overlay with a fixed width of almost the full page. The script adds a drag handle
 * to the right edge of that overlay.
 *
 * Rules:
 * - The handle is 6 px wide and shows the focus color under the mouse.
 * - Drag the handle to set the width, from [MIN_WIDTH] px to the page width minus 16 px.
 * - The width stays in localStorage under [STORAGE_KEY] and applies each time the overlay opens.
 * - A double-click on the handle restores the T3 width.
 * - A page of 768 px or more keeps the T3 sidebar and its own resize handle.
 */
internal object T3SidebarResize {

    const val STORAGE_KEY = "t3plugin:mobile-sidebar-width"
    const val MIN_WIDTH = 200

    fun script(): String = """
        (function(){if(window.__t3SidebarResize)return;window.__t3SidebarResize=true;
        var KEY='$STORAGE_KEY',MIN=$MIN_WIDTH;
        function saved(){try{var v=parseInt(localStorage.getItem(KEY),10);return v>0?v:null;}catch(e){return null;}}
        function clamp(w){return Math.round(Math.max(MIN,Math.min(w,window.innerWidth-16)));}
        function apply(el,w){el.style.setProperty('--sidebar-width',w+'px');el.style.width=w+'px';el.style.maxWidth='none';}
        function attach(el){if(el.__t3Resize)return;el.__t3Resize=true;
          var orig={v:el.style.getPropertyValue('--sidebar-width'),w:el.style.width,m:el.style.maxWidth};
          var w=saved();if(w)apply(el,clamp(w));
          if(getComputedStyle(el).position==='static')el.style.position='relative';
          var h=document.createElement('div');h.setAttribute('data-t3-sidebar-resize','');
          h.title='Drag to resize the sidebar. Double-click to restore the width.';
          h.style.cssText='position:absolute;top:0;right:0;width:6px;height:100%;cursor:col-resize;z-index:2147483647;touch-action:none;';
          h.addEventListener('pointerdown',function(e){if(e.button!==0)return;e.preventDefault();e.stopPropagation();
            h.setPointerCapture(e.pointerId);var left=el.getBoundingClientRect().left;
            function move(ev){ev.preventDefault();apply(el,clamp(ev.clientX-left));}
            function up(){h.removeEventListener('pointermove',move);h.removeEventListener('pointerup',up);
              h.removeEventListener('pointercancel',up);
              try{localStorage.setItem(KEY,String(Math.round(el.getBoundingClientRect().width)));}catch(x){}}
            h.addEventListener('pointermove',move);h.addEventListener('pointerup',up);h.addEventListener('pointercancel',up);});
          h.addEventListener('pointerenter',function(){h.style.background='color-mix(in srgb, var(--app-theme-focus, #3574f0) 60%, transparent)';});
          h.addEventListener('pointerleave',function(){h.style.background='';});
          h.addEventListener('click',function(e){e.stopPropagation();});
          h.addEventListener('dblclick',function(e){e.preventDefault();e.stopPropagation();
            try{localStorage.removeItem(KEY);}catch(x){}
            el.style.setProperty('--sidebar-width',orig.v);el.style.width=orig.w;el.style.maxWidth=orig.m;});
          el.appendChild(h);}
        function scan(){document.querySelectorAll('[data-slot="sidebar"][data-mobile="true"]').forEach(attach);}
        new MutationObserver(scan).observe(document.body,{childList:true,subtree:true});scan();
        })();
    """.trimIndent()
}
