package com.bicf.t3plugin.toolWindow

/**
 * Holds back the "thread visited" command of a T3 page that the user does not see.
 *
 * T3 0.1.9 (verified in the web bundle and on a T3 server): each mounted thread page runs a visit tracker. When the
 * thread changes after the last visit, the tracker sends the command `thread.visit` (an RPC request with
 * `"type":"thread.visit"` in the payload, `orchestration.dispatchCommand`): at once when a run completed, otherwise
 * within 10 s. The server then removes the unread state ("Done", "Failed") of the thread in the sidebar. The server
 * answers `{"_tag":"Exit","requestId":<id>,"exit":{"_tag":"Success","value":{"sequence":<n>}}}`.
 * For a thread without a server visit time, the tracker writes the visit only into the local state of the page, which
 * T3 saves in `localStorage` ([UI_STATE_KEY], `threadLastVisitedAtById`); a page reads that state when it loads.
 * The T3 app mounts only the thread on screen. The plugin keeps one page per editor tab and a page in "T3 Workspace":
 * without this gate, a thread in a tab in the background lost its unread state when its run completed.
 *
 * Rules:
 * - The plugin installs the gate when the page starts to load, before the T3 client creates its WebSocket (the client
 *   keeps the `send` function of the socket, see [com.bicf.t3plugin.ide.T3IdeBridge]).
 * - While the page is hidden, the gate holds the last `thread.visit` request of each thread. A newer request of the
 *   same thread replaces the held one, and the gate answers the old one with a successful `Exit` (`sequence` 0), so
 *   the T3 client does not wait for it. The gate puts the answer between 2 messages, never between 2 parts of a long
 *   message (a part starts with U+001E).
 * - While the page is hidden, a save of [UI_STATE_KEY] writes the stored visit times, unchanged: the gate holds each
 *   visit time that is newer than the stored one. An older visit time or a missing thread does not change the stored
 *   times. The other fields of the state are saved unchanged.
 * - When the page shows, the gate sends the held requests unchanged and saves the held local visit times (the newer
 *   time of the stored one and the held one). If the save fails, the gate keeps the held times for the next show.
 * - An editor tab is shown while its component is on screen (the selected tab of an editor window).
 *   The page of "T3 Workspace" never shows for the gate: the tab of the same thread sends the visit.
 * - A held request goes to its socket, or to the last socket of the page when its socket closed. The T3 client ignores
 *   the answer to a request of a closed socket. Without an open socket, the gate drops the request.
 * - When a saved value is not valid JSON, the gate saves it unchanged (a lost save is worse) and logs `local error`.
 *   When the gate cannot save the held times (no stored state, or an error), it logs `local error`.
 * - `window.__t3VisitLog` keeps the last 20 gate events (held, superseded, sent, dropped, local held, local saved,
 *   local error; never content) for diagnosis.
 */
internal object T3VisitGate {

    /** The `localStorage` key of the UI state of T3 0.1.9, with the local visit times of the threads. */
    const val UI_STATE_KEY = "t3code:ui-state:v1"

    /** The script that installs the gate (once per page) and sets the visibility of the page to [visible]. */
    fun script(visible: Boolean): String = """
        (function(){if(!window.__t3VisitGate){
        var shown=false,held={},P=WebSocket.prototype,next=P.send,last=null,LOG=window.__t3VisitLog=[];
        var S=Storage.prototype,setItem=S.setItem,getItem=S.getItem,UI='$UI_STATE_KEY',localHeld={};
        function dbg(s){if(LOG.length>=20)LOG.shift();LOG.push(s);}
        function find(o,d){if(!o||typeof o!=='object'||d>3)return null;if(o.type==='thread.visit')return o;
          for(var k in o){var r=find(o[k],d+1);if(r)return r;}return null;}
        function visitOf(t){if(typeof t!=='string'||t.indexOf('"thread.visit"')<0)return null;
          try{var m=JSON.parse(t);if(!m||m._tag!=='Request')return null;var c=find(m.payload,0);
            return c?{id:m.id,thread:String(c.threadId||'')}:null;}catch(x){return null;}}
        function deliver(ws,t){var e=new MessageEvent('message',{data:t});e.__t3gate=true;ws.dispatchEvent(e);}
        function state(ws){var s=ws.__t3gateState;if(s)return s;s=ws.__t3gateState={partial:false,queue:[]};
          ws.addEventListener('message',function(ev){if(ev.__t3gate||ev.__t3ide||typeof ev.data!=='string')return;
            if(ev.data.charCodeAt(0)===30){s.partial=true;return;}s.partial=false;
            while(s.queue.length&&!s.partial)deliver(ws,s.queue.shift());});
          return s;}
        function answer(ws,id){if(ws.readyState!==1)return;
          var t=JSON.stringify({_tag:'Exit',requestId:id,exit:{_tag:'Success',value:{sequence:0}}}),s=state(ws);
          if(s.partial)s.queue.push(t);else deliver(ws,t);}
        P.send=function(t){var ws=this;try{state(ws);}catch(x){}
          if(typeof t==='string'&&t.indexOf('"Request"')>=0)last=ws;
          if(!shown){var v=visitOf(t);if(v){var old=held[v.thread];
            if(old){try{answer(old.ws,old.id);}catch(x){}dbg('superseded');}
            held[v.thread]={ws:ws,t:t,id:v.id};dbg('held');return;}}
          return next.call(ws,t);};
        function newer(a,b){var x=Date.parse(a),y=Date.parse(b);return !isNaN(x)&&!(y>=x);}
        S.setItem=function(k,v){if(!shown&&k===UI){try{if(this===window.localStorage){
            var n=JSON.parse(v),o=JSON.parse(getItem.call(this,UI)||'null')||{},nm=n&&n.threadLastVisitedAtById,
              om=o.threadLastVisitedAtById,kept=false,keep={};
            if(nm&&typeof nm==='object'){if(!om||typeof om!=='object')om={};
              Object.keys(nm).forEach(function(id){if(!newer(nm[id],om[id]))return;
                if(newer(nm[id],localHeld[id]))localHeld[id]=nm[id];kept=true;});
              Object.keys(om).forEach(function(id){keep[id]=om[id];});
              if(JSON.stringify(keep)!==JSON.stringify(nm)){n.threadLastVisitedAtById=keep;v=JSON.stringify(n);
                if(kept)dbg('local held');}}}}catch(x){dbg('local error');}}
          return setItem.call(this,k,v);};
        function release(){var h=held;held={};Object.keys(h).forEach(function(k){var x=h[k];
            var ws=x.ws.readyState===1?x.ws:(last&&last.readyState===1?last:null);
            if(!ws){dbg('dropped');return;}try{next.call(ws,x.t);dbg('sent');}catch(e){dbg('dropped');}});
          var lh=localHeld,ids=Object.keys(lh);if(!ids.length)return;
          try{var cur=JSON.parse(getItem.call(window.localStorage,UI)||'null');
            if(!cur||typeof cur!=='object'){dbg('local error');return;}
            var m=cur.threadLastVisitedAtById;if(!m||typeof m!=='object')m=cur.threadLastVisitedAtById={};
            ids.forEach(function(id){if(newer(lh[id],m[id]))m[id]=lh[id];});
            setItem.call(window.localStorage,UI,JSON.stringify(cur));localHeld={};dbg('local saved');}
          catch(e){dbg('local error');}}
        window.__t3VisitGate=function(v){shown=v===true;if(shown)release();};}
        window.__t3VisitGate($visible);})();
    """.trimIndent()
}
