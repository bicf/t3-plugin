package com.bicf.t3plugin.toolWindow

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Connects the clipboard of the T3 page to the clipboard of the IDE.
 *
 * Reason: on Linux with Wayland, the IDE writes to the Wayland clipboard, but Chromium in JCEF runs on XWayland and
 * reads the X11 clipboard. The desktop syncs the two clipboards only for a focused X11 window, and the off-screen
 * browser never has one: a native paste in the page gives an old clipboard value. Also, JCEF rejects
 * `navigator.clipboard` writes ("Document is not focused") or never answers them.
 *
 * Rules:
 * - Copy: `navigator.clipboard.writeText` and the `text/plain` part of `navigator.clipboard.write` go to the plugin.
 *   Ctrl+C and Cut in the page also send the copied text to the plugin. The plugin writes it to the IDE clipboard.
 * - Paste: the script cancels each trusted `paste` event, asks the plugin for the IDE clipboard, then sends a new
 *   `paste` event with the IDE text or PNG image to the same element. When no handler takes that event,
 *   the script inserts the text with `insertText`. `navigator.clipboard.readText` also reads the IDE clipboard.
 * - A clipboard without text or image pastes nothing.
 * - Primary selection, only when the system has one (Linux, `primary = true`): the page has the same problem as
 *   with the clipboard (Chromium writes and reads only the X11 primary selection, verified with runIde on Wayland).
 *   - A selection in the page goes to the primary selection of the IDE 200 ms after the last change.
 *     An empty selection and a selection in a password field change nothing.
 *   - A middle-click paste (a trusted `paste` event less than 1 s after a middle-button press) gets the text of the
 *     primary selection of the IDE, not the clipboard. A primary selection without text pastes nothing.
 * - Without a primary selection (Windows, macOS), a middle-click paste keeps the native behavior.
 */
internal object T3ClipboardBridge {

    const val COPY_PREFIX = "copy:"
    const val PASTE_PREFIX = "paste:"
    const val SELECTION_PREFIX = "selection:"
    const val PRIMARY_PREFIX = "primary:"

    private val REQUEST_ID = Regex("[0-9]{1,9}")

    /** The content of the IDE clipboard for the page: `text` or `image` with base64 data, or `none`. */
    data class Payload(val kind: String, val base64: String)

