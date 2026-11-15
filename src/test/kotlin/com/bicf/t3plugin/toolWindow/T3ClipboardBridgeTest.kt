package com.bicf.t3plugin.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.util.Base64

class T3ClipboardBridgeTest {

    @Test
    fun `text payload is UTF-8 base64`() {
        val payload = T3ClipboardBridge.payload(StringSelection("città 'x'\n"))
        assertEquals("text", payload.kind)
        assertEquals("città 'x'\n", String(Base64.getDecoder().decode(payload.base64), Charsets.UTF_8))
    }

    @Test
    fun `image payload is a PNG`() {
        val image = BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB)
        val payload = T3ClipboardBridge.payload(ImageSelection(image))
        assertEquals("image", payload.kind)
        val png = Base64.getDecoder().decode(payload.base64)
        assertEquals(0x89.toByte(), png[0])
        assertEquals("PNG", String(png, 1, 3, Charsets.US_ASCII))
    }

    @Test
    fun `empty clipboard gives none`() {
        assertEquals("none", T3ClipboardBridge.payload(null).kind)
    }

    @Test
    fun `reply accepts only numeric request ids`() {
        val payload = T3ClipboardBridge.Payload("text", "YQ==")
        assertEquals("window.__t3PasteReply&&window.__t3PasteReply(7,'text','YQ==');", T3ClipboardBridge.replyScript("7", payload))
        assertFalse(T3ClipboardBridge.isRequestId("7);alert(1"))
        assertThrows(IllegalArgumentException::class.java) { T3ClipboardBridge.replyScript("1,2", payload) }
        assertThrows(IllegalArgumentException::class.java) {
            T3ClipboardBridge.replyScript("1", T3ClipboardBridge.Payload("text');alert(1);('", ""))
        }
    }

    @Test
    fun `script sends copy and paste requests through the report code`() {
        val script = T3ClipboardBridge.script { expression -> "REPORT($expression)" }
        assertTrue(script.contains("REPORT('copy:' + t)"))
        assertTrue(script.contains("var r=(middle?'primary:':'paste:')+id;REPORT(r)"))
        assertTrue(script.contains("window.addEventListener('paste'"))
        assertTrue(script.contains("c.readText="))
        assertTrue(script.contains("var PRIMARY=false;"))
    }

    @Test
    fun `script with a primary selection sends the page selection`() {
        val script = T3ClipboardBridge.script(primary = true) { expression -> "REPORT($expression)" }
        assertTrue(script.contains("var PRIMARY=true;"))
        assertTrue(script.contains("REPORT('selection:' + t)"))
        assertTrue(script.contains("a.type==='password'"))
    }

    @Test
    fun `message actions script marks the user message boxes`() {
        val script = T3MessageActions.script()
        assertTrue(script.contains("'[data-message-role=\"user\"]'"))
        assertTrue(script.contains("button[aria-label=\"Copy message\"]:not([data-t3plugin-button])"))
        assertTrue(script.contains("'Wrap lines'"))
        assertTrue(script.contains("'Disable line wrap'"))
        assertTrue(script.contains("NOWRAP='${T3MessageActions.NO_WRAP_ATTRIBUTE}'"))
    }

    @Test
    fun `sidebar script targets only the overlay sidebar`() {
        val script = T3SidebarResize.script()
        assertTrue(script.contains("[data-slot=\"sidebar\"][data-mobile=\"true\"]"))
        assertTrue(script.contains("'t3plugin:mobile-sidebar-width'"))
    }

    private class ImageSelection(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any = image
    }
}
