package com.asthoonlite.chat

import com.asthoonlite.config.Config
import com.asthoonlite.mixin.ChatComponentAccessor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.TextAlignment
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix3x2f
import org.joml.Vector2f

object ChatCopyHandler {

    class LineCollector(
        private val mouseX: Double,
        private val mouseY: Double,
        private val font: net.minecraft.client.gui.Font
    ) : ActiveTextCollector {
        private var defaultParams = ActiveTextCollector.Parameters(Matrix3x2f(), 1f, null)
        var matchedSequence: FormattedCharSequence? = null
        var matchedText: String? = null

        override fun defaultParameters(): ActiveTextCollector.Parameters = defaultParams
        override fun defaultParameters(parameters: ActiveTextCollector.Parameters) {
            defaultParams = parameters
        }

        override fun accept(
            alignment: TextAlignment,
            x: Int,
            y: Int,
            params: ActiveTextCollector.Parameters,
            sequence: FormattedCharSequence
        ) {
            val left = alignment.calculateLeft(x, font, sequence)
            val pose = params.pose()
            val inverted = pose.invert(Matrix3x2f())
            val local = inverted.transformPosition(Vector2f(mouseX.toFloat(), mouseY.toFloat()))
            val width = font.width(sequence)
            val height = 9

            if (local.x >= left && local.x <= left + width && local.y >= y && local.y <= y + height) {
                matchedSequence = sequence
                val sb = StringBuilder()
                sequence.accept { _, _, cp ->
                    sb.appendCodePoint(cp)
                    true
                }
                matchedText = sb.toString()
            }
        }

        override fun acceptScrolling(p0: Component, p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: ActiveTextCollector.Parameters) {}
    }

    fun handleRightClick(screen: ChatScreen, mouseX: Double, mouseY: Double): Boolean {
        if (!Config.chatRightClickCopy) return false

        val mc = Minecraft.getInstance()
        val chat = mc.gui.chat
        val collector = LineCollector(mouseX, mouseY, mc.font)

        chat.captureClickableText(
            collector,
            mc.window.guiScaledHeight,
            mc.gui.guiTicks,
            ChatComponent.DisplayMode.FOREGROUND
        )

        val matchedSeq = collector.matchedSequence
        var textToCopy = collector.matchedText ?: return false

        // Attempt to find original parent message for full multi-line unformatted content
        if (matchedSeq != null && chat is ChatComponentAccessor) {
            val lines = chat.trimmedMessages
            val matchingLine = lines.find { it.content() == matchedSeq }
            if (matchingLine != null) {
                val fullText = matchingLine.parent().content().string
                if (fullText.isNotBlank()) {
                    textToCopy = fullText
                }
            }
        }

        val clean = ChatFormatting.stripFormatting(textToCopy)?.trim() ?: textToCopy.trim()
        if (clean.isEmpty()) return false

        mc.keyboardHandler.clipboard = clean

        val preview = if (clean.length > 40) clean.take(37) + "..." else clean
        mc.gui.setOverlayMessage(Component.literal("§aCopied to clipboard: §f$preview"), false)
        return true
    }
}
