package com.asthoonlite.gui

import com.asthoonlite.config.Config
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen

object NeuInventoryOverlay {

    data class SidebarButton(val label: String, val command: String, val iconChar: String)

    private val buttons = listOf(
        SidebarButton("Wardrobe", "wardrobe", "W"),
        SidebarButton("Pets", "pets", "P"),
        SidebarButton("Storage", "storage", "S"),
        SidebarButton("Craft", "craft", "C"),
        SidebarButton("Menu", "sbmenu", "M")
    )

    fun render(graphics: GuiGraphicsExtractor, screen: InventoryScreen, mouseX: Int, mouseY: Int, leftPos: Int, topPos: Int) {
        if (!Config.neuInventoryGui) return
        val mc = Minecraft.getInstance()
        val font = mc.font

        val btnWidth = 24
        val btnHeight = 22
        val gap = 4
        val startX = leftPos - btnWidth - 6
        var startY = topPos + 10

        // Draw sidebar container background
        val totalH = buttons.size * (btnHeight + gap) + 4
        fillRoundedRect(graphics, startX - 3, startY - 3, startX + btnWidth + 3, startY + totalH, 0xEE090B10.toInt(), 4)
        outlineRoundedRect(graphics, startX - 3, startY - 3, startX + btnWidth + 3, startY + totalH, 0xFF1E90FF.toInt(), 4)

        for (btn in buttons) {
            val isHovered = mouseX in startX..(startX + btnWidth) && mouseY in startY..(startY + btnHeight)
            val bg = if (isHovered) 0xFF0D3B66.toInt() else 0xDD121B28.toInt()
            val border = if (isHovered) 0xFF00FFFF.toInt() else 0x882A3A4E.toInt()

            fillRoundedRect(graphics, startX, startY, startX + btnWidth, startY + btnHeight, bg, 3)
            outlineRoundedRect(graphics, startX, startY, startX + btnWidth, startY + btnHeight, border, 3)

            val textX = startX + (btnWidth - font.width(btn.iconChar)) / 2
            val textY = startY + (btnHeight - font.lineHeight) / 2 + 1
            graphics.text(font, btn.iconChar, textX, textY, if (isHovered) 0xFF00FFFF.toInt() else 0xFFFFFFFF.toInt())

            if (isHovered) {
                // Tooltip
                val tipX = startX - font.width(btn.label) - 10
                fillRoundedRect(graphics, tipX, startY + 2, tipX + font.width(btn.label) + 6, startY + font.lineHeight + 6, 0xF0000000.toInt(), 3)
                graphics.text(font, btn.label, tipX + 3, startY + 4, 0xFFFFFFFF.toInt())
            }

            startY += btnHeight + gap
        }

        // Equipment panel on right
        val eqX = leftPos + 180
        val eqY = topPos + 10
        val eqWidth = 24
        val eqSlots = listOf("N", "C", "B", "G")
        val eqLabels = listOf("Necklace", "Cloak", "Belt", "Gloves")

        val eqTotalH = eqSlots.size * (btnHeight + gap) + 4
        fillRoundedRect(graphics, eqX - 3, eqY - 3, eqX + eqWidth + 3, eqY + eqTotalH, 0xEE090B10.toInt(), 4)
        outlineRoundedRect(graphics, eqX - 3, eqY - 3, eqX + eqWidth + 3, eqY + eqTotalH, 0xFF00E676.toInt(), 4)

        var curEqY = eqY
        for (i in eqSlots.indices) {
            val isHovered = mouseX in eqX..(eqX + eqWidth) && mouseY in curEqY..(curEqY + btnHeight)
            val bg = if (isHovered) 0xFF004D40.toInt() else 0xDD121B28.toInt()
            val border = if (isHovered) 0xFF00E676.toInt() else 0x882A3A4E.toInt()

            fillRoundedRect(graphics, eqX, curEqY, eqX + eqWidth, curEqY + btnHeight, bg, 3)
            outlineRoundedRect(graphics, eqX, curEqY, eqX + eqWidth, curEqY + btnHeight, border, 3)

            val textX = eqX + (eqWidth - font.width(eqSlots[i])) / 2
            val textY = curEqY + (btnHeight - font.lineHeight) / 2 + 1
            graphics.text(font, eqSlots[i], textX, textY, if (isHovered) 0xFF00E676.toInt() else 0xFFAAAAAA.toInt())

            if (isHovered) {
                val tip = eqLabels[i] + " (/equipment)"
                val tipX = eqX + eqWidth + 8
                fillRoundedRect(graphics, tipX, curEqY + 2, tipX + font.width(tip) + 6, curEqY + font.lineHeight + 6, 0xF0000000.toInt(), 3)
                graphics.text(font, tip, tipX + 3, curEqY + 4, 0xFFFFFFFF.toInt())
            }

            curEqY += btnHeight + gap
        }
    }

    fun handleMouseClick(screen: InventoryScreen, mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean {
        if (!Config.neuInventoryGui) return false
        val btnWidth = 24
        val btnHeight = 22
        val gap = 4
        val startX = leftPos - btnWidth - 6
        var startY = topPos + 10

        for (btn in buttons) {
            if (mouseX >= startX && mouseX <= startX + btnWidth && mouseY >= startY && mouseY <= startY + btnHeight) {
                Minecraft.getInstance().player?.connection?.sendCommand(btn.command)
                return true
            }
            startY += btnHeight + gap
        }

        val eqX = leftPos + 180
        val eqY = topPos + 10
        val eqWidth = 24
        val eqTotalH = 4 * (btnHeight + gap)
        if (mouseX >= eqX && mouseX <= eqX + eqWidth && mouseY >= eqY && mouseY <= eqY + eqTotalH) {
            Minecraft.getInstance().player?.connection?.sendCommand("equipment")
            return true
        }

        return false
    }
}
