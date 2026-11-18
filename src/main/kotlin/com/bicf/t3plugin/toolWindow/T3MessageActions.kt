package com.bicf.t3plugin.toolWindow

/**
 * Adds a line wrap button and an "Edit in composer" button to the action row of each message of the user.
 *
 * T3 0.1.9 marks a message row with `data-message-role="user"` and the text with `data-user-message-body`.
 * Under the box, T3 has an action row: time, "Edit from here" (only for a turn with a checkpoint), "Copy message" and
 * "Copy prompt link". The row shows when the mouse is on the message or the focus is in it (verified in the web bundle).
 * T3 can render the row twice (on the left of the box in a wide page and under the box): each copy gets the buttons.
 *
 * Rules:
 * - The 2 buttons go at the end of the button group of T3 that holds "Copy message". They take the classes of that
 *   button, so they have the same size, colors and hover. They show and hide with the row of T3.
 * - The box itself gets no button: the text of the box is never covered.
 * - "Disable line wrap" / "Wrap lines": the box wraps long lines (the T3 default), or shows each line on one row
 *   with a horizontal scroll bar. The state stays per message until the page loads again.
 * - "Edit in composer" puts the text of the message at the end of the composer and gives the focus to the composer.
 *   The text is the text that "Copy message" copies: the `text` prop of the React component of that button (in
 *   T3 0.1.9, 9 levels above the button). The search stops at the first DOM element above the button, so it never
 *   reads the prop of another component. Without the prop, the text is the visible text of the box.
 *   Each line break becomes a hard break (Shift+Enter), as when the user types the text. A draft in the composer
 *   stays: the text goes after it, after an empty line. If the TipTap editor is missing or rejects the insert, the
 *   script types the text with `insertText`. The plugin sends nothing.
 *   T3 "Edit from here" is different: it reverts the conversation to that turn.
 * - A message without text (only attachments) has no "Copy message" button of T3, so it gets no buttons.
 */
internal object T3MessageActions {

    const val NO_WRAP_ATTRIBUTE = "data-t3plugin-nowrap"

    /** The T3 composer: a TipTap editor; its element has the editor in the `editor` property. */
    const val COMPOSER_SELECTOR = "[data-composer-rich-text=\"true\"]"

    fun script(): String = """
        (function(){if(window.__t3MessageActions)return;window.__t3MessageActions=true;
        var NOWRAP='$NO_WRAP_ATTRIBUTE',nowrap={};
        var css='[data-t3plugin-button][aria-pressed=false]{background:var(--accent,rgba(128,128,128,.15));color:var(--foreground,currentColor);}'+
          '['+NOWRAP+'] [data-user-message-body] .chat-markdown{white-space:nowrap;overflow-wrap:normal!important;word-break:normal!important;overflow-x:auto;}';
        function addStyle(){if(document.getElementById('t3plugin-message-actions'))return;
          var s=document.createElement('style');s.id='t3plugin-message-actions';s.textContent=css;document.head.appendChild(s);}
        var SVG='<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">';
        var ICON={wrap:'<line x1="3" x2="21" y1="6" y2="6"/><path d="M3 12h15a3 3 0 1 1 0 6h-4"/><polyline points="16 16 14 18 16 20"/><line x1="3" x2="10" y1="18" y2="18"/></svg>',
          edit:'<path d="M5 4h1a3 3 0 0 1 3 3 3 3 0 0 1 3-3h1"/><path d="M13 20h-1a3 3 0 0 1-3-3 3 3 0 0 1-3 3H5"/><path d="M5 16H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2h1"/><path d="M13 8h7a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-7"/><path d="M9 7v10"/></svg>'};
        function label(b,l){b.setAttribute('aria-label',l);b.title=l;}
        function button(t3,kind,l){var b=document.createElement('button');b.type='button';b.className=t3.className;
          b.setAttribute('data-t3plugin-button',kind);label(b,l);
          var svg=t3.querySelector('svg'),cls=svg?svg.getAttribute('class'):null;
          b.innerHTML=SVG+ICON[kind];if(cls)b.firstChild.setAttribute('class',cls);return b;}
        function rowOf(b){return b.closest('[data-message-role="user"]');}
        function wrapState(row){var off=!!nowrap[row.getAttribute('data-message-id')];
          if(off)row.setAttribute(NOWRAP,'');else row.removeAttribute(NOWRAP);
          row.querySelectorAll('[data-t3plugin-button="wrap"]').forEach(function(b){
            b.setAttribute('aria-pressed',off?'false':'true');label(b,off?'Wrap lines':'Disable line wrap');});}
        function propText(el){try{for(var k in el){if(k.indexOf('__reactFiber${'$'}')!==0)continue;
          for(var f=el[k],i=0;f&&i<30;f=f.return,i++){if(typeof f.type==='string'&&f.stateNode!==el)return null;
            var p=f.memoizedProps;if(p&&typeof p.text==='string')return p.text;}}}catch(x){}
          return null;}
        function messageText(row){var t3=row.querySelector('button[aria-label="Copy message"]:not([data-t3plugin-button])');
          var t=t3?propText(t3):null;if(t!=null&&t.trim())return t;
          var body=row.querySelector('[data-user-message-body]');return body?body.innerText:'';}
        function inline(text){var out=[];text.replace(/\r\n?/g,'\n').split('\n').forEach(function(l,i){
          if(i)out.push({type:'hardBreak'});if(l)out.push({type:'text',text:l});});return out;}
        function edit(b){var row=rowOf(b);if(!row)return;var text=messageText(row);if(!text||!text.trim())return;
          var el=document.querySelector('$COMPOSER_SELECTOR');if(!el)return;var ed=el.editor;
          if(ed&&ed.chain&&!ed.isDestroyed){var nodes=inline(text);
            if(!ed.isEmpty)nodes=[{type:'hardBreak'},{type:'hardBreak'}].concat(nodes);
            if(ed.chain().focus('end').insertContent(nodes).focus('end').run()!==false)return;}
          el.focus();try{var s=window.getSelection(),r=document.createRange();r.selectNodeContents(el);r.collapse(false);
            s.removeAllRanges();s.addRange(r);}catch(x){}
          document.execCommand('insertText',false,text);}
        function attach(row){if(!row.querySelector('[data-user-message-body]'))return;var added=false;
          row.querySelectorAll('button[aria-label="Copy message"]:not([data-t3plugin-button])').forEach(function(t3){
            var group=t3.parentElement;if(!group)return;
            var w=group.querySelector(':scope>[data-t3plugin-button="wrap"]'),e=group.querySelector(':scope>[data-t3plugin-button="edit"]');
            if(!w){w=button(t3,'wrap','Disable line wrap');added=true;
              w.addEventListener('click',function(ev){ev.stopPropagation();var r=rowOf(w);if(!r)return;
                var id=r.getAttribute('data-message-id');nowrap[id]=!nowrap[id];wrapState(r);});}
            if(!e){e=button(t3,'edit','Edit in composer');added=true;
              e.addEventListener('click',function(ev){ev.stopPropagation();edit(e);});}
            if(group.lastElementChild!==e||e.previousElementSibling!==w){group.appendChild(w);group.appendChild(e);}});
          if(added)wrapState(row);}
        var queued=false;
        function scan(){queued=false;addStyle();document.querySelectorAll('[data-message-role="user"]').forEach(attach);}
        function queue(){if(!queued){queued=true;requestAnimationFrame(scan);}}
        new MutationObserver(queue).observe(document.body,{childList:true,subtree:true});scan();
        })();
    """.trimIndent()
}
