package com.asthoonlite.gui

import com.asthoonlite.config.Config
import com.asthoonlite.qol.CommandShortcuts
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class CommandShortcutsScreen(private val parent: Screen? = null) : Screen(Component.literal("Command Shortcuts")) {

    private val panelW = 440
    private val panelH = 280
    private val maxVisible = 6
    private var scrollOffset = 0

    private var triggerBox: EditBox? = null
    private var targetBox: EditBox? = null

    override fun init() {
        val panelX = width / 2 - panelW / 2
        val panelY = height / 2 - panelH / 2
        val addY = panelY + panelH - 65

        val tBox = EditBox(font, panelX + 24, addY, 110, 18, Component.literal("Trigger"))
        tBox.setMaxLength(32)
        tBox.setHint(Component.literal("e.g. dh"))
        triggerBox = tBox
        addRenderableWidget(tBox)

        val tgtBox = EditBox(font, panelX + 144, addY, 180, 18, Component.literal("Target"))
        tgtBox.setMaxLength(120)
        tgtBox.setHint(Component.literal("e.g. warp dh"))
        targetBox = tgtBox
        addRenderableWidget(tgtBox)

        val btnAdd = Button.builder(Component.literal("+ Add")) {
            val trg = triggerBox?.value ?: ""
            val tgt = targetBox?.value ?: ""
            if (trg.isNotBlank() && tgt.isNotBlank()) {
                CommandShortcuts.addShortcut(trg, tgt)
                triggerBox?.value = ""
                targetBox?.value = ""
            }
        }.bounds(panelX + 334, addY - 1, 80, 20).build()
        addRenderableWidget(btnAdd)

        val btnDone = Button.builder(Component.literal("Done")) {
            onClose()
        }.bounds(width / 2 - 50, panelY + panelH - 30, 100, 20).build()
        addRenderableWidget(btnDone)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0xCC000000.toInt())

        val panelX = width / 2 - panelW / 2
        val panelY = height / 2 - panelH / 2

        fillRoundedRect(graphics, panelX, panelY, panelX + panelW, panelY + panelH, 0xEE090B10.toInt(), 6)
        outlineRoundedRect(graphics, panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1E90FF.toInt(), 6)

        graphics.text(font, "§b§lCommand Shortcuts", panelX + 16, panelY + 14, 0xFFFFFFFF.toInt())
        graphics.text(font, "§7Shorten any command (e.g. /dh runs /warp dh automatically)", panelX + 16, panelY + 28, 0xFFAAAAAA.toInt())
        graphics.fill(panelX + 16, panelY + 42, panelX + panelW - 16, panelY + 43, 0x551E90FF.toInt())

        val listY = panelY + 48
        val itemHeight = 22
        val shortcuts = Config.commandShortcuts.toList()
        val visible = shortcuts.drop(scrollOffset).take(maxVisible)

        if (shortcuts.isEmpty()) {
            graphics.text(font, "§7No command shortcuts configured. Add one below!", panelX + 24, listY + 20, 0xFF888888.toInt())
        }

        for ((idx, entry) in visible.withIndex()) {
            val (trigger, target) = entry
            val rowY = listY + idx * (itemHeight + 4)
            val isHovered = mouseX in (panelX + 16)..(panelX + panelW - 16) && mouseY in rowY..(rowY + itemHeight)
            val rowBg = if (isHovered) 0xAA182230.toInt() else 0x880F1622.toInt()

            fillRoundedRect(graphics, panelX + 16, rowY, panelX + panelW - 16, rowY + itemHeight, rowBg, 4)

            // Trigger and Target
            val label = "§e/$trigger §7➔ §a/$target"
            graphics.text(font, label, panelX + 24, rowY + 6, 0xFFFFFFFF.toInt())

            // Delete button on right
            val delX = panelX + panelW - 65
            val delY = rowY + 2
            val delHovered = mouseX in delX..(delX + 44) && mouseY in delY..(delY + 18)
            val delBg = if (delHovered) 0xFF881111.toInt() else 0x66441111.toInt()
            fillRoundedRect(graphics, delX, delY, delX + 44, delY + 18, delBg, 3)
            graphics.text(font, "§cDelete", delX + 6, delY + 5, 0xFFFFFFFF.toInt())
        }

        // Add section label
        val addY = panelY + panelH - 65
        graphics.text(font, "§7Shortcut Trigger:", panelX + 24, addY - 10, 0xFFAAAAAA.toInt())
        graphics.text(font, "§7Target Command:", panelX + 144, addY - 10, 0xFFAAAAAA.toInt())

        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val panelX = width / 2 - panelW / 2
        val panelY = height / 2 - panelH / 2
        val listY = panelY + 48
        val itemHeight = 22

        if (event.button() == 0) {
            val shortcuts = Config.commandShortcuts.toList()
            val visible = shortcuts.drop(scrollOffset).take(maxVisible)

            for ((idx, entry) in visible.withIndex()) {
                val rowY = listY + idx * (itemHeight + 4)
                val delX = panelX + panelW - 65
                val delY = rowY + 2
                if (mx in delX..(delX + 44) && my in delY..(delY + 18)) {
                    CommandShortcuts.removeShortcut(entry.first)
                    return true
                }
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val total = Config.commandShortcuts.size
        val maxScroll = (total - maxVisible).coerceAtLeast(0)
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }
}