    /**
     * The bridge script. [report] returns the JavaScript code that sends the value of a JavaScript expression
     * to the plugin (see `JBCefJSQuery.inject`). [primary] is true when the system has a primary selection.
     */
    fun script(primary: Boolean = false, report: (String) -> String): String = """
        (function(){if(window.__t3ClipboardBridge)return;window.__t3ClipboardBridge=true;
        var PRIMARY=$primary;
        var c=navigator.clipboard;
        if(!c){c={};try{Object.defineProperty(navigator,'clipboard',{value:c,configurable:true});}catch(e){return;}}
        var write=typeof c.write==='function'?c.write.bind(c):null;
        function send(t){t=String(t);${report("'$COPY_PREFIX' + t")};return Promise.resolve();}
        c.writeText=function(t){return send(t);};
        c.write=function(items){var i=items&&items[0];
          if(i&&i.types&&Array.prototype.indexOf.call(i.types,'text/plain')>=0){
            return i.getType('text/plain').then(function(b){return b.text();}).then(send);}
          return write?write(items):Promise.reject(new DOMException('Clipboard write is not available.','NotAllowedError'));};
        var pending={},seq=0,lastMiddle=0;
        function bytes(b){return Uint8Array.from(atob(b),function(ch){return ch.charCodeAt(0);});}
        function text(b){return new TextDecoder().decode(bytes(b));}
        function request(cb,middle){var id=++seq;pending[id]=cb;var r=(middle?'$PRIMARY_PREFIX':'$PASTE_PREFIX')+id;${report("r")};}
        window.__t3PasteReply=function(id,kind,b){var cb=pending[id];delete pending[id];if(cb)cb(kind,b);};
        c.readText=function(){return new Promise(function(ok){request(function(kind,b){ok(kind==='text'?text(b):'');});});};
        document.addEventListener('mousedown',function(e){if(e.button===1)lastMiddle=Date.now();},true);
        window.addEventListener('paste',function(e){
          if(!e.isTrusted)return;
          var middle=Date.now()-lastMiddle<1000;if(middle&&!PRIMARY)return;
          var target=e.target;e.preventDefault();e.stopImmediatePropagation();
          request(function(kind,b){
            if(kind!=='text'&&(middle||kind!=='image'))return;
            var t=target&&target.isConnected?target:document.activeElement;if(!t)return;
            if(t.focus)t.focus({preventScroll:true});
            var dt=new DataTransfer();
            if(kind==='text')dt.setData('text/plain',text(b));
            else dt.items.add(new File([bytes(b)],'image.png',{type:'image/png'}));
            var ev=new ClipboardEvent('paste',{clipboardData:dt,bubbles:true,cancelable:true,composed:true});
            if(t.dispatchEvent(ev)&&kind==='text')document.execCommand('insertText',false,text(b));
          },middle);
        },true);
        if(PRIMARY){var timer=0;
          document.addEventListener('selectionchange',function(){clearTimeout(timer);timer=setTimeout(function(){
            var a=document.activeElement;if(a&&a.tagName==='INPUT'&&a.type==='password')return;
            var t=selected();if(t){${report("'$SELECTION_PREFIX' + t")};}},200);},true);}
        var copied=null;
        function selected(){var a=document.activeElement;
          if(a&&(a.tagName==='TEXTAREA'||a.tagName==='INPUT')&&typeof a.selectionStart==='number')
            return a.value.substring(a.selectionStart,a.selectionEnd);
          var s=window.getSelection();return s?s.toString():'';}
        function before(e){if(e.isTrusted)copied=selected();}
        function after(e){if(!e.isTrusted)return;
          var d=e.clipboardData&&e.defaultPrevented?e.clipboardData.getData('text/plain'):'';if(d)copied=d;}
        function flush(){setTimeout(function(){if(copied){send(copied);}copied=null;},0);}
        ['copy','cut'].forEach(function(n){
          window.addEventListener(n,function(e){before(e);flush();},true);
          window.addEventListener(n,after,false);});
        })();
    """.trimIndent()

    /** True when [id] is a request id that the script created. */
    fun isRequestId(id: String): Boolean = REQUEST_ID.matches(id)

    /** The script that gives [payload] to the paste request [id]. [id] must pass [isRequestId]. */
    fun replyScript(id: String, payload: Payload): String {
        require(isRequestId(id)) { "Invalid paste request id" }
        require(payload.kind in setOf("text", "image", "none")) { "Invalid payload kind: ${payload.kind}" }
        // Base64 has only A-Z, a-z, 0-9, + and / and =: the string needs no escaping.
        return "window.__t3PasteReply&&window.__t3PasteReply($id,'${payload.kind}','${payload.base64}');"
    }

    /** Reads text, or else an image as PNG, from [contents]. */
    fun payload(contents: Transferable?): Payload {
        if (contents == null) return Payload("none", "")
        if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            val text = contents.getTransferData(DataFlavor.stringFlavor) as? String
            if (text != null) return Payload("text", Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
        }
        if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            val image = contents.getTransferData(DataFlavor.imageFlavor) as? Image
            if (image != null) return Payload("image", Base64.getEncoder().encodeToString(png(image)))
        }
        return Payload("none", "")
    }

    private fun png(image: Image): ByteArray {
        val buffered = image as? BufferedImage ?: BufferedImage(
            image.getWidth(null).coerceAtLeast(1), image.getHeight(null).coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB,
        ).also { it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() } }
        return ByteArrayOutputStream().use { out -> ImageIO.write(buffered, "png", out); out.toByteArray() }
    }
}
