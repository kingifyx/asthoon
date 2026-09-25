package com.asthoonlite.funny

import com.asthoonlite.config.Config
import com.asthoonlite.mixin.AbstractContainerScreenAccessor
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.Random

/**
 * Spams clicks in inventories/containers (such as claiming stash items directly into
 * the inventory) at a lower, human-simulated CPS via either:
 * 1) A toggleable keybind that works even while inside container/stash GUIs.
 * 2) Holding down left-click over items.
 *
 * Uses QUICK_MOVE (Shift-click) to move items straight into the player's inventory
 * without cursor pickup desyncs or stuck items.
 */
object InventoryAutoClicker {

    private val random = Random()
    private var baseCpsDrift = 5.0
    private var lastDriftTime = 0L
    private var nextClickTime = 0L
    private var wasMouseDown = false
    private var previousKeyDown = false

    var isMacroActive = false
        private set

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            tick()
        }
    }

    /**
     * Intercepts key presses inside container screens so the stash macro keybind
     * can toggle the macro while actively viewing the stash GUI.
     */
    fun handleScreenKeyPressed(keyCode: Int): Boolean {
        if (!Config.inventoryAutoClickerEnabled) return false
        val boundKey = Config.inventoryAutoClickerKey
        if (boundKey >= 0 && keyCode == boundKey) {
            toggleMacro()
            return true
        }
        return false
    }

    /**
     * Intercepts mouse button clicks inside container screens for mouse button keybinds.
     */
    fun handleScreenMouseClicked(button: Int): Boolean {
        if (!Config.inventoryAutoClickerEnabled) return false
        val boundKey = Config.inventoryAutoClickerKey
        if (boundKey in 0..9 && button == boundKey) {
            toggleMacro()
            return true
        }
        return false
    }

    private fun toggleMacro() {
        isMacroActive = !isMacroActive
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val status = if (isMacroActive) "§aACTIVE" else "§cINACTIVE"
        val msg = Component.literal("§7[AsthoonLite] Stash Macro: $status")
        player.sendSystemMessage(msg)
        mc.gui.setOverlayMessage(msg, false)
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, if (isMacroActive) 1.2f else 0.8f)
    }

    private fun tick() {
        val mc = Minecraft.getInstance()
        val screen = mc.screen as? AbstractContainerScreen<*> ?: run {
            if (isMacroActive) isMacroActive = false
            reset()
            return
        }

        if (!Config.inventoryAutoClickerEnabled) {
            if (isMacroActive) isMacroActive = false
            reset()
            return
        }

        val window = mc.window

        // Poll keybind in tick loop as well for mouse buttons or modifier keys
        val boundKey = Config.inventoryAutoClickerKey
        if (boundKey >= 0) {
            val isKeyDown = if (boundKey < 10) {
                GLFW.glfwGetMouseButton(window.handle(), boundKey) == GLFW.GLFW_PRESS
            } else {
                InputConstants.isKeyDown(window, boundKey)
            }
            if (isKeyDown && !previousKeyDown) {
                toggleMacro()
            }
            previousKeyDown = isKeyDown
        } else {
            previousKeyDown = false
        }

        // The stash macro ONLY runs when explicitly toggled ON.
        // It never interferes with or hijacks regular manual mouse clicks when inactive.
        if (!isMacroActive) {
            reset()
            return
        }

        val screenAcc = screen as? AbstractContainerScreenAccessor ?: return
        val slot = screenAcc.hoveredSlot
        if (slot == null || !slot.hasItem()) {
            return
        }

        val now = System.currentTimeMillis()
        if (nextClickTime == 0L) {
            nextClickTime = now + calculateNextInterval(now, Config.inventoryAutoClickerCps.toDouble())
            return
        }

        if (now >= nextClickTime) {
            val player = mc.player ?: return
            val gameMode = mc.gameMode ?: return

            // In "View Stash", right-click picks up a full 64-stack while left-click picks up 1 item.
            // If the item lore indicates stack pickup, send right-click (button 1) for full stack pickup.
            val isStackPickup = hasStackPickupLore(slot.item)
            val button = if (isStackPickup) 1 else 0

            gameMode.handleContainerInput(screen.menu.containerId, slot.index, button, ContainerInput.PICKUP, player)

            // In Hypixel menus like View Stash, clicking an item transfers it straight into
            // the player inventory. Clear client-side carried stack immediately so subsequent
            // clicks don't attempt to place the item back into the container slot.
            if (!screen.menu.carried.isEmpty) {
                screen.menu.carried = ItemStack.EMPTY
            }
            if (!player.containerMenu.carried.isEmpty) {
                player.containerMenu.carried = ItemStack.EMPTY
            }

            nextClickTime = now + calculateNextInterval(now, Config.inventoryAutoClickerCps.toDouble())
        }
    }

    private fun hasStackPickupLore(stack: ItemStack): Boolean {
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE) ?: return false
        for (line in lore.lines) {
            if (line.string.contains("Right-click to pickup a stack", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun reset() {
        wasMouseDown = false
        nextClickTime = 0L
    }

    /**
     * Generates humanized click intervals for inventory clicking at a lower rate (~5 CPS default).
     */
    private fun calculateNextInterval(now: Long, targetCps: Double): Long {
        val cps = targetCps.coerceIn(2.0, 15.0)

        // Drift base CPS slightly every ~1000ms
        if (now - lastDriftTime > 1000L) {
            val drift = (random.nextDouble() * 1.2) - 0.6 // ±0.6 CPS drift
            baseCpsDrift = (cps + drift).coerceIn(cps - 0.8, cps + 0.8)
            lastDriftTime = now
        }

        val baseDelay = (1000.0 / baseCpsDrift).toLong()

        // Gaussian jitter
        val gaussian = random.nextGaussian()
        val jitter = if (gaussian < 0) (gaussian * 10.0).toLong() else (gaussian * 22.0).toLong()

        var finalDelay = baseDelay + jitter

        // Micro-variations
        val roll = random.nextDouble()
        if (roll < 0.02) {
            finalDelay += random.nextInt(40, 90)
        } else if (roll < 0.04) {
            finalDelay -= random.nextInt(15, 25)
        }

        val minDelay = (1000.0 / (cps + 2.0)).toLong().coerceAtLeast(80L)
        val maxDelay = (1000.0 / (cps - 1.5).coerceAtLeast(1.0)).toLong()
        return finalDelay.coerceIn(minDelay, maxDelay)
    }
}
