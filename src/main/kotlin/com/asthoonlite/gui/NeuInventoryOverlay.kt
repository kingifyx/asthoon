package com.asthoonlite.gui

import com.asthoonlite.config.Config
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object NeuInventoryOverlay {

    data class SidebarButton(val label: String, val command: String, val icon: ItemStack)
    data class EquipmentButton(val label: String, val slotName: String, val icon: ItemStack)

    private val buttons by lazy {
        listOf(
            SidebarButton("Wardrobe", "wardrobe", ItemStack(Items.LEATHER_CHESTPLATE)),
            SidebarButton("Pets", "pets", ItemStack(Items.BONE)),
            SidebarButton("Storage", "storage", ItemStack(Items.CHEST)),
            SidebarButton("Craft", "craft", ItemStack(Items.CRAFTING_TABLE)),
            SidebarButton("Menu", "menu", ItemStack(Items.NETHER_STAR))
        )
    }

    private val equipmentButtons by lazy {
        listOf(
            EquipmentButton("Necklace", "N", ItemStack(Items.STRING)),
            EquipmentButton("Cloak", "C", ItemStack(Items.ELYTRA)),
            EquipmentButton("Belt", "B", ItemStack(Items.LEAD)),
            EquipmentButton("Gloves", "G", ItemStack(Items.LEATHER))
        )
    }

    fun render(graphics: GuiGraphicsExtractor, screen: InventoryScreen, mouseX: Int, mouseY: Int, leftPos: Int, topPos: Int) {
        if (!Config.neuInventoryGui) return
        val mc = Minecraft.getInstance()
        val font = mc.font

        // Note: extractSlots already translated pose by (leftPos, topPos)
        // so coordinates are relative to inventory top-left (0, 0)
        val relMouseX = mouseX - leftPos
        val relMouseY = mouseY - topPos

        val btnWidth = 24
        val btnHeight = 22
        val gap = 4
        val startX = -btnWidth - 6
        var startY = 10

        // Draw sidebar container background
        val totalH = buttons.size * (btnHeight + gap) + 4
        fillRoundedRect(graphics, startX - 3, startY - 3, startX + btnWidth + 3, startY + totalH, 0xEE090B10.toInt(), 4)
        outlineRoundedRect(graphics, startX - 3, startY - 3, startX + btnWidth + 3, startY + totalH, 0xFF1E90FF.toInt(), 4)

        for (btn in buttons) {
            val isHovered = relMouseX in startX..(startX + btnWidth) && relMouseY in startY..(startY + btnHeight)
            val bg = if (isHovered) 0xFF0D3B66.toInt() else 0xDD121B28.toInt()
            val border = if (isHovered) 0xFF00FFFF.toInt() else 0x882A3A4E.toInt()

            fillRoundedRect(graphics, startX, startY, startX + btnWidth, startY + btnHeight, bg, 3)
            outlineRoundedRect(graphics, startX, startY, startX + btnWidth, startY + btnHeight, border, 3)

            val itemX = startX + (btnWidth - 16) / 2
            val itemY = startY + (btnHeight - 16) / 2
            graphics.fakeItem(btn.icon, itemX, itemY)

            if (isHovered) {
                // Tooltip
                val tip = "${btn.label} (/${btn.command})"
                val tipW = font.width(tip)
                val tipX = startX - tipW - 10
                fillRoundedRect(graphics, tipX, startY + 2, tipX + tipW + 6, startY + font.lineHeight + 6, 0xF0000000.toInt(), 3)
                graphics.text(font, tip, tipX + 3, startY + 4, 0xFFFFFFFF.toInt())
            }

            startY += btnHeight + gap
        }

        // Equipment panel on right (vanilla inventory width is 176)
        val eqX = 176 + 6
        val eqY = 10
        val eqWidth = 24

        val eqTotalH = equipmentButtons.size * (btnHeight + gap) + 4
        fillRoundedRect(graphics, eqX - 3, eqY - 3, eqX + eqWidth + 3, eqY + eqTotalH, 0xEE090B10.toInt(), 4)
        outlineRoundedRect(graphics, eqX - 3, eqY - 3, eqX + eqWidth + 3, eqY + eqTotalH, 0xFF00E676.toInt(), 4)

        var curEqY = eqY
        for (eq in equipmentButtons) {
            val isHovered = relMouseX in eqX..(eqX + eqWidth) && relMouseY in curEqY..(curEqY + btnHeight)
            val bg = if (isHovered) 0xFF004D40.toInt() else 0xDD121B28.toInt()
            val border = if (isHovered) 0xFF00E676.toInt() else 0x882A3A4E.toInt()

            fillRoundedRect(graphics, eqX, curEqY, eqX + eqWidth, curEqY + btnHeight, bg, 3)
            outlineRoundedRect(graphics, eqX, curEqY, eqX + eqWidth, curEqY + btnHeight, border, 3)

            val itemX = eqX + (eqWidth - 16) / 2
            val itemY = curEqY + (btnHeight - 16) / 2
            graphics.fakeItem(eq.icon, itemX, itemY)

            if (isHovered) {
                val tip = "${eq.label} (/equipment)"
                val tipW = font.width(tip)
                val tipX = eqX + eqWidth + 8
                fillRoundedRect(graphics, tipX, curEqY + 2, tipX + tipW + 6, curEqY + font.lineHeight + 6, 0xF0000000.toInt(), 3)
                graphics.text(font, tip, tipX + 3, curEqY + 4, 0xFFFFFFFF.toInt())
            }

            curEqY += btnHeight + gap
        }
    }

    fun handleMouseClick(screen: InventoryScreen, mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean {
        if (!Config.neuInventoryGui) return false
        val relX = mouseX - leftPos
        val relY = mouseY - topPos

        val btnWidth = 24
        val btnHeight = 22
        val gap = 4
        val startX = -btnWidth - 6
        var startY = 10

        for (btn in buttons) {
            if (relX >= startX && relX <= startX + btnWidth && relY >= startY && relY <= startY + btnHeight) {
                val player = Minecraft.getInstance().player ?: return true
                player.closeContainer()
                player.connection.sendCommand(btn.command)
                return true
            }
            startY += btnHeight + gap
        }

        val eqX = 176 + 6
        val eqY = 10
        val eqWidth = 24
        val eqTotalH = equipmentButtons.size * (btnHeight + gap)
        if (relX >= eqX && relX <= eqX + eqWidth && relY >= eqY && relY <= eqY + eqTotalH) {
            val player = Minecraft.getInstance().player ?: return true
            player.closeContainer()
            player.connection.sendCommand("equipment")
            return true
        }

        return false
    }
}
