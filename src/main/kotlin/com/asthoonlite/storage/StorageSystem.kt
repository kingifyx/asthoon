package com.asthoonlite.storage

import com.asthoonlite.config.Config
import com.asthoonlite.qol.ItemProtection
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.*
import kotlin.collections.HashMap

data class StoragePage(val index: Int) {
    val isEnderChest: Boolean = index < 9
    val pageNum: Int = if (isEnderChest) index + 1 else index - 9 + 1
    val name: String = if (isEnderChest) "Ender Chest #$pageNum" else "Backpack #$pageNum"
    val command: String = if (isEnderChest) "enderchest $pageNum" else "backpack $pageNum"
}

object StorageSystem {

    private val enderChestRegex = Regex("""^Ender Chest (?:✦ )?\((\d+)/(\d+)\)$""")
    private val backpackRegex = Regex("""^.+Backpack (?:✦ )?\(Slot #(\d+)\)$""")

    val cachedPages = HashMap<Int, List<ItemStack>>()

    var activePageIndex: Int? = null
    var inTransition: Boolean = false
    var transitionUntil: Long = 0L

    var searchQuery: String = ""
    var searchFocused: Boolean = false

    var scroll: Float = 0f
    var maxScroll: Float = 0f
    private var isDraggingScrollbar: Boolean = false
    private var dragStartKnobOffset: Float = 0f

    // Cached layout measurements
    private var lastOverviewX = 0
    private var lastOverviewY = 0
    private var lastOverviewW = 0
    private var lastOverviewH = 0

    private var lastPanelX = 0
    private var lastPanelY = 0
    private var lastPanelW = 0
    private var lastPanelH = 0

    private var lastBarX = 0
    private var lastBarY = 0
    private var lastBarW = 0
    private var lastBarH = 0
    private var lastKnobY = 0
    private var lastKnobH = 0

    private var lastSearchX = 0
    private var lastSearchY = 0
    private var lastSearchW = 0
    private var lastSearchH = 0
    private var lastClearX = 0

    private var lastPlayerInvX = 0
    private var lastPlayerInvY = 0
    private var lastPlayerInvW = 0
    private var lastPlayerInvH = 0

    private const val SLOT_SIZE = 18
    private const val PAGE_WIDTH = 9 * SLOT_SIZE + 4 // 166px

    // Layout positions of cards for mouse hit-testing
    private class PageCardHitbox(
        val page: StoragePage,
        val cardX: Int,
        val cardY: Int,
        val cardW: Int,
        val cardH: Int,
        val rows: Int
    )

    private val cardHitboxes = ArrayList<PageCardHitbox>()

