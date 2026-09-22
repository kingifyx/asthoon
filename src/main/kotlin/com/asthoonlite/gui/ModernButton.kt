package com.asthoonlite.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Modern styled button widget with dark slate background, cyan hover border,
 * and crisp typography.
 */
class ModernButton(
    x: Int, y: Int, w: Int, h: Int,
    text: Component,
    private val onPress: () -> Unit
) : AbstractWidget(x, y, w, h, text) {

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        onPress()
        playDownSound(Minecraft.getInstance().soundManager)
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (!visible) return
        val hovered = isHoveredOrFocused
        val bgCol = if (hovered) 0xFF1F2E45.toInt() else 0xFF141E2D.toInt()
        val borderCol = if (hovered) 0xFF38BDF8.toInt() else 0xFF24354A.toInt()
        val textCol = if (hovered) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt()

        // Background
        context.fill(x, y, x + width, y + height, bgCol)
        // 1px border
        context.fill(x, y, x + width, y + 1, borderCol)
        context.fill(x, y + height - 1, x + width, y + height, borderCol)
        context.fill(x, y, x + 1, y + height, borderCol)
        context.fill(x + width - 1, y, x + width, y + height, borderCol)

        // Centered text
        val font = Minecraft.getInstance().font
        val textW = font.width(message)
        val textX = x + (width - textW) / 2
        val textY = y + (height - 8) / 2
        context.text(font, message.string, textX, textY, textCol)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}
