package com.asthoonlite.qol

import com.asthoonlite.config.Config
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW

object WardrobeKeybinds {

    private val wardrobeTitleRegex = Regex("""^\(\d+/\d+\) (Armor|Equipment) Sets$""")
    private var lastClickTime = 0L

    fun isWardrobeMenu(title: String): Boolean {
        return title.matches(wardrobeTitleRegex) || title.equals("Wardrobe", ignoreCase = true)
    }

    fun handleKeyPressed(screen: AbstractContainerScreen<*>, keyCode: Int): Boolean {
        if (!Config.wardrobeKeybindsEnabled) return false

        val title = screen.title.string
        if (!isWardrobeMenu(title)) return false

        if (keyCode in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_9) {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 250L) return true
            lastClickTime = now

            val index = keyCode - GLFW.GLFW_KEY_1
            val targetSlot = 36 + index

            val menu = screen.menu
            if (targetSlot in menu.slots.indices && menu.slots[targetSlot].hasItem()) {
                val mc = Minecraft.getInstance()
                val player = mc.player ?: return true
                val gameMode = mc.gameMode ?: return true

                gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player)

                // Play confirmation sound
                player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f)

                if (Config.wardrobeAutoClose) {
                    player.closeContainer()
                }
                return true
            }
        }
        return false
    }
}
