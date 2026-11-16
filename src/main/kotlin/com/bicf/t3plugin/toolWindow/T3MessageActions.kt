package com.bicf.t3plugin.toolWindow

/**
 * Adds a copy button and a line wrap button to each message box of the user in the T3 page.
 *
 * T3 0.1.9 marks a message row with `data-message-role="user"` and the text with `data-user-message-body`
 * (verified with DevTools). T3 shows its own "Copy message" button only under the box, when the mouse is on the row.
 *
 * Rules:
 * - The 2 buttons are in the box, at the top right. They show when the mouse is on the box or the focus is in it.
 *   The buttons float: the text of the first line goes around them, so they never cover the text. Their space stays
 *   reserved when they are hidden. The box of T3 has a transparent background, so a button on the text would not hide it.
 *   A float moves only the text after it: the script keeps the buttons before the text of the box.
 *   Without line wrap, the text starts under the buttons: its scroll area never goes under them.
 * - "Copy message" clicks the "Copy message" button of T3, which copies the message text as the user wrote it.
 *   Without that button, the script copies the visible text of the box. For 1.2 s the button shows a check mark.
 * - "Disable line wrap" / "Wrap lines": the box wraps long lines (the T3 default), or shows each line on one row
 *   with a horizontal scroll bar. The state stays per message until the page loads again.
 * - A message without text (only attachments) gets no buttons.
 * - The buttons use the T3 theme variables, so they follow the IDE colors (see [T3ThemeSync]).
 */
internal object T3MessageActions {

    const val ACTIONS_ATTRIBUTE = "data-t3plugin-message-actions"
    const val NO_WRAP_ATTRIBUTE = "data-t3plugin-nowrap"

    fun script(): String = """
        (function(){if(window.__t3MessageActions)return;window.__t3MessageActions=true;
        var ATTR='$ACTIONS_ATTRIBUTE',NOWRAP='$NO_WRAP_ATTRIBUTE',nowrap={};
        var css='['+ATTR+']{float:right;position:relative;z-index:1;display:flex;gap:2px;margin:-6px -6px 2px 8px;opacity:0;transition:opacity .2s;}'+
          '[data-t3plugin-bubble]:hover>['+ATTR+'],[data-t3plugin-bubble]:focus-within>['+ATTR+']{opacity:1;}'+
          '@media (pointer:coarse){['+ATTR+']{opacity:1;}}'+
          '['+ATTR+'] button{display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;padding:0;'+
          'border:0;border-radius:var(--control-radius,.5rem);background:transparent;color:var(--muted-foreground,currentColor);cursor:pointer;}'+
          '['+ATTR+'] button:hover,['+ATTR+'] button[aria-pressed=false]{background:var(--accent,rgba(128,128,128,.15));color:var(--foreground,currentColor);}'+
          '['+ATTR+'] button:focus-visible{outline:2px solid var(--ring,#3574f0);outline-offset:1px;}'+
          '['+NOWRAP+'] [data-user-message-body] .chat-markdown{white-space:nowrap;overflow-wrap:normal!important;word-break:normal!important;overflow-x:auto;}';
        function addStyle(){if(document.getElementById('t3plugin-message-actions'))return;
          var s=document.createElement('style');s.id='t3plugin-message-actions';s.textContent=css;document.head.appendChild(s);}
        var SVG='<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">';
        var ICON={copy:SVG+'<rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>',
          check:SVG+'<path d="M20 6 9 17l-5-5"/></svg>',
          wrap:SVG+'<line x1="3" x2="21" y1="6" y2="6"/><path d="M3 12h15a3 3 0 1 1 0 6h-4"/><polyline points="16 16 14 18 16 20"/><line x1="3" x2="10" y1="18" y2="18"/></svg>'};
        function label(b,l){b.setAttribute('aria-label',l);b.title=l;}
        function button(l,icon){var b=document.createElement('button');b.type='button';b.setAttribute('data-t3plugin-button','');
          label(b,l);b.innerHTML=ICON[icon];return b;}
        function rowOf(b){return b.closest('[data-message-role="user"]');}
        function wrapState(row,b){var off=!!nowrap[row.getAttribute('data-message-id')];
          if(off)row.setAttribute(NOWRAP,'');else row.removeAttribute(NOWRAP);
          b.setAttribute('aria-pressed',off?'false':'true');label(b,off?'Wrap lines':'Disable line wrap');}
        function copy(b){var row=rowOf(b);if(!row)return;
          var t3=row.querySelector('button[aria-label="Copy message"]:not([data-t3plugin-button])');
          if(t3)t3.click();
          else{var body=row.querySelector('[data-user-message-body]');if(body&&navigator.clipboard)navigator.clipboard.writeText(body.innerText);}
          b.innerHTML=ICON.check;label(b,'Copied');clearTimeout(b.__t3Timer);
          b.__t3Timer=setTimeout(function(){b.innerHTML=ICON.copy;label(b,'Copy message');},1200);}
        function attach(row){var body=row.querySelector('[data-user-message-body]');if(!body)return;
          var bubble=body.closest('.bg-message')||body.parentElement;if(!bubble||!row.contains(bubble))return;
          var bar=bubble.querySelector(':scope>['+ATTR+']');
          if(!bar){bubble.setAttribute('data-t3plugin-bubble','');
            bar=document.createElement('div');bar.setAttribute(ATTR,'');
            var c=button('Copy message','copy'),w=button('Disable line wrap','wrap');
            c.addEventListener('click',function(e){e.stopPropagation();copy(c);});
            w.addEventListener('click',function(e){e.stopPropagation();var r=rowOf(w);if(!r)return;
              var id=r.getAttribute('data-message-id');nowrap[id]=!nowrap[id];wrapState(r,w);});
            bar.appendChild(c);bar.appendChild(w);}
          if(!(bar.compareDocumentPosition(body)&Node.DOCUMENT_POSITION_FOLLOWING))bubble.insertBefore(bar,bubble.firstChild);
          wrapState(row,bar.lastChild);}
        var queued=false;
        function scan(){queued=false;addStyle();document.querySelectorAll('[data-message-role="user"]').forEach(attach);}
        function queue(){if(!queued){queued=true;requestAnimationFrame(scan);}}
        new MutationObserver(queue).observe(document.body,{childList:true,subtree:true});scan();
        })();
    """.trimIndent()
}
