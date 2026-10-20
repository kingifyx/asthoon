package com.asthoonlite.storage

import com.asthoonlite.config.Config
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object StorageSystem {

    private val enderChestRegex = Regex("""^Ender Chest (?:✦ )?\((\d)/(\d)\)$""")
    private val backpackRegex = Regex("""^.+Backpack (?:✦ )?\(Slot #(\d+)\)$""")

    val enderChests = HashMap<Int, List<ItemStack>>()
    val backpacks = HashMap<Int, List<ItemStack>>()

    var currentEcPage: Int? = null
    var currentBpPage: Int? = null

    fun onScreenOpenOrUpdate(screen: AbstractContainerScreen<*>) {
        val title = screen.title.string
        val ecMatch = enderChestRegex.find(title)
        if (ecMatch != null) {
            val page = ecMatch.groupValues[1].toIntOrNull() ?: 1
            currentEcPage = page
            currentBpPage = null
            cacheSlots(screen, page, isBackpack = false)
            return
        }

        val bpMatch = backpackRegex.find(title)
        if (bpMatch != null) {
            val page = bpMatch.groupValues[1].toIntOrNull() ?: 1
            currentBpPage = page
            currentEcPage = null
            cacheSlots(screen, page, isBackpack = true)
            return
        }

        if (title.equals("Storage", ignoreCase = true)) {
            currentEcPage = null
            currentBpPage = null
        }
    }

    private fun cacheSlots(screen: AbstractContainerScreen<*>, page: Int, isBackpack: Boolean) {
        val menu = screen.menu
        val upperSlotCount = menu.slots.size - 36
        if (upperSlotCount <= 0) return
        val list = ArrayList<ItemStack>(upperSlotCount)
        for (i in 0 until upperSlotCount) {
            list.add(menu.slots[i].item.copy())
        }
        if (isBackpack) {
            backpacks[page] = list
        } else {
            enderChests[page] = list
        }
    }

    fun isStorageScreen(screen: AbstractContainerScreen<*>): Boolean {
        val title = screen.title.string
        return title.matches(enderChestRegex) || title.matches(backpackRegex) || title.equals("Storage", ignoreCase = true)
    }

    fun renderStorageTabs(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int, leftPos: Int, topPos: Int) {
        if (!Config.storageOverlayEnabled || !isStorageScreen(screen)) return
        val mc = Minecraft.getInstance()
        val font = mc.font

        val tabWidth = 28
        val tabHeight = 13
        val startY = topPos - 32

        // Render EC tabs 1..9
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + 2)
            val isCurrent = currentEcPage == i
            val isHovered = mouseX in tx..(tx + tabWidth) && mouseY in startY..(startY + tabHeight)

            val bg = if (isCurrent) 0xFF0D3B66.toInt() else if (isHovered) 0xEE1E2A38.toInt() else 0xDD090B10.toInt()
            val border = if (isCurrent) 0xFF00FFFF.toInt() else if (isHovered) 0xFF1E90FF.toInt() else 0x882A3A4E.toInt()

            fillRoundedRect(graphics, tx, startY, tx + tabWidth, startY + tabHeight, bg, 2)
            outlineRoundedRect(graphics, tx, startY, tx + tabWidth, startY + tabHeight, border, 2)

            val label = "EC $i"
            val lx = tx + (tabWidth - font.width(label)) / 2
            val ly = startY + (tabHeight - font.lineHeight) / 2 + 1
            graphics.text(font, label, lx, ly, if (isCurrent) 0xFF00FFFF.toInt() else 0xFFCCCCCC.toInt())
        }

        // Render BP tabs 1..9 (and 10..18 on next line if space allows or compact)
        val bpStartY = topPos - 16
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + 2)
            val isCurrent = currentBpPage == i
            val isHovered = mouseX in tx..(tx + tabWidth) && mouseY in bpStartY..(bpStartY + tabHeight)

            val bg = if (isCurrent) 0xFF4A3B18.toInt() else if (isHovered) 0xEE2A2518.toInt() else 0xDD090B10.toInt()
            val border = if (isCurrent) 0xFFFFD700.toInt() else if (isHovered) 0xFFFFA000.toInt() else 0x883A2E1A.toInt()

            fillRoundedRect(graphics, tx, bpStartY, tx + tabWidth, bpStartY + tabHeight, bg, 2)
            outlineRoundedRect(graphics, tx, bpStartY, tx + tabWidth, bpStartY + tabHeight, border, 2)

            val label = "BP $i"
            val lx = tx + (tabWidth - font.width(label)) / 2
            val ly = bpStartY + (tabHeight - font.lineHeight) / 2 + 1
            graphics.text(font, label, lx, ly, if (isCurrent) 0xFFFFD700.toInt() else 0xFFCCCCCC.toInt())
        }
    }

    fun handleMouseClick(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean {
        if (!Config.storageOverlayEnabled || !isStorageScreen(screen)) return false
        val tabWidth = 28
        val tabHeight = 13
        val startY = topPos - 32

        // Check EC clicks
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + 2)
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= startY && mouseY <= startY + tabHeight) {
                Minecraft.getInstance().player?.connection?.sendCommand("enderchest $i")
                return true
            }
        }

        // Check BP clicks
        val bpStartY = topPos - 16
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + 2)
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= bpStartY && mouseY <= bpStartY + tabHeight) {
                Minecraft.getInstance().player?.connection?.sendCommand("backpack $i")
                return true
            }
        }

        return false
    }
}
