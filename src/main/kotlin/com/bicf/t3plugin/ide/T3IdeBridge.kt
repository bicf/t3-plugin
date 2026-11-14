package com.bicf.t3plugin.ide

/**
 * The script that gives T3 tools to the IDE (files, editor, diff, terminal, Git). It runs in each T3 page of the plugin.
 * The plugin runs it when the page starts to load: the T3 client keeps the `send` function of its WebSocket when it
 * creates the socket, so a later patch of `WebSocket.prototype.send` sees no RPC message.
 *
 * The script sends a [T3IdeRequest] (`ide:` + JSON) to the plugin and waits for `__t3IdeReply(id, handled)`.
 * When the plugin does not handle a request, the script lets T3 do the job: it repeats the click (an untrusted click,
 * which the script ignores) or sends the original RPC message.
 *
 * Rules (T3 0.1.9, verified in the web UI with DevTools and in the IDE sandbox):
 * - File links: a click on a link of the conversation (same origin with `data-markdown-copy`, or `file:`) opens the
 *   file in the IDE editor. Without the plugin, T3 opens the file in its "Files" panel.
 * - Editor: the RPC `shell.openInEditor` (the "Open in …" button) opens the file or directory in the IDE. The script
 *   answers the RPC with a successful `Exit`, so T3 starts no external editor. `file-manager` stays with T3.
 * - Diff: "Open diff" and a file row of a "changed files" card. The card has the turn number in the React props
 *   (`turnSummary.checkpointTurnCount` = n). The script asks the server for `orchestration.getTurnDiff` from turn n-1
 *   to turn n on the socket of T3, with its own request id (from 1 000 000 001): the T3 client ignores the answer.
 *   The IDE diff viewer shows the patch; T3 opens no "Diff" panel. Without a turn number or an answer within 15 s,
 *   T3 does the job.
 * - Panel surfaces: an item of the "Add panel surface" menu, or a button of the "Open a surface" list of the empty
 *   right panel. Browser, Pull request, Device and Side chat stay with T3.
 * - Terminal: "Toggle terminal drawer" (when the drawer is closed) and the "Terminal" panel surface open an IDE terminal
 *   in the workspace of the thread. The workspace is the `cwd` of the last `subscribeVcsStatus` request of the page.
 * - Files: the "Files" panel surface selects the workspace in the IDE Project view.
 * - Diff: the "Diff" panel surface opens the IDE diff of the working tree with `HEAD` (Git feature).
 * - Git (region "Version Control"): the branch selector opens the IDE branches popup (on `pointerdown`: the selector
 *   opens there, before the click), "Changes vs <ref>" opens the IDE diff of the working tree with <ref>,
 *   "Changes Uncommitted" (no upstream) the same diff with `HEAD`, and a button whose label starts with "Commit"
 *   opens the IDE commit.
 * - The script changes no message of T3; it only holds back the `shell.openInEditor` request that the IDE handled.
 * - `window.__t3IdeLog` keeps the last 50 bridge events (RPC tags and results, never content) for diagnosis.
 *
 * WebSocket frames: the server splits a long message into parts; a part starts with U+001E, and the last frame of a
 * message does not. The script injects its `Exit` message only between 2 messages, never between 2 parts.
 */
object T3IdeBridge {

    /** The T3 tools that the IDE takes over in a page. */
    data class Features(
        val files: Boolean,
        val diff: Boolean,
        val terminal: Boolean,
        val git: Boolean,
    ) {
        val any: Boolean get() = files || diff || terminal || git
    }

    /** The script that answers request [id]. [id] comes from a parsed [T3IdeRequest]. */
    fun replyScript(id: Long, handled: Boolean): String {
        require(id in 1..T3IdeRequest.MAX_ID) { "Invalid request id" }
        return "window.__t3IdeReply&&window.__t3IdeReply($id,$handled);"
    }

