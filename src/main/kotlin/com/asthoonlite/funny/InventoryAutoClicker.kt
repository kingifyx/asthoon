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
        if (boundKey >= 10 && keyCode == boundKey) {
            toggleMacro()
            previousKeyDown = true
            return true
        }
        return false
    }

    /**
     * Intercepts mouse button clicks inside container screens for mouse button keybinds.
     * Button 0 (left-click) is never intercepted here to protect normal container clicks.
     */
    fun handleScreenMouseClicked(button: Int): Boolean {
        if (!Config.inventoryAutoClickerEnabled) return false
        val boundKey = Config.inventoryAutoClickerKey
        if (boundKey in 1..9 && button == boundKey) {
            toggleMacro()
            previousKeyDown = true
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

        // Poll keybind in tick loop to synchronize state and support outside-container or modifier toggles
        val boundKey = Config.inventoryAutoClickerKey
        if (boundKey >= 0) {
            val isKeyDown = if (boundKey in 1..9) {
                GLFW.glfwGetMouseButton(window.handle(), boundKey) == GLFW.GLFW_PRESS
            } else {
                InputConstants.isKeyDown(window, boundKey)
            }
            if (isKeyDown) {
                if (!previousKeyDown) {
                    toggleMacro()
                    previousKeyDown = true
                }
            } else {
                previousKeyDown = false
            }
        } else {
            previousKeyDown = false
        }

        val isLeftMouseDown = GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

        // Macro runs if toggled ACTIVE, or if holding left-click while hovering over stash items
        val isClicking = isMacroActive || isLeftMouseDown
        if (!isClicking) {
            reset()
            return
        }

        val screenAcc = screen as? AbstractContainerScreenAccessor ?: return
        val slot = screenAcc.hoveredSlot
        if (slot == null || !slot.hasItem()) {
            wasMouseDown = false
            return
        }

        val player = mc.player ?: return
        val isTopContainer = slot.container != player.inventory
        val isStashItem = isTopContainer && (hasStackPickupLore(slot.item) || hasPickupLore(slot.item))

        // When holding left-click, only auto-click if hovering over actual stash items
        if (!isStashItem) {
            wasMouseDown = false
            return
        }

        val now = System.currentTimeMillis()

        // When holding left mouse button, initial click was handled by physical click
        if (isLeftMouseDown && !isMacroActive && !wasMouseDown) {
            wasMouseDown = true
            nextClickTime = now + calculateNextInterval(now, Config.inventoryAutoClickerCps.toDouble())
            return
        }

        if (nextClickTime == 0L) {
            nextClickTime = now + calculateNextInterval(now, Config.inventoryAutoClickerCps.toDouble())
            return
        }

        if (now >= nextClickTime) {
            val gameMode = mc.gameMode ?: return

            // In "View Stash", right-click picks up a full 64-stack while left-click picks up 1 item.
            // If the item lore indicates stack pickup, send right-click (button 1) for full stack pickup.
            val isStackPickup = hasStackPickupLore(slot.item)
            val button = if (isStackPickup) 1 else 0

            val savedItem = slot.item.copy()
            gameMode.handleContainerInput(screen.menu.containerId, slot.index, button, ContainerInput.PICKUP, player)

            // Restore slot item so it never disappears on client
            slot.set(savedItem)

            // Clear client-side carried stack immediately so subsequent clicks don't desync
            if (!screen.menu.carried.isEmpty) {
                screen.menu.carried = ItemStack.EMPTY
            }
            if (!player.containerMenu.carried.isEmpty) {
                player.containerMenu.carried = ItemStack.EMPTY
            }

            nextClickTime = now + calculateNextInterval(now, Config.inventoryAutoClickerCps.toDouble())
        }
    }

    fun hasStackPickupLore(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE) ?: return false
        for (line in lore.lines) {
            val text = line.string
            if (text.contains("Right-click to pickup a stack", ignoreCase = true) ||
                text.contains("pickup a stack", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun hasPickupLore(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE) ?: return false
        for (line in lore.lines) {
            val text = line.string
            if (text.contains("Left-click to pickup", ignoreCase = true) ||
                text.contains("click to pickup", ignoreCase = true) ||
                text.contains("click to claim", ignoreCase = true)) {
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
