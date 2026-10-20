package com.asthoonlite.qol

import com.asthoonlite.config.Config
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

object WardrobeKeybinds {

    private val wardrobeTitleRegex = Regex("""^\(\d+/\d+\) (Armor|Equipment|Loadouts) Sets?$""", RegexOption.IGNORE_CASE)
    private val loadoutSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
    private var lastClickTime = 0L

    @Volatile var pendingLoadoutIndex: Int? = null
    @Volatile var pendingLoadoutTime = 0L

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || mc.screen != null) return@register

            // Handle dedicated loadout keybinds (Loadouts 1..9)
            val window = mc.window
            for ((key, loadoutIdx) in Config.loadoutBinds) {
                if (key <= 0) continue
                val isDown = try { com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key) } catch (_: Throwable) { false }
                if (isDown) {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime > 400L) {
                        lastClickTime = now
                        triggerLoadout(loadoutIdx)
                    }
                }
            }
        }
    }

    fun isLoadoutMenu(title: String): Boolean {
        val t = title.lowercase().trim()
        return t.contains("loadout") ||
            t.contains("wardrobe") ||
            t.contains("equipment set") ||
            t.contains("armor set") ||
            title.matches(wardrobeTitleRegex)
    }

    fun isWardrobeMenu(title: String): Boolean = isLoadoutMenu(title)

    fun triggerLoadout(index: Int) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        pendingLoadoutIndex = index
        pendingLoadoutTime = System.currentTimeMillis()
        player.connection.sendCommand("loadout")
    }

    fun onScreenOpen(screen: AbstractContainerScreen<*>) {
        val targetIdx = pendingLoadoutIndex ?: return
        if (System.currentTimeMillis() - pendingLoadoutTime > 3000L) {
            pendingLoadoutIndex = null
            return
        }

        val title = screen.title.string
        if (!isLoadoutMenu(title)) return

        val slot = getTargetSlot(title, targetIdx - 1)
        val menu = screen.menu
        if (slot in menu.slots.indices && menu.slots[slot].hasItem()) {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return
            val gameMode = mc.gameMode ?: return

            gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player)
            player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f)
            player.closeContainer()
            pendingLoadoutIndex = null
        }
    }

    private fun getTargetSlot(title: String, indexZeroBased: Int): Int {
        val idx = indexZeroBased.coerceIn(0, 8)
        return if (title.lowercase().contains("loadout")) {
            loadoutSlots[idx]
        } else {
            36 + idx
        }
    }

    fun handleKeyPressed(screen: AbstractContainerScreen<*>, keyCode: Int): Boolean {
        if (!Config.wardrobeKeybindsEnabled) return false

        val title = screen.title.string
        if (!isLoadoutMenu(title)) return false

        if (keyCode in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_9) {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 250L) return true
            lastClickTime = now

            val index = keyCode - GLFW.GLFW_KEY_1
            val targetSlot = getTargetSlot(title, index)

            val menu = screen.menu
            if (targetSlot in menu.slots.indices && menu.slots[targetSlot].hasItem()) {
                val mc = Minecraft.getInstance()
                val player = mc.player ?: return true
                val gameMode = mc.gameMode ?: return true

                gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player)
                player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f)

                if (Config.wardrobeAutoClose) {
                    player.closeContainer()
                }
                return true
            }
        }
        return false
    }

    fun onSlotClick(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        slotId: Int,
        actionType: ContainerInput
    ): Boolean {
        if (!Config.wardrobeKeybindsEnabled && !Config.wardrobeAutoClose) return false
        val title = screen.title.string
        if (!isLoadoutMenu(title)) return false

        val targetSlot = slot?.index ?: slotId
        val isTarget = (title.lowercase().contains("loadout") && targetSlot in loadoutSlots) ||
            (!title.lowercase().contains("loadout") && targetSlot in 36..44)

        if (isTarget && slot != null && slot.hasItem()) {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return false
            player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f)

            if (Config.wardrobeAutoClose) {
                // Let the click packet go through, then close container next tick
                net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(object : net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick {
                    override fun onEndTick(client: Minecraft) {
                        client.player?.closeContainer()
                    }
                })
            }
        }
        return false
    }
}
