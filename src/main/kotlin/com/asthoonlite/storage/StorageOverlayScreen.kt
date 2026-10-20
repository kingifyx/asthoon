package com.asthoonlite.storage

import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

class StorageOverlayScreen : Screen(Component.literal("Storage")) {

    override fun init() {
        super.init()
        StorageSystem.loadDiskCache()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Dark background tint
        graphics.fill(0, 0, width, height, 0x88000000.toInt())

        val font = this.font
        val sw = width
        val sh = height

        val slotSize = 18
        val pageWidth = 9 * slotSize + 4

        val colCount = ((sw - 40) / (pageWidth + 8)).coerceIn(1, 4)
        val overviewW = (colCount * pageWidth + (colCount - 1) * 8 + 32).coerceAtMost(sw - 16)
        val overviewX = (sw - overviewW) / 2
        val playerInvH = 4 * slotSize + 14
        val topMargin = 10
        val overviewH = (sh - playerInvH - topMargin - 18).coerceIn(160, 420)
        val overviewY = topMargin

        // Draw overview box
        fillRoundedRect(graphics, overviewX, overviewY, overviewX + overviewW, overviewY + overviewH, 0xF2090B10.toInt(), 6)
        outlineRoundedRect(graphics, overviewX, overviewY, overviewX + overviewW, overviewY + overviewH, 0xFF1E2838.toInt(), 6)

        // Title
        graphics.text(font, "§b✦ §fStorage §8(All Backpacks & Ender Chests)", overviewX + 10, overviewY + 8, 0xFFFFFFFF.toInt())

        // Search Box
        val searchBoxW = 180.coerceAtMost(overviewW - 240)
        val searchBoxX = overviewX + overviewW - searchBoxW - 12
        val searchBoxY = overviewY + 5
        val searchBoxH = 16

        val searchBorder = if (StorageSystem.searchFocused) 0xFF00E5FF.toInt() else 0xFF35445A.toInt()
        fillRoundedRect(graphics, searchBoxX, searchBoxY, searchBoxX + searchBoxW, searchBoxY + searchBoxH, 0xEE141A24.toInt(), 3)
        outlineRoundedRect(graphics, searchBoxX, searchBoxY, searchBoxX + searchBoxW, searchBoxY + searchBoxH, searchBorder, 3)

        val displayText = if (StorageSystem.searchQuery.isEmpty() && !StorageSystem.searchFocused) "§8Search items..." else StorageSystem.searchQuery
        val cursor = if (StorageSystem.searchFocused && (System.currentTimeMillis() / 500) % 2 == 0L) "_" else ""
        graphics.text(font, displayText + cursor, searchBoxX + 6, searchBoxY + 4, 0xFFFFFFFF.toInt())

        val panelX = overviewX + 8
        val panelY = overviewY + 26
        val panelW = overviewW - 24
        val panelH = overviewH - 32

        var hoveredItem: ItemStack? = null

        val allPages = (if (StorageSystem.unlockedPages.isNotEmpty()) StorageSystem.unlockedPages.sorted() else listOf(0)).map { StoragePage(it) }

        var curY = 0
        var col = 0
        var maxRowH = 0

        graphics.enableScissor(panelX, panelY, panelX + panelW, panelY + panelH)

        for (page in allPages) {
            val cached = StorageSystem.cachedPages[page.index]
            val hasData = cached != null && cached.isNotEmpty()
            val rows = if (hasData) ((cached!!.size + 8) / 9).coerceIn(1, 6) else 0
            val cardH = if (rows > 0) 16 + rows * slotSize + 4 else 34

            val cx = panelX + col * (pageWidth + 8)
            val cy = panelY + curY - StorageSystem.scroll.toInt()

            if (cardH > maxRowH) maxRowH = cardH

            if (cy + cardH >= panelY && cy <= panelY + panelH) {
                fillRoundedRect(graphics, cx, cy, cx + pageWidth, cy + cardH, 0xDD0D111A.toInt(), 4)
                outlineRoundedRect(graphics, cx, cy, cx + pageWidth, cy + cardH, 0x443E4E68.toInt(), 4)

                graphics.text(font, page.name, cx + 5, cy + 4, 0xFFFFFFFF.toInt())

                if (rows > 0) {
                    val slotCount = rows * 9
                    for (sIdx in 0 until slotCount) {
                        val scol = sIdx % 9
                        val srow = sIdx / 9
                        val sx = cx + 2 + scol * slotSize
                        val sy = cy + 16 + srow * slotSize

                        graphics.fill(sx, sy, sx + 16, sy + 16, 0x44141A24)
                        val stack = cached?.getOrNull(sIdx) ?: ItemStack.EMPTY
                        if (!stack.isEmpty) {
                            graphics.fakeItem(stack, sx, sy)
                            if (stack.count > 1) graphics.itemDecorations(font, stack, sx, sy)

                            if (StorageSystem.searchQuery.isNotBlank()) {
                                if (StorageSystem.matchesSearch(stack, StorageSystem.searchQuery)) {
                                    outlineRoundedRect(graphics, sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFAA00.toInt(), 1)
                                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x33FFAA00)
                                } else {
                                    graphics.fill(sx, sy, sx + 16, sy + 16, 0xAA000000.toInt())
                                }
                            }
                        }

                        if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16) && mouseY in panelY..(panelY + panelH)) {
                            graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF)
                            if (!stack.isEmpty) hoveredItem = stack
                        }
                    }
                } else {
                    val btnY = cy + 16
                    fillRoundedRect(graphics, cx + 4, btnY, cx + pageWidth - 4, btnY + 14, 0x441F2B3E, 2)
                    graphics.text(font, "§7Click to load", cx + (pageWidth - font.width("Click to load")) / 2, btnY + 3, 0xFF888888.toInt())
                }
            }

            col++
            if (col >= colCount) {
                curY += maxRowH + 8
                col = 0
                maxRowH = 0
            }
        }
        if (col != 0) curY += maxRowH + 8

        graphics.disableScissor()

        val totalContentH = curY
        StorageSystem.maxScroll = (totalContentH - panelH).coerceAtLeast(0).toFloat()
        StorageSystem.scroll = StorageSystem.scroll.coerceIn(0f, StorageSystem.maxScroll)

        // Scrollbar
        val barX = overviewX + overviewW - 12
        val barY = panelY
        val barW = 6
        val barH = panelH

        fillRoundedRect(graphics, barX, barY, barX + barW, barY + barH, 0x22FFFFFF, 3)
        val ratio = (panelH.toFloat() / totalContentH.coerceAtLeast(panelH).toFloat()).coerceIn(0.1f, 1.0f)
        val knobH = (barH * ratio).toInt().coerceAtLeast(16)
        val knobY = barY + ((if (StorageSystem.maxScroll > 0) StorageSystem.scroll / StorageSystem.maxScroll else 0f) * (barH - knobH)).toInt()
        fillRoundedRect(graphics, barX, knobY, barX + barW, knobY + knobH, 0x88A0B0C8.toInt(), 3)

        // Player Inventory
        val playerInvW = pageWidth
        val playerInvX = (sw - playerInvW) / 2
        val playerInvY = overviewY + overviewH + 6

        fillRoundedRect(graphics, playerInvX, playerInvY, playerInvX + playerInvW, playerInvY + playerInvH, 0xF2090B10.toInt(), 5)
        outlineRoundedRect(graphics, playerInvX, playerInvY, playerInvX + playerInvW, playerInvY + playerInvH, 0xFF1E2838.toInt(), 5)
        graphics.text(font, "§7Player Inventory", playerInvX + 5, playerInvY + 3, 0xFFAAAAAA.toInt())

        val inv = Minecraft.getInstance().player?.inventory
        if (inv != null) {
            for (i in 9 until 36) {
                val sx = playerInvX + 2 + ((i - 9) % 9) * slotSize
                val sy = playerInvY + 13 + ((i - 9) / 9) * slotSize
                graphics.fill(sx, sy, sx + 16, sy + 16, 0x44141A24)
                val stack = inv.getItem(i)
                if (!stack.isEmpty) {
                    graphics.fakeItem(stack, sx, sy)
                    if (stack.count > 1) graphics.itemDecorations(font, stack, sx, sy)
                }
                if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16)) {
                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF)
                    if (!stack.isEmpty) hoveredItem = stack
                }
            }
            for (i in 0 until 9) {
                val sx = playerInvX + 2 + i * slotSize
                val sy = playerInvY + 13 + 3 * slotSize + 4
                graphics.fill(sx, sy, sx + 16, sy + 16, 0x44141A24)
                val stack = inv.getItem(i)
                if (!stack.isEmpty) {
                    graphics.fakeItem(stack, sx, sy)
                    if (stack.count > 1) graphics.itemDecorations(font, stack, sx, sy)
                }
                if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16)) {
                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF)
                    if (!stack.isEmpty) hoveredItem = stack
                }
            }
        }

        if (hoveredItem != null) {
            graphics.setTooltipForNextFrame(font, hoveredItem, mouseX, mouseY)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val mc = Minecraft.getInstance()

        val slotSize = 18
        val pageWidth = 9 * slotSize + 4
        val colCount = ((width - 40) / (pageWidth + 8)).coerceIn(1, 4)
        val overviewW = (colCount * pageWidth + (colCount - 1) * 8 + 32).coerceAtMost(width - 16)
        val overviewX = (width - overviewW) / 2
        val searchBoxW = 180.coerceAtMost(overviewW - 240)
        val searchBoxX = overviewX + overviewW - searchBoxW - 12
        val searchBoxY = 15

        if (mx in searchBoxX..(searchBoxX + searchBoxW) && my in searchBoxY..(searchBoxY + 16)) {
            StorageSystem.searchFocused = true
            return true
        } else {
            StorageSystem.searchFocused = false
        }

        val panelX = overviewX + 8
        val panelY = 10 + 26
        val panelW = overviewW - 24
        val panelH = (height - (4 * slotSize + 14) - 28).coerceIn(160, 420) - 32

        if (mx in panelX..(panelX + panelW) && my in panelY..(panelY + panelH)) {
            var curY = 0
            var col = 0
            var maxRowH = 0
            val allPages = (if (StorageSystem.unlockedPages.isNotEmpty()) StorageSystem.unlockedPages.sorted() else listOf(0)).map { StoragePage(it) }

            for (page in allPages) {
                val cached = StorageSystem.cachedPages[page.index]
                val rows = if (cached != null && cached.isNotEmpty()) ((cached.size + 8) / 9).coerceIn(1, 6) else 0
                val cardH = if (rows > 0) 16 + rows * slotSize + 4 else 34
                val cx = panelX + col * (pageWidth + 8)
                val cy = panelY + curY - StorageSystem.scroll.toInt()

                if (cardH > maxRowH) maxRowH = cardH

                if (mx in cx..(cx + pageWidth) && my in cy..(cy + cardH)) {
                    mc.player?.connection?.sendCommand(page.command)
                    StorageSystem.inTransition = true
                    StorageSystem.transitionUntil = System.currentTimeMillis() + 800L
                    return true
                }

                col++
                if (col >= colCount) {
                    curY += maxRowH + 8
                    col = 0
                    maxRowH = 0
                }
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
        StorageSystem.scroll = (StorageSystem.scroll - (vertical * 26).toFloat()).coerceIn(0f, StorageSystem.maxScroll)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (StorageSystem.handleKeyPressed(event)) return true
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (StorageSystem.handleCharTyped(event)) return true
        return super.charTyped(event)
    }
}
