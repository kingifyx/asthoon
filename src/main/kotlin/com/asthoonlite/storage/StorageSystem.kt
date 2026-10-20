package com.asthoonlite.storage

import com.asthoonlite.config.Config
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

object StorageSystem {

    private val enderChestRegex = Regex("""^Ender Chest (?:✦ )?\((\d+)/(\d+)\)$""")
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

        val tabWidth = 26
        val tabHeight = 12
        val gap = 2

        var hoveredCachedItems: List<ItemStack>? = null
        var hoveredLabel: String? = null

        // Row 1: EC 1..9
        val ecStartY = topPos - 44
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + gap)
            val isCurrent = currentEcPage == i
            val hasData = enderChests.containsKey(i) && enderChests[i]?.any { !it.isEmpty } == true
            val isHovered = mouseX in tx..(tx + tabWidth) && mouseY in ecStartY..(ecStartY + tabHeight)

            val bg = if (isCurrent) 0xFF0D3B66.toInt() else if (isHovered) 0xEE1E2A38.toInt() else 0xDD090B10.toInt()
            val border = if (isCurrent) 0xFF00FFFF.toInt() else if (hasData) 0xFF3B82F6.toInt() else 0x662A3A4E.toInt()

            fillRoundedRect(graphics, tx, ecStartY, tx + tabWidth, ecStartY + tabHeight, bg, 2)
            outlineRoundedRect(graphics, tx, ecStartY, tx + tabWidth, ecStartY + tabHeight, border, 2)

            val label = "E$i"
            val lx = tx + (tabWidth - font.width(label)) / 2
            val ly = ecStartY + (tabHeight - font.lineHeight) / 2 + 1
            val textColor = if (isCurrent) 0xFF00FFFF.toInt() else if (hasData) 0xFFFFFFFF.toInt() else 0xFF888888.toInt()
            graphics.text(font, label, lx, ly, textColor)

            if (isHovered) {
                hoveredLabel = "Ender Chest #$i"
                hoveredCachedItems = enderChests[i]
            }
        }

        // Row 2: BP 1..9
        val bp1StartY = topPos - 30
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + gap)
            val isCurrent = currentBpPage == i
            val hasData = backpacks.containsKey(i) && backpacks[i]?.any { !it.isEmpty } == true
            val isHovered = mouseX in tx..(tx + tabWidth) && mouseY in bp1StartY..(bp1StartY + tabHeight)

            val bg = if (isCurrent) 0xFF4A3B18.toInt() else if (isHovered) 0xEE2A2518.toInt() else 0xDD090B10.toInt()
            val border = if (isCurrent) 0xFFFFD700.toInt() else if (hasData) 0xFFF59E0B.toInt() else 0x663A2E1A.toInt()

            fillRoundedRect(graphics, tx, bp1StartY, tx + tabWidth, bp1StartY + tabHeight, bg, 2)
            outlineRoundedRect(graphics, tx, bp1StartY, tx + tabWidth, bp1StartY + tabHeight, border, 2)

            val label = "B$i"
            val lx = tx + (tabWidth - font.width(label)) / 2
            val ly = bp1StartY + (tabHeight - font.lineHeight) / 2 + 1
            val textColor = if (isCurrent) 0xFFFFD700.toInt() else if (hasData) 0xFFFFFFFF.toInt() else 0xFF888888.toInt()
            graphics.text(font, label, lx, ly, textColor)

            if (isHovered) {
                hoveredLabel = "Backpack #$i"
                hoveredCachedItems = backpacks[i]
            }
        }

        // Row 3: BP 10..18
        val bp2StartY = topPos - 16
        for (i in 10..18) {
            val tx = leftPos + (i - 10) * (tabWidth + gap)
            val isCurrent = currentBpPage == i
            val hasData = backpacks.containsKey(i) && backpacks[i]?.any { !it.isEmpty } == true
            val isHovered = mouseX in tx..(tx + tabWidth) && mouseY in bp2StartY..(bp2StartY + tabHeight)

            val bg = if (isCurrent) 0xFF4A3B18.toInt() else if (isHovered) 0xEE2A2518.toInt() else 0xDD090B10.toInt()
            val border = if (isCurrent) 0xFFFFD700.toInt() else if (hasData) 0xFFF59E0B.toInt() else 0x663A2E1A.toInt()

            fillRoundedRect(graphics, tx, bp2StartY, tx + tabWidth, bp2StartY + tabHeight, bg, 2)
            outlineRoundedRect(graphics, tx, bp2StartY, tx + tabWidth, bp2StartY + tabHeight, border, 2)

            val label = "B$i"
            val lx = tx + (tabWidth - font.width(label)) / 2
            val ly = bp2StartY + (tabHeight - font.lineHeight) / 2 + 1
            val textColor = if (isCurrent) 0xFFFFD700.toInt() else if (hasData) 0xFFFFFFFF.toInt() else 0xFF888888.toInt()
            graphics.text(font, label, lx, ly, textColor)

            if (isHovered) {
                hoveredLabel = "Backpack #$i"
                hoveredCachedItems = backpacks[i]
            }
        }

        // Render preview tooltip popup if hovering a page with items
        if (hoveredLabel != null) {
            val items = hoveredCachedItems
            if (items != null && items.isNotEmpty()) {
                val previewCols = 9
                val previewRows = (items.size + 8) / 9
                val slotSize = 18
                val popupW = previewCols * slotSize + 8
                val popupH = previewRows * slotSize + font.lineHeight + 12
                val popupX = mouseX + 12
                val popupY = (mouseY - popupH / 2).coerceIn(10, screen.height - popupH - 10)

                fillRoundedRect(graphics, popupX, popupY, popupX + popupW, popupY + popupH, 0xF50B0E14.toInt(), 5)
                outlineRoundedRect(graphics, popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1E90FF.toInt(), 5)

                graphics.text(font, "§e$hoveredLabel", popupX + 6, popupY + 5, 0xFFFFFFFF.toInt())

                for (idx in items.indices) {
                    val stack = items[idx]
                    val col = idx % previewCols
                    val row = idx / previewCols
                    val sx = popupX + 4 + col * slotSize
                    val sy = popupY + font.lineHeight + 8 + row * slotSize

                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x44222E42)
                    if (!stack.isEmpty) {
                        graphics.fakeItem(stack, sx, sy)
                        if (stack.count > 1) {
                            graphics.itemDecorations(font, stack, sx, sy)
                        }
                    }
                }
            } else {
                val tip = "§7$hoveredLabel §8(Empty / Not Cached)"
                val tw = font.width(tip)
                val tx = mouseX + 12
                val ty = mouseY - 10
                fillRoundedRect(graphics, tx, ty, tx + tw + 8, ty + font.lineHeight + 6, 0xF0090B10.toInt(), 3)
                graphics.text(font, tip, tx + 4, ty + 4, 0xFFFFFFFF.toInt())
            }
        }
    }

    fun handleMouseClick(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean {
        if (!Config.storageOverlayEnabled || !isStorageScreen(screen)) return false
        val tabWidth = 26
        val tabHeight = 12
        val gap = 2

        // Row 1: EC 1..9
        val ecStartY = topPos - 44
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + gap)
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= ecStartY && mouseY <= ecStartY + tabHeight) {
                Minecraft.getInstance().player?.connection?.sendCommand("enderchest $i")
                return true
            }
        }

        // Row 2: BP 1..9
        val bp1StartY = topPos - 30
        for (i in 1..9) {
            val tx = leftPos + (i - 1) * (tabWidth + gap)
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= bp1StartY && mouseY <= bp1StartY + tabHeight) {
                Minecraft.getInstance().player?.connection?.sendCommand("backpack $i")
                return true
            }
        }

        // Row 3: BP 10..18
        val bp2StartY = topPos - 16
        for (i in 10..18) {
            val tx = leftPos + (i - 10) * (tabWidth + gap)
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= bp2StartY && mouseY <= bp2StartY + tabHeight) {
                Minecraft.getInstance().player?.connection?.sendCommand("backpack $i")
                return true
            }
        }

        return false
    }
}