    /**
     * The bridge script. [report] returns the JavaScript code that sends the value of a JavaScript expression
     * to the plugin (see `JBCefJSQuery.inject`).
     */
    fun script(features: Features, report: (String) -> String): String = """
        (function(){if(window.__t3IdeBridge)return;window.__t3IdeBridge=true;
        var F={files:${features.files},diff:${features.diff},terminal:${features.terminal},git:${features.git}};
        var seq=0,pending={},cwd=null,rpcWs=null,rpcSeq=0,rpcWait={},LOG=window.__t3IdeLog=[];
        function dbg(s){if(LOG.length>=50)LOG.shift();LOG.push(s);}
        function ask(m,cb){var id=++seq;pending[id]=cb||function(){};m.id=id;var r='${T3IdeRequest.PREFIX}'+JSON.stringify(m);${report("r")};}
        window.__t3IdeReply=function(id,ok){var cb=pending[id];delete pending[id];dbg('reply '+id+' '+ok);if(cb)cb(ok===true);};
        var P=WebSocket.prototype,send0=P.send;
        function deliver(ws,t){var e=new MessageEvent('message',{data:t});e.__t3ide=true;ws.dispatchEvent(e);}
        function state(ws){var s=ws.__t3ideState;if(s)return s;s=ws.__t3ideState={buf:'',partial:false,queue:[]};
          ws.addEventListener('message',function(ev){if(ev.__t3ide||typeof ev.data!=='string')return;
            if(ev.data.charCodeAt(0)===30){s.partial=true;s.buf+=ev.data.slice(1);return;}
            var full=s.partial?s.buf+ev.data:ev.data;s.buf='';s.partial=false;
            try{inbound(full);}catch(x){}
            while(s.queue.length&&!s.partial)deliver(ws,s.queue.shift());});
          return s;}
        function exitOk(ws,id){var t=JSON.stringify({_tag:'Exit',requestId:id,exit:{_tag:'Success'}});var s=state(ws);
          if(s.partial)s.queue.push(t);else deliver(ws,t);}
        function inbound(text){if(!Object.keys(rpcWait).length||text.indexOf('"Exit"')<0)return;
          var m=JSON.parse(text);(Array.isArray(m)?m:[m]).forEach(function(x){
            if(!x||x._tag!=='Exit')return;var cb=rpcWait[x.requestId];if(!cb)return;delete rpcWait[x.requestId];
            cb(x.exit&&x.exit._tag==='Success'?x.exit.value:null);});}
        function rpc(tag,payload,cb){var ws=rpcWs;dbg('rpc '+tag+' ws='+(ws?ws.readyState:'none'));if(!ws||ws.readyState!==1){cb(null);return;}
          var id=1000000000+(++rpcSeq),done=false;
          function finish(v){if(done)return;done=true;delete rpcWait[id];dbg('rpc '+id+(v?' ok':' failed'));cb(v);}
          rpcWait[id]=finish;setTimeout(function(){finish(null);},15000);
          try{send0.call(ws,JSON.stringify({_tag:'Request',id:id,tag:tag,payload:payload,headers:[]}));}catch(x){finish(null);}}
        P.send=function(d){var ws=this;try{state(ws);}catch(x){}
          if(typeof d==='string'&&d.indexOf('"Request"')>=0){var m=null;try{m=JSON.parse(d);}catch(x){}
            if(m&&m._tag==='Request'){rpcWs=ws;
              if(m.payload&&m.tag==='subscribeVcsStatus'&&typeof m.payload.cwd==='string')cwd=m.payload.cwd;
              else if(m.payload&&m.tag==='shell.openInEditor'&&F.files&&typeof m.payload.cwd==='string'&&m.payload.editor!=='file-manager'){
                ask({kind:'open',path:m.payload.cwd},function(ok){if(ok)exitOk(ws,m.id);else send0.call(ws,d);});return;}}}
          return send0.call(ws,d);};
        var ROUTES=/^\/(draft|settings|pair|projects|pull-requests|usage|api)(\/|$)/;
        function filePath(a){var u;try{u=new URL(a.getAttribute('href'),location.href);}catch(x){return null;}
          var p;try{p=decodeURIComponent(u.pathname);}catch(x){return null;}
          if(u.protocol==='file:')return{path:p,hash:u.hash};
          if(u.origin!==location.origin||!a.hasAttribute('data-markdown-copy')||p==='/'||ROUTES.test(p))return null;
          return{path:p,hash:u.hash};}
        function name(el){return(el.getAttribute('aria-label')||el.innerText||el.textContent||'').replace(/\s+/g,' ').trim();}
        function turnOf(el){try{for(var k in el){if(k.indexOf('__reactFiber${'$'}')!==0)continue;
          for(var f=el[k],i=0;f&&i<25;f=f.return,i++){var p=f.memoizedProps,t=p&&p.turnSummary;
            if(t&&typeof t.checkpointTurnCount==='number')return t.checkpointTurnCount;}}}catch(x){}
          return 0;}
        function threadId(){var s=location.pathname.split('/');try{return s.length>2?decodeURIComponent(s[2]):null;}catch(x){return null;}}
        function closeMenu(){var a=document.activeElement||document.body;
          a.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true,cancelable:true}));}
        function replay(el){try{el.click();}catch(x){}}
        function surfaceMenu(m){if(!m)return false;var id=m.getAttribute('aria-labelledby'),l=id?document.getElementById(id):null;
          return(m.getAttribute('aria-label')||(l&&l.getAttribute('aria-label'))||'')==='Add panel surface';}
        function take(e,m,el,after){e.preventDefault();e.stopImmediatePropagation();
          ask(m,function(ok){if(ok){if(after)after();}else replay(el);});}
        window.addEventListener('click',function(e){if(!e.isTrusted)return;var t=e.target;if(!t||!t.closest)return;
          if(F.files){var a=t.closest('a[href]');if(a){var fp=filePath(a);if(fp){take(e,{kind:'file',path:fp.path,hash:fp.hash},a);return;}}}
          var b=t.closest('button,[role="menuitem"],[role="combobox"]');if(!b)return;var n=name(b);
          if(F.terminal&&cwd&&b.matches('button[aria-label="Toggle terminal drawer"]')&&b.getAttribute('aria-pressed')!=='true'){
            take(e,{kind:'terminal',cwd:cwd},b);return;}
          var chooser=b.tagName==='BUTTON'&&!!b.closest('[aria-label="Open a surface"]');
          if(cwd&&(chooser||(b.getAttribute('role')==='menuitem'&&surfaceMenu(b.closest('[role="menu"]'))))){
            var w=n.split(' ')[0],sm=w==='Terminal'&&F.terminal?{kind:'terminal',cwd:cwd}:w==='Files'&&F.files?{kind:'files',cwd:cwd}:
              w==='Diff'&&F.git?{kind:'branchDiff',cwd:cwd,ref:'HEAD'}:null;
            if(sm){take(e,sm,b,chooser?null:closeMenu);return;}}
          if(F.git&&cwd&&b.closest('[aria-label="Version Control"]')){
            if(b.getAttribute('role')==='combobox'){if(combo===b){combo=null;e.preventDefault();e.stopImmediatePropagation();}return;}
            var r=/^Changes (?:vs (\S+)|Uncommitted\b)/.exec(n);
            if(r){take(e,{kind:'branchDiff',cwd:cwd,ref:r[1]?r[1].replace(/…$/,''):'HEAD'},b);return;}
            if(/^Commit\b/.test(n)){take(e,{kind:'commit',cwd:cwd},b);return;}}
          if(F.diff&&b.tagName==='BUTTON'){var open=n==='Open diff';
            if(!open&&(b.hasAttribute('aria-expanded')||!/(\d+ additions?, \d+ deletions?|\+\d+ [-−]\d+)$/.test(n)))return;
            var turn=turnOf(b),th=threadId();dbg('diff click turn='+turn);if(turn<1||!th)return;
            var file=open?null:((b.innerText||'').split('\n')[0].trim()||null);
            e.preventDefault();e.stopImmediatePropagation();
            rpc('orchestration.getTurnDiff',{threadId:th,fromTurnCount:turn-1,toTurnCount:turn,ignoreWhitespace:false},function(v){
              if(!v||typeof v.diff!=='string'){replay(b);return;}
              ask({kind:'diff',patch:v.diff,fromTurn:v.fromTurnCount,toTurn:v.toTurnCount,file:file||''},function(ok){if(!ok)replay(b);});});}
        },true);
        var combo=null;
        function openCombo(c){var o={bubbles:true,cancelable:true,button:0,pointerType:'mouse'};combo=null;
          try{c.dispatchEvent(new PointerEvent('pointerdown',o));c.dispatchEvent(new MouseEvent('mousedown',o));
            c.dispatchEvent(new PointerEvent('pointerup',o));c.dispatchEvent(new MouseEvent('mouseup',o));c.click();}catch(x){}}
        window.addEventListener('pointerdown',function(e){if(!e.isTrusted||e.button!==0||!F.git||!cwd)return;
          var c=e.target&&e.target.closest?e.target.closest('[aria-label="Version Control"] [role="combobox"]'):null;if(!c)return;
          e.preventDefault();e.stopImmediatePropagation();combo=c;
          ask({kind:'branches',cwd:cwd},function(ok){if(!ok)openCombo(c);});},true);
        })();
    """.trimIndent()
}