    private fun getStorageFile(): File? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val uuid = player.stringUUID ?: return null
        val dir = File(mc.gameDirectory, "config/asthoonlite")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "storage_$uuid.nbt")
    }

    fun loadDiskCache() {
        val file = getStorageFile() ?: return
        if (!file.exists()) return
        try {
            val root = NbtIo.readCompressed(file.toPath(), NbtAccounter.uncompressedQuota())
            for (i in 0 until 27) {
                val key = "${i}_inv"
                if (root.contains(key)) {
                    val encoded = root.getString(key).orElse("")
                    val inv = NBTInventory.decode(encoded)
                    if (inv != null && inv.stacks.isNotEmpty()) {
                        cachedPages[i] = inv.stacks
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveDiskCache() {
        val file = getStorageFile() ?: return
        try {
            val root = CompoundTag()
            for ((index, stacks) in cachedPages) {
                if (stacks.isNotEmpty()) {
                    val encoded = NBTInventory(stacks).encode()
                    if (encoded.isNotEmpty()) {
                        root.putString("${index}_inv", encoded)
                    }
                }
            }
            NbtIo.writeCompressed(root, file.toPath())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isStorageScreen(screen: AbstractContainerScreen<*>): Boolean {
        val title = screen.title.string
        return title.matches(enderChestRegex) || title.matches(backpackRegex) || title.equals("Storage", ignoreCase = true)
    }

    fun isOverlayActive(screen: AbstractContainerScreen<*>): Boolean {
        return Config.storageOverlayEnabled && isStorageScreen(screen)
    }

    fun isSearchFocused(): Boolean = searchFocused

    fun shouldSuppressScreenClose(newScreen: Screen?): Boolean {
        if (newScreen == null && inTransition && System.currentTimeMillis() < transitionUntil) {
            return true
        }
        return false
    }

    fun onScreenOpenOrUpdate(screen: AbstractContainerScreen<*>) {
        val title = screen.title.string
        val ecMatch = enderChestRegex.find(title)
        if (ecMatch != null) {
            val pageNum = ecMatch.groupValues[1].toIntOrNull() ?: 1
            val pageIndex = pageNum - 1
            activePageIndex = pageIndex
            inTransition = false
            cacheContainerSlots(screen, pageIndex)
            return
        }

        val bpMatch = backpackRegex.find(title)
        if (bpMatch != null) {
            val slotNum = bpMatch.groupValues[1].toIntOrNull() ?: 1
            val pageIndex = slotNum - 1 + 9
            activePageIndex = pageIndex
            inTransition = false
            cacheContainerSlots(screen, pageIndex)
            return
        }

        if (title.equals("Storage", ignoreCase = true)) {
            activePageIndex = null
            inTransition = false
            // Scan overview menu for available pages
            val menu = screen.menu
            // Row 1 (9..17) are Ender Chests 1..9
            for (i in 0 until 9) {
                val slotIdx = 9 + i
                if (slotIdx < menu.slots.size) {
                    val item = menu.slots[slotIdx].item
                    if (!item.isEmpty && !item.hoverName.string.contains("Locked", ignoreCase = true) && !item.hoverName.string.contains("Not Unlocked", ignoreCase = true)) {
                        if (!cachedPages.containsKey(i)) {
                            cachedPages[i] = emptyList()
                        }
                    }
                }
            }
            // Rows 3 and 4 (27..44) are Backpacks 1..18
            for (i in 0 until 18) {
                val slotIdx = 27 + i
                if (slotIdx < menu.slots.size) {
                    val item = menu.slots[slotIdx].item
                    if (!item.isEmpty && !item.hoverName.string.contains("Locked", ignoreCase = true) && !item.hoverName.string.contains("Empty", ignoreCase = true)) {
                        if (!cachedPages.containsKey(i + 9)) {
                            cachedPages[i + 9] = emptyList()
                        }
                    }
                }
            }
        }
    }

    private fun cacheContainerSlots(screen: AbstractContainerScreen<*>, pageIndex: Int) {
        val menu = screen.menu
        val upperSlotCount = menu.slots.size - 36
        // Hypixel Ender Chests & Backpacks have row 0 as navigation buttons; actual storage is slots 9 until upperSlotCount
        if (upperSlotCount <= 9) return
        val list = ArrayList<ItemStack>(upperSlotCount - 9)
        for (i in 9 until upperSlotCount) {
            list.add(menu.slots[i].item.copy())
        }
        cachedPages[pageIndex] = list
        saveDiskCache()
    }

    fun matchesSearch(stack: ItemStack, query: String): Boolean {
        if (query.isBlank()) return true
        if (stack.isEmpty) return false
        val q = query.trim().lowercase()
        if (stack.hoverName.string.lowercase().contains(q)) return true
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE)?.lines()
        return lore?.any { it.string.lowercase().contains(q) } == true
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Render Overlay
    // ─────────────────────────────────────────────────────────────────────────────

    fun renderOverlay(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int) {
        onScreenOpenOrUpdate(screen)
        val mc = Minecraft.getInstance()
        val font = mc.font
        val sw = screen.width
        val sh = screen.height

        // Auto load disk cache if empty
        if (cachedPages.isEmpty()) loadDiskCache()

        // 1. Calculate main layout
        val colCount = ((sw - 40) / (PAGE_WIDTH + 8)).coerceIn(1, 4)
        val overviewW = (colCount * PAGE_WIDTH + (colCount - 1) * 8 + 32).coerceAtMost(sw - 16)
        val overviewX = (sw - overviewW) / 2
        val playerInvH = 4 * SLOT_SIZE + 14 // 86px
        val topMargin = 10
        val overviewH = (sh - playerInvH - topMargin - 18).coerceIn(160, 420)
        val overviewY = topMargin

        lastOverviewX = overviewX
        lastOverviewY = overviewY
        lastOverviewW = overviewW
        lastOverviewH = overviewH

        // 2. Draw outer overview container
        fillRoundedRect(graphics, overviewX, overviewY, overviewX + overviewW, overviewY + overviewH, 0xF2090B10.toInt(), 6)
        outlineRoundedRect(graphics, overviewX, overviewY, overviewX + overviewW, overviewY + overviewH, 0xFF1E2838.toInt(), 6)

        // 3. Top Header Bar
        graphics.text(font, "§b✦ §fStorage §8(All Backpacks & Ender Chests)", overviewX + 10, overviewY + 8, 0xFFFFFFFF.toInt())

        // Search Box
        val searchBoxW = 180.coerceAtMost(overviewW - 240)
        val searchBoxX = overviewX + overviewW - searchBoxW - 12
        val searchBoxY = overviewY + 5
        val searchBoxH = 16

        lastSearchX = searchBoxX
        lastSearchY = searchBoxY
        lastSearchW = searchBoxW
        lastSearchH = searchBoxH

        val searchBorder = if (searchFocused) 0xFF00E5FF.toInt() else 0xFF35445A.toInt()
        fillRoundedRect(graphics, searchBoxX, searchBoxY, searchBoxX + searchBoxW, searchBoxY + searchBoxH, 0xEE141A24.toInt(), 3)
        outlineRoundedRect(graphics, searchBoxX, searchBoxY, searchBoxX + searchBoxW, searchBoxY + searchBoxH, searchBorder, 3)

        val displayText = if (searchQuery.isEmpty() && !searchFocused) "§8Search items..." else searchQuery
        val cursor = if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0L) "_" else ""
        graphics.text(font, displayText + cursor, searchBoxX + 6, searchBoxY + 4, 0xFFFFFFFF.toInt())

        // Clear 'X' button
        lastClearX = searchBoxX + searchBoxW - 14
        if (searchQuery.isNotEmpty()) {
            graphics.text(font, "§7×", lastClearX, searchBoxY + 4, 0xFFAAAAAA.toInt())
        }

        // 4. Scroll Panel Setup
        val panelX = overviewX + 8
        val panelY = overviewY + 26
        val panelW = overviewW - 24
        val panelH = overviewH - 32

        lastPanelX = panelX
        lastPanelY = panelY
        lastPanelW = panelW
        lastPanelH = panelH

        var hoveredItem: ItemStack? = null

        // Precompute card heights
        cardHitboxes.clear()
        val allPages = (0 until 27).map { StoragePage(it) }

        // Layout rows
        var curY = 0
        var col = 0
        var maxRowH = 0

        val rowCards = ArrayList<Pair<StoragePage, Int>>() // page to cardH

        for (page in allPages) {
            val isActive = page.index == activePageIndex
            val cached = cachedPages[page.index]
            val hasData = cached != null && cached.isNotEmpty()

            val rows = if (isActive) {
                val chestSlotCount = (screen.menu.slots.size - 36 - 9).coerceAtLeast(0)
                ((chestSlotCount + 8) / 9).coerceIn(1, 6)
            } else if (hasData) {
                ((cached!!.size + 8) / 9).coerceIn(1, 6)
            } else {
                0
            }

            val cardH = if (rows > 0) 16 + rows * SLOT_SIZE + 4 else 34
            rowCards.add(page to cardH)
            if (cardH > maxRowH) maxRowH = cardH

            col++
            if (col >= colCount) {
                for ((p, h) in rowCards) {
                    val cx = panelX + (p.index % colCount) * (PAGE_WIDTH + 8)
                    val cy = panelY + curY - scroll.toInt()
                    val pRows = if (p.index == activePageIndex) {
                        ((screen.menu.slots.size - 36 - 9 + 8) / 9).coerceIn(1, 6)
                    } else if (cachedPages[p.index]?.isNotEmpty() == true) {
                        ((cachedPages[p.index]!!.size + 8) / 9).coerceIn(1, 6)
                    } else 0
                    cardHitboxes.add(PageCardHitbox(p, cx, cy, PAGE_WIDTH, h, pRows))
                }
                curY += maxRowH + 8
                col = 0
                maxRowH = 0
                rowCards.clear()
            }
        }
        if (rowCards.isNotEmpty()) {
            for ((p, h) in rowCards) {
                val cx = panelX + (p.index % colCount) * (PAGE_WIDTH + 8)
                val cy = panelY + curY - scroll.toInt()
                val pRows = if (p.index == activePageIndex) {
                    ((screen.menu.slots.size - 36 - 9 + 8) / 9).coerceIn(1, 6)
                } else if (cachedPages[p.index]?.isNotEmpty() == true) {
                    ((cachedPages[p.index]!!.size + 8) / 9).coerceIn(1, 6)
                } else 0
                cardHitboxes.add(PageCardHitbox(p, cx, cy, PAGE_WIDTH, h, pRows))
            }
            curY += maxRowH + 8
        }

        val totalContentH = curY
        maxScroll = (totalContentH - panelH).coerceAtLeast(0).toFloat()
        scroll = scroll.coerceIn(0f, maxScroll)

        // 5. Draw Cards inside Scissor
        graphics.enableScissor(panelX, panelY, panelX + panelW, panelY + panelH)

        for (hitbox in cardHitboxes) {
            val page = hitbox.page
            val cx = hitbox.cardX
            val cy = hitbox.cardY
            val ch = hitbox.cardH
            val rows = hitbox.rows

            // Culling
            if (cy + ch < panelY || cy > panelY + panelH) continue

            val isActive = page.index == activePageIndex
            val cached = cachedPages[page.index]

            // Card background & border
            val cardBg = if (isActive) 0xEE141C2A.toInt() else 0xDD0D111A.toInt()
            val cardBorder = if (isActive) 0xFF00E5FF.toInt() else 0x443E4E68.toInt()
            fillRoundedRect(graphics, cx, cy, cx + PAGE_WIDTH, cy + ch, cardBg, 4)
            outlineRoundedRect(graphics, cx, cy, cx + PAGE_WIDTH, cy + ch, cardBorder, 4)

            // Header text
            val titleColor = if (isActive) 0xFF00E5FF.toInt() else 0xFFFFFFFF.toInt()
            val titleStr = if (isActive) "§b✦ ${page.name}" else page.name
            graphics.text(font, titleStr, cx + 5, cy + 4, titleColor)

            if (rows > 0) {
                // Render slot cells
                val slotCount = rows * 9
                for (sIdx in 0 until slotCount) {
                    val scol = sIdx % 9
                    val srow = sIdx / 9
                    val sx = cx + 2 + scol * SLOT_SIZE
                    val sy = cy + 16 + srow * SLOT_SIZE

                    // Cell background
                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x44141A24)

                    val stack = if (isActive) {
                        screen.menu.slots.getOrNull(9 + sIdx)?.item ?: ItemStack.EMPTY
                    } else {
                        cached?.getOrNull(sIdx) ?: ItemStack.EMPTY
                    }

                    if (!stack.isEmpty) {
                        graphics.fakeItem(stack, sx, sy)
                        if (stack.count > 1) {
                            graphics.itemDecorations(font, stack, sx, sy)
                        }

                        // Search filtering
                        if (searchQuery.isNotBlank()) {
                            if (matchesSearch(stack, searchQuery)) {
                                outlineRoundedRect(graphics, sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFAA00.toInt(), 1)
                                graphics.fill(sx, sy, sx + 16, sy + 16, 0x33FFAA00)
                            } else {
                                graphics.fill(sx, sy, sx + 16, sy + 16, 0xAA000000.toInt())
                            }
                        }
                    }

                    // Hover check
                    if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16) && mouseY in panelY..(panelY + panelH)) {
                        graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF)
                        if (!stack.isEmpty) {
                            hoveredItem = stack
                        }
                    }
                }
            } else {
                // Uncached page - "Click to load" button
                val btnY = cy + 16
                fillRoundedRect(graphics, cx + 4, btnY, cx + PAGE_WIDTH - 4, btnY + 14, 0x441F2B3E, 2)
                graphics.text(font, "§7Click to load", cx + (PAGE_WIDTH - font.width("Click to load")) / 2, btnY + 3, 0xFF888888.toInt())
            }
        }

        graphics.disableScissor()

        // 6. Draw Scrollbar
        val barX = overviewX + overviewW - 12
        val barY = panelY
        val barW = 6
        val barH = panelH

        lastBarX = barX
        lastBarY = barY
        lastBarW = barW
        lastBarH = barH

        fillRoundedRect(graphics, barX, barY, barX + barW, barY + barH, 0x22FFFFFF, 3)

        val ratio = (panelH.toFloat() / totalContentH.coerceAtLeast(panelH).toFloat()).coerceIn(0.1f, 1.0f)
        val knobH = (barH * ratio).toInt().coerceAtLeast(16)
        val knobY = barY + ((if (maxScroll > 0) scroll / maxScroll else 0f) * (barH - knobH)).toInt()

        lastKnobY = knobY
        lastKnobH = knobH

        val knobColor = if (isDraggingScrollbar) 0xFF00E5FF.toInt() else 0x88A0B0C8.toInt()
        fillRoundedRect(graphics, barX, knobY, barX + barW, knobY + knobH, knobColor, 3)

        // 7. Player Inventory Card at bottom
        val playerInvW = PAGE_WIDTH
        val playerInvX = (sw - playerInvW) / 2
        val playerInvY = overviewY + overviewH + 6

        lastPlayerInvX = playerInvX
        lastPlayerInvY = playerInvY
        lastPlayerInvW = playerInvW
        lastPlayerInvH = playerInvH

        fillRoundedRect(graphics, playerInvX, playerInvY, playerInvX + playerInvW, playerInvY + playerInvH, 0xF2090B10.toInt(), 5)
        outlineRoundedRect(graphics, playerInvX, playerInvY, playerInvX + playerInvW, playerInvY + playerInvH, 0xFF1E2838.toInt(), 5)
        graphics.text(font, "§7Player Inventory", playerInvX + 5, playerInvY + 3, 0xFFAAAAAA.toInt())

        // Main Inventory: 3 rows of 9 (slots 9..35)
        val player = mc.player
        val inv = player?.inventory
        if (inv != null) {
            for (i in 9 until 36) {
                val colIdx = (i - 9) % 9
                val rowIdx = (i - 9) / 9
                val sx = playerInvX + 2 + colIdx * SLOT_SIZE
                val sy = playerInvY + 13 + rowIdx * SLOT_SIZE

                graphics.fill(sx, sy, sx + 16, sy + 16, 0x44141A24)
                val stack = inv.getItem(i)
                if (!stack.isEmpty) {
                    graphics.fakeItem(stack, sx, sy)
                    if (stack.count > 1) graphics.itemDecorations(font, stack, sx, sy)

                    if (searchQuery.isNotBlank()) {
                        if (matchesSearch(stack, searchQuery)) {
                            outlineRoundedRect(graphics, sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFAA00.toInt(), 1)
                            graphics.fill(sx, sy, sx + 16, sy + 16, 0x33FFAA00)
                        } else {
                            graphics.fill(sx, sy, sx + 16, sy + 16, 0xAA000000.toInt())
                        }
                    }
                }

                if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16)) {
                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF)
                    if (!stack.isEmpty) hoveredItem = stack
                }
            }

            // Hotbar: 1 row of 9 (slots 0..8)
            for (i in 0 until 9) {
                val sx = playerInvX + 2 + i * SLOT_SIZE
                val sy = playerInvY + 13 + 3 * SLOT_SIZE + 4

                graphics.fill(sx, sy, sx + 16, sy + 16, 0x44141A24)
                val stack = inv.getItem(i)
                if (!stack.isEmpty) {
                    graphics.fakeItem(stack, sx, sy)
                    if (stack.count > 1) graphics.itemDecorations(font, stack, sx, sy)

                    if (searchQuery.isNotBlank()) {
                        if (matchesSearch(stack, searchQuery)) {
                            outlineRoundedRect(graphics, sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFAA00.toInt(), 1)
                            graphics.fill(sx, sy, sx + 16, sy + 16, 0x33FFAA00)
                        } else {
                            graphics.fill(sx, sy, sx + 16, sy + 16, 0xAA000000.toInt())
                        }
                    }
                }

                if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16)) {
                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF)
                    if (!stack.isEmpty) hoveredItem = stack
                }
            }
        }

        // 8. Carried Item
        val carried = screen.menu.carried
        if (!carried.isEmpty) {
            graphics.fakeItem(carried, mouseX - 8, mouseY - 8)
            if (carried.count > 1) graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8)
        }

        // 9. Tooltip for hovered item
        if (hoveredItem != null && carried.isEmpty) {
            graphics.setTooltipForNextFrame(font, hoveredItem, mouseX, mouseY)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Mouse and Keyboard Handling
    // ─────────────────────────────────────────────────────────────────────────────

    fun handleMouseClick(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double, button: Int, doubleClick: Boolean): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        val mc = Minecraft.getInstance()

        // 1. Search Box Click
        if (mx in lastSearchX..(lastSearchX + lastSearchW) && my in lastSearchY..(lastSearchY + lastSearchH)) {
            if (searchQuery.isNotEmpty() && mx >= lastClearX - 2) {
                searchQuery = ""
            } else {
                searchFocused = true
            }
            return true
        } else {
            searchFocused = false
        }

        // 2. Scrollbar Click
        if (mx in (lastBarX - 4)..(lastBarX + lastBarW + 4) && my in lastBarY..(lastBarY + lastBarH)) {
            isDraggingScrollbar = true
            val percentage = ((my - lastBarY).toFloat() / lastBarH.toFloat()).coerceIn(0f, 1f)
            scroll = (maxScroll * percentage).coerceIn(0f, maxScroll)
            return true
        }

        // 3. Scroll Panel Hit Testing (Cards and Slots)
        if (mx in lastPanelX..(lastPanelX + lastPanelW) && my in lastPanelY..(lastPanelY + lastPanelH)) {
            for (hitbox in cardHitboxes) {
                val cx = hitbox.cardX
                val cy = hitbox.cardY
                val cw = hitbox.cardW
                val ch = hitbox.cardH
                val page = hitbox.page
                val rows = hitbox.rows

                if (mx in cx..(cx + cw) && my in cy..(cy + ch)) {
                    val isActive = page.index == activePageIndex

                    // If card is NOT active, click anywhere to open it!
                    if (!isActive) {
                        mc.player?.connection?.sendCommand(page.command)
                        inTransition = true
                        transitionUntil = System.currentTimeMillis() + 800L
                        return true
                    }

                    // Card IS active -> Check if slot was clicked
                    if (rows > 0 && my >= cy + 16) {
                        val scol = (mx - (cx + 2)) / SLOT_SIZE
                        val srow = (my - (cy + 16)) / SLOT_SIZE
                        if (scol in 0..8 && srow in 0 until rows) {
                            val slotIdx = srow * 9 + scol
                            val menuSlot = screen.menu.slots.getOrNull(9 + slotIdx)
                            val player = mc.player
                            val gameMode = mc.gameMode
                            if (menuSlot != null && gameMode != null && player != null) {
                                val isShift = InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                                        InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_RIGHT_SHIFT)
                                val clickType = if (isShift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
                                gameMode.handleContainerInput(screen.menu.containerId, menuSlot.index, button, clickType, player)
                                return true
                            }
                        }
                    }
                    return true
                }
            }
            return true
        }

        // 4. Player Inventory Click
        if (mx in lastPlayerInvX..(lastPlayerInvX + lastPlayerInvW) && my in lastPlayerInvY..(lastPlayerInvY + lastPlayerInvH)) {
            val player = mc.player
            if (player != null && mc.gameMode != null) {
                val isShift = InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                        InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_RIGHT_SHIFT)
                val clickType = if (isShift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP

                // Main inventory slots (9..35)
                for (i in 9 until 36) {
                    val scol = (i - 9) % 9
                    val srow = (i - 9) / 9
                    val sx = lastPlayerInvX + 2 + scol * SLOT_SIZE
                    val sy = lastPlayerInvY + 13 + srow * SLOT_SIZE
                    if (mx in sx..(sx + 16) && my in sy..(sy + 16)) {
                        val slot = screen.menu.slots.find { it.container is Inventory && it.containerSlot == i }
                        if (slot != null) {
                            mc.gameMode!!.handleContainerInput(screen.menu.containerId, slot.index, button, clickType, player)
                            return true
                        }
                    }
                }

                // Hotbar slots (0..8)
                for (i in 0 until 9) {
                    val sx = lastPlayerInvX + 2 + i * SLOT_SIZE
                    val sy = lastPlayerInvY + 13 + 3 * SLOT_SIZE + 4
                    if (mx in sx..(sx + 16) && my in sy..(sy + 16)) {
                        val slot = screen.menu.slots.find { it.container is Inventory && it.containerSlot == i }
                        if (slot != null) {
                            mc.gameMode!!.handleContainerInput(screen.menu.containerId, slot.index, button, clickType, player)
                            return true
                        }
                    }
                }
            }
            return true
        }

        // If clicking outside the overview bounds, return true to consume (or false)
        return true
    }

    fun handleMouseRelease(): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return false
    }

    fun handleMouseDrag(mouseX: Double, mouseY: Double, dx: Double, dy: Double): Boolean {
        if (isDraggingScrollbar && lastBarH > 0) {
            val percentage = ((mouseY - lastBarY).toFloat() / lastBarH.toFloat()).coerceIn(0f, 1f)
            scroll = (maxScroll * percentage).coerceIn(0f, maxScroll)
            return true
        }
        return false
    }

    fun handleMouseScroll(vertical: Double): Boolean {
        scroll = (scroll - (vertical * 26).toFloat()).coerceIn(0f, maxScroll)
        return true
    }

    fun handleKeyPressed(event: KeyEvent): Boolean {
        val mc = Minecraft.getInstance()
        if (searchFocused) {
            when (event.key) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    searchFocused = false
                    return true
                }
                GLFW.GLFW_KEY_V -> {
                    if ((event.modifiers and GLFW.GLFW_MOD_CONTROL) != 0) {
                        val clip = mc.keyboardHandler.clipboard
                        if (!clip.isNullOrBlank()) searchQuery += clip.trim()
                        return true
                    }
                }
            }
            // Consume all other keys while typing in search box
            return true
        }

        // If search is NOT focused:
        if (event.key == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event)) {
            inTransition = false
            mc.player?.closeContainer()
            return true
        }

        return false
    }

    fun handleCharTyped(event: CharacterEvent): Boolean {
        if (searchFocused) {
            if (event.isAllowedChatCharacter) {
                searchQuery += event.codepointAsString()
                return true
            }
        }
        return false
    }
}
