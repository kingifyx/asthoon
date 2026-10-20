package com.asthoonlite.gui

import com.asthoonlite.config.Config
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class CommandKeybindsScreen(private val parent: Screen? = null) : Screen(Component.literal("Command Keybinds")) {

    private var editingKey: Int? = null
    private var isAddingNew = false
    private var listeningForNewKey = false
    private var newKey: Int? = null
    private var newCommandEditBox: EditBox? = null
    private var scrollOffset = 0

    override fun init() {
        val centerX = width / 2

        if (isAddingNew) {
            val eb = EditBox(font, centerX - 120, height - 70, 180, 20, Component.literal("Command"))
            eb.setMaxLength(100)
            eb.value = newCommandEditBox?.value ?: "/warp dungeon_hub"
            newCommandEditBox = eb
            addRenderableWidget(eb)
        }
    }

    private fun getKeyName(key: Int): String {
        return try {
            InputConstants.Type.KEYSYM.getOrCreate(key).name ?: GLFW.glfwGetKeyName(key, 0) ?: "Key $key"
        } catch (_: Throwable) {
            "Key $key"
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        // Dark translucent backdrop
        graphics.fill(0, 0, width, height, 0xCC000000.toInt())

        val panelW = 400
        val panelH = 260
        val panelX = width / 2 - panelW / 2
        val panelY = height / 2 - panelH / 2

        // Slate container panel
        fillRoundedRect(graphics, panelX, panelY, panelX + panelW, panelY + panelH, 0xEE090B10.toInt(), 6)
        outlineRoundedRect(graphics, panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1E90FF.toInt(), 6)

        // Title & Header
        val title = "§b§lCommand Keybinds"
        val subtitle = "§7Press bound keys in-game (when no GUI is open) to run commands or chat"
        graphics.text(font, title, panelX + 16, panelY + 14, 0xFFFFFFFF.toInt())
        graphics.text(font, subtitle, panelX + 16, panelY + 28, 0xFFAAAAAA.toInt())

        // Separator line
        graphics.fill(panelX + 16, panelY + 42, panelX + panelW - 16, panelY + 43, 0x551E90FF.toInt())

        // List of bindings
        val listY = panelY + 48
        val itemHeight = 24
        val maxVisible = 6

        val binds = Config.commandBinds.toList()
        val visibleBinds = binds.drop(scrollOffset).take(maxVisible)

        if (binds.isEmpty() && !isAddingNew) {
            val emptyMsg = "§7No command keybinds configured. Click §a[+ Add Bind] §7below to create one."
            graphics.text(font, emptyMsg, panelX + 24, listY + 20, 0xFF888888.toInt())
        }

        for ((idx, entry) in visibleBinds.withIndex()) {
            val (key, cmd) = entry
            val rowY = listY + idx * (itemHeight + 4)
            val isEditingThis = editingKey == key

            // Row background
            val isHovered = mouseX in (panelX + 16)..(panelX + panelW - 16) && mouseY in rowY..(rowY + itemHeight)
            val rowBg = if (isEditingThis) 0xFF1E3A5F.toInt() else if (isHovered) 0xAA182230.toInt() else 0x880F1622.toInt()
            fillRoundedRect(graphics, panelX + 16, rowY, panelX + panelW - 16, rowY + itemHeight, rowBg, 4)

            // Key button pill
            val keyLabel = if (isEditingThis) "§e[ Press Key... ]" else "§f[ ${getKeyName(key)} ]"
            val keyW = font.width(keyLabel) + 12
            val keyHovered = mouseX in (panelX + 22)..(panelX + 22 + keyW) && mouseY in (rowY + 2)..(rowY + itemHeight - 2)
            val keyPillBg = if (isEditingThis) 0xFFFFAA00.toInt() else if (keyHovered) 0xFF2A4A70.toInt() else 0xFF162536.toInt()
            fillRoundedRect(graphics, panelX + 22, rowY + 2, panelX + 22 + keyW, rowY + itemHeight - 2, keyPillBg, 3)
            graphics.text(font, keyLabel, panelX + 28, rowY + (itemHeight - font.lineHeight) / 2 + 1, 0xFFFFFFFF.toInt())

            // Command label
            val cmdX = panelX + 26 + keyW
            val trimmedCmd = font.plainSubstrByWidth(cmd, panelW - keyW - 80)
            graphics.text(font, "§b$trimmedCmd", cmdX, rowY + (itemHeight - font.lineHeight) / 2 + 1, 0xFF00FFFF.toInt())

            // Delete button [X]
            val delX = panelX + panelW - 42
            val delHovered = mouseX in delX..(delX + 20) && mouseY in (rowY + 3)..(rowY + itemHeight - 3)
            val delBg = if (delHovered) 0xFFD32F2F.toInt() else 0x88771111.toInt()
            fillRoundedRect(graphics, delX, rowY + 3, delX + 20, rowY + itemHeight - 3, delBg, 3)
            graphics.text(font, "§f✕", delX + 6, rowY + (itemHeight - font.lineHeight) / 2, 0xFFFFFFFF.toInt())
        }

        // Add New Bind UI
        if (isAddingNew) {
            val addY = panelY + panelH - 80
            fillRoundedRect(graphics, panelX + 16, addY, panelX + panelW - 16, addY + 36, 0xEE141E2B.toInt(), 4)
            outlineRoundedRect(graphics, panelX + 16, addY, panelX + panelW - 16, addY + 36, 0xFF1E90FF.toInt(), 4)

            val keyText = if (listeningForNewKey) "§e[ Press Key... ]" else if (newKey != null) "§a[ ${getKeyName(newKey!!)} ]" else "§7[ Click to Set Key ]"
            val keyW = font.width(keyText) + 12
            val keyX = panelX + 24
            val keyBtnHover = mouseX in keyX..(keyX + keyW) && mouseY in (addY + 7)..(addY + 27)
            fillRoundedRect(graphics, keyX, addY + 7, keyX + keyW, addY + 27, if (keyBtnHover) 0xFF2A4A70.toInt() else 0xFF162536.toInt(), 3)
            graphics.text(font, keyText, keyX + 6, addY + 12, 0xFFFFFFFF.toInt())

            // Save new bind button
            val saveX = panelX + panelW - 90
            val saveHover = mouseX in saveX..(saveX + 68) && mouseY in (addY + 7)..(addY + 27)
            fillRoundedRect(graphics, saveX, addY + 7, saveX + 68, addY + 27, if (saveHover) 0xFF388E3C.toInt() else 0xFF1B5E20.toInt(), 3)
            graphics.text(font, "§a✔ Save", saveX + 12, addY + 12, 0xFFFFFFFF.toInt())
        }

        // Bottom Action Buttons
        val btnY = panelY + panelH - 34

        // Add Bind button
        val addBtnX = panelX + 16
        val addHover = mouseX in addBtnX..(addBtnX + 100) && mouseY in btnY..(btnY + 22)
        fillRoundedRect(graphics, addBtnX, btnY, addBtnX + 100, btnY + 22, if (addHover) 0xFF1E88E5.toInt() else 0xFF0D47A1.toInt(), 3)
        graphics.text(font, if (isAddingNew) "§cCancel Add" else "§a+ Add Bind", addBtnX + 14, btnY + 6, 0xFFFFFFFF.toInt())

        // Clear All button
        val clearX = panelX + 124
        val clearHover = mouseX in clearX..(clearX + 90) && mouseY in btnY..(btnY + 22)
        fillRoundedRect(graphics, clearX, btnY, clearX + 90, btnY + 22, if (clearHover) 0xFFC62828.toInt() else 0xFF4A1010.toInt(), 3)
        graphics.text(font, "§cClear All", clearX + 16, btnY + 6, 0xFFFFFFFF.toInt())

        // Done button
        val doneX = panelX + panelW - 96
        val doneHover = mouseX in doneX..(doneX + 80) && mouseY in btnY..(btnY + 22)
        fillRoundedRect(graphics, doneX, btnY, doneX + 80, btnY + 22, if (doneHover) 0xFF37474F.toInt() else 0xFF21272B.toInt(), 3)
        graphics.text(font, "§fDone", doneX + 24, btnY + 6, 0xFFFFFFFF.toInt())

        super.extractRenderState(graphics, mouseX, mouseY, a)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x.toInt()
        val mouseY = event.y.toInt()

        val panelW = 400
        val panelH = 260
        val panelX = width / 2 - panelW / 2
        val panelY = height / 2 - panelH / 2
        val btnY = panelY + panelH - 34

        // Done button
        val doneX = panelX + panelW - 96
        if (mouseX in doneX..(doneX + 80) && mouseY in btnY..(btnY + 22)) {
            onClose()
            return true
        }

        // Clear All
        val clearX = panelX + 124
        if (mouseX in clearX..(clearX + 90) && mouseY in btnY..(btnY + 22)) {
            Config.commandBinds.clear()
            Config.save()
            return true
        }

        // Toggle Add Bind mode
        val addBtnX = panelX + 16
        if (mouseX in addBtnX..(addBtnX + 100) && mouseY in btnY..(btnY + 22)) {
            isAddingNew = !isAddingNew
            listeningForNewKey = false
            clearWidgets()
            init()
            return true
        }

        // Handle Add New UI clicks
        if (isAddingNew) {
            val addY = panelY + panelH - 80
            val keyX = panelX + 24
            val keyText = if (listeningForNewKey) "§e[ Press Key... ]" else if (newKey != null) "§a[ ${getKeyName(newKey!!)} ]" else "§7[ Click to Set Key ]"
            val keyW = font.width(keyText) + 12

            if (mouseX in keyX..(keyX + keyW) && mouseY in (addY + 7)..(addY + 27)) {
                listeningForNewKey = true
                return true
            }

            val saveX = panelX + panelW - 90
            if (mouseX in saveX..(saveX + 68) && mouseY in (addY + 7)..(addY + 27)) {
                val cmd = newCommandEditBox?.value?.trim() ?: ""
                val k = newKey
                if (cmd.isNotEmpty() && k != null && k > 0) {
                    Config.commandBinds[k] = cmd
                    Config.save()
                    isAddingNew = false
                    listeningForNewKey = false
                    newKey = null
                    clearWidgets()
                    init()
                }
                return true
            }
        }

        // List item clicks (Key rebind & Delete)
        val listY = panelY + 48
        val itemHeight = 24
        val binds = Config.commandBinds.toList()
        val visibleBinds = binds.drop(scrollOffset).take(6)

        for ((idx, entry) in visibleBinds.withIndex()) {
            val (key, _) = entry
            val rowY = listY + idx * (itemHeight + 4)

            // Delete click
            val delX = panelX + panelW - 42
            if (mouseX in delX..(delX + 20) && mouseY in (rowY + 3)..(rowY + itemHeight - 3)) {
                Config.commandBinds.remove(key)
                Config.save()
                return true
            }

            // Key pill click (rebind)
            val keyLabel = "§f[ ${getKeyName(key)} ]"
            val keyW = font.width(keyLabel) + 12
            if (mouseX in (panelX + 22)..(panelX + 22 + keyW) && mouseY in (rowY + 2)..(rowY + itemHeight - 2)) {
                editingKey = if (editingKey == key) null else key
                return true
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()

        if (editingKey != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editingKey = null
                return true
            }
            val oldKey = editingKey!!
            val cmd = Config.commandBinds.remove(oldKey)
            if (cmd != null) {
                Config.commandBinds[key] = cmd
                Config.save()
            }
            editingKey = null
            return true
        }

        if (listeningForNewKey) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listeningForNewKey = false
                return true
            }
            newKey = key
            listeningForNewKey = false
            return true
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        return super.keyPressed(event)
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }
}
