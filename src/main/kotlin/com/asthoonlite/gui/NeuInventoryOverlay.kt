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

    // Left side: /hub, /is, /warp dh, /bz, /ah
    private val leftButtons by lazy {
        listOf(
            SidebarButton("Hub", "hub", ItemStack(Items.COMPASS)),
            SidebarButton("Island", "is", ItemStack(Items.GRASS_BLOCK)),
            SidebarButton("Dungeon Hub", "warp dh", ItemStack(Items.IRON_SWORD)),
            SidebarButton("Bazaar", "bz", ItemStack(Items.GOLD_INGOT)),
            SidebarButton("Auction House", "ah", ItemStack(Items.EMERALD))
        )
    }

    // Right side: Equipment (Elytra), Loadouts, Wardrobe, Pets, Storage
    private val rightButtons by lazy {
        listOf(
            SidebarButton("Equipment", "equipment", ItemStack(Items.ELYTRA)),
            SidebarButton("Loadouts", "loadouts", ItemStack(Items.ARMOR_STAND)),
            SidebarButton("Wardrobe", "wardrobe", ItemStack(Items.LEATHER_CHESTPLATE)),
            SidebarButton("Pets", "pets", ItemStack(Items.BONE)),
            SidebarButton("Storage", "storage", ItemStack(Items.CHEST))
        )
    }

    fun render(graphics: GuiGraphicsExtractor, screen: InventoryScreen, mouseX: Int, mouseY: Int, leftPos: Int, topPos: Int) {
        if (!Config.neuInventoryGui) return
        val mc = Minecraft.getInstance()
        val font = mc.font

        val relMouseX = mouseX - leftPos
        val relMouseY = mouseY - topPos

        val btnWidth = 24
        val btnHeight = 22
        val gap = 4

        // 1. Render Left Sidebar
        val leftX = -btnWidth - 6
        var leftY = 10
        val leftTotalH = leftButtons.size * (btnHeight + gap) + 4

        fillRoundedRect(graphics, leftX - 3, leftY - 3, leftX + btnWidth + 3, leftY + leftTotalH, 0xEE090B10.toInt(), 4)
        outlineRoundedRect(graphics, leftX - 3, leftY - 3, leftX + btnWidth + 3, leftY + leftTotalH, 0xFF1E90FF.toInt(), 4)

        for (btn in leftButtons) {
            val isHovered = relMouseX in leftX..(leftX + btnWidth) && relMouseY in leftY..(leftY + btnHeight)
            val bg = if (isHovered) 0xFF0D3B66.toInt() else 0xDD121B28.toInt()
            val border = if (isHovered) 0xFF00FFFF.toInt() else 0x882A3A4E.toInt()

            fillRoundedRect(graphics, leftX, leftY, leftX + btnWidth, leftY + btnHeight, bg, 3)
            outlineRoundedRect(graphics, leftX, leftY, leftX + btnWidth, leftY + btnHeight, border, 3)

            val itemX = leftX + (btnWidth - 16) / 2
            val itemY = leftY + (btnHeight - 16) / 2
            graphics.fakeItem(btn.icon, itemX, itemY)

            if (isHovered) {
                val tip = "${btn.label} (/${btn.command})"
                val tipW = font.width(tip)
                val tipX = leftX - tipW - 8
                fillRoundedRect(graphics, tipX, leftY + 2, tipX + tipW + 6, leftY + font.lineHeight + 6, 0xF0000000.toInt(), 3)
                graphics.text(font, tip, tipX + 3, leftY + 4, 0xFFFFFFFF.toInt())
            }

            leftY += btnHeight + gap
        }

        // 2. Render Right Sidebar
        val rightX = 176 + 6
        var rightY = 10
        val rightTotalH = rightButtons.size * (btnHeight + gap) + 4

        fillRoundedRect(graphics, rightX - 3, rightY - 3, rightX + btnWidth + 3, rightY + rightTotalH, 0xEE090B10.toInt(), 4)
        outlineRoundedRect(graphics, rightX - 3, rightY - 3, rightX + btnWidth + 3, rightY + rightTotalH, 0xFF00E676.toInt(), 4)

        for (btn in rightButtons) {
            val isHovered = relMouseX in rightX..(rightX + btnWidth) && relMouseY in rightY..(rightY + btnHeight)
            val bg = if (isHovered) 0xFF004D40.toInt() else 0xDD121B28.toInt()
            val border = if (isHovered) 0xFF00E676.toInt() else 0x882A3A4E.toInt()

            fillRoundedRect(graphics, rightX, rightY, rightX + btnWidth, rightY + btnHeight, bg, 3)
            outlineRoundedRect(graphics, rightX, rightY, rightX + btnWidth, rightY + btnHeight, border, 3)

            val itemX = rightX + (btnWidth - 16) / 2
            val itemY = rightY + (btnHeight - 16) / 2
            graphics.fakeItem(btn.icon, itemX, itemY)

            if (isHovered) {
                val tip = "${btn.label} (/${btn.command})"
                val tipW = font.width(tip)
                val tipX = rightX + btnWidth + 8
                fillRoundedRect(graphics, tipX, rightY + 2, tipX + tipW + 6, rightY + font.lineHeight + 6, 0xF0000000.toInt(), 3)
                graphics.text(font, tip, tipX + 3, rightY + 4, 0xFFFFFFFF.toInt())
            }

            rightY += btnHeight + gap
        }
    }

    fun handleMouseClick(screen: InventoryScreen, mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean {
        if (!Config.neuInventoryGui) return false
        val relX = mouseX - leftPos
        val relY = mouseY - topPos

        val btnWidth = 24
        val btnHeight = 22
        val gap = 4

        // Check Left Buttons
        val leftX = -btnWidth - 6
        var leftY = 10
        for (btn in leftButtons) {
            if (relX >= leftX && relX <= leftX + btnWidth && relY >= leftY && relY <= leftY + btnHeight) {
                val player = Minecraft.getInstance().player ?: return true
                player.closeContainer()
                player.connection.sendCommand(btn.command)
                return true
            }
            leftY += btnHeight + gap
        }

        // Check Right Buttons
        val rightX = 176 + 6
        var rightY = 10
        for (btn in rightButtons) {
            if (relX >= rightX && relX <= rightX + btnWidth && relY >= rightY && relY <= rightY + btnHeight) {
                val player = Minecraft.getInstance().player ?: return true
                player.closeContainer()
                player.connection.sendCommand(btn.command)
                return true
            }
            rightY += btnHeight + gap
        }

        return false
    }
}
