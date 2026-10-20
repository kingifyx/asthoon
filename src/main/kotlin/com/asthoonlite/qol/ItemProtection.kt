package com.asthoonlite.qol

import com.asthoonlite.config.Config
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ItemProtection {

    private var wasHotbarKeyPressed = false

    fun register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val window = mc.window
            val player = mc.player ?: return@register
            if (mc.screen != null) {
                wasHotbarKeyPressed = false
                return@register
            }

            val protectKey = Config.protectKeybind
            val starKey = Config.starProtectionKey

            val isDown = (protectKey > 0 && try { com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, protectKey) } catch (_: Throwable) { false }) ||
                         (starKey > 0 && try { com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, starKey) } catch (_: Throwable) { false })

            if (isDown && !wasHotbarKeyPressed) {
                val held = player.inventory.selectedItem
                if (!held.isEmpty) {
                    toggleProtection(held)
                }
            }
            wasHotbarKeyPressed = isDown
        }
    }

    fun getExtraAttributes(stack: ItemStack): CompoundTag? {
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        if (tag.contains("id") || tag.contains("uuid") || tag.contains("baseStatBoostPercentage")) return tag
        val sub = tag.getCompoundOrEmpty("ExtraAttributes")
        if (!sub.isEmpty) return sub
        return tag
    }

    fun getUuid(stack: ItemStack): String? {
        val tag = getExtraAttributes(stack) ?: return null
        val uuid = tag.getStringOr("uuid", "")
        return if (uuid.isNotBlank()) uuid else null
    }

    fun getSkyblockId(stack: ItemStack): String {
        val tag = getExtraAttributes(stack)
        val id = tag?.getStringOr("id", "") ?: ""
        if (id.isNotBlank()) return id
        return BuiltInRegistries.ITEM.getKey(stack.item).path
    }

    fun isProtected(stack: ItemStack): Boolean {
        if ((!Config.itemProtectionEnabled && !Config.starredItemProtection) || stack.isEmpty) return false

        val uuid = getUuid(stack)
        if (uuid != null && uuid in Config.protectedUuids) return true

        val id = getSkyblockId(stack)
        if (id.isNotEmpty() && id in Config.protectedIds) return true

        return false
    }

    fun toggleProtection(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false

        val uuid = getUuid(stack)
        val id = getSkyblockId(stack)

        val key = uuid ?: id
        if (key.isBlank()) {
            notify("§cItem has no unique ID or UUID to protect.")
            return false
        }

        val isUuid = uuid != null
        val list = if (isUuid) Config.protectedUuids else Config.protectedIds

        val newState: Boolean
        if (list.contains(key)) {
            list.remove(key)
            newState = false
            notify("§c✪ Star Protection Removed: §f${stack.hoverName.string}")
        } else {
            list.add(key)
            newState = true
            notify("§6✪ Star Protection Added: §f${stack.hoverName.string}")
        }
        Config.save()
        return newState
    }

    fun shouldBlockSlotClick(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        slotId: Int,
        actionType: ContainerInput
    ): Boolean {
        if (!Config.itemProtectionEnabled && !Config.starredItemProtection) return false

        val menu = screen.menu
        val stack = when {
            slotId == -999 -> menu.carried
            slot != null -> slot.item
            slotId in menu.slots.indices -> menu.slots[slotId].item
            else -> ItemStack.EMPTY
        }

        if (stack.isEmpty || !isProtected(stack)) return false

        val isThrowing = actionType == ContainerInput.THROW || slotId == -999
        val isSelling = isSellMenu(screen) && (slotId in menu.slots.indices)

        if (isThrowing || isSelling) {
            notify("§cAction Blocked: This item is protected!")
            return true
        }
        return false
    }

    fun isSellMenu(screen: AbstractContainerScreen<*>): Boolean {
        val title = screen.title.string.lowercase()
        if (title.contains("trades") || title.contains("shop") || title.contains("merchant") ||
            title.contains("sell") || title.contains("ophelia") || title.contains("cookie") ||
            title.contains("bazaar") || title.contains("forge") || title.contains("blacksmith") ||
            title.contains("alchemist") || title.contains("librarian") || title.contains("armorsmith") ||
            title.contains("weaponsmith") || title.contains("builder")) {
            return true
        }

        val slots = screen.menu.slots
        val upperSlotCount = maxOf(0, slots.size - 36)
        val checkLimit = minOf(54, upperSlotCount)
        for (i in 0 until checkLimit) {
            val item = slots[i].item
            if (item.isEmpty) continue

            val name = item.hoverName.string.lowercase()
            if (name.contains("sell item") || name.contains("sell inventory") || name.contains("buyback")) {
                return true
            }

            val loreComp = item.get(DataComponents.LORE)
            if (loreComp != null) {
                for (line in loreComp.lines()) {
                    val s = line.string.lowercase()
                    if (s.contains("click to buy") || s.contains("click to buyback") ||
                        s.contains("click an item in your inventory to sell") ||
                        s.contains("click to sell") || s.contains("cost:") || s.contains("buy price")) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun notify(message: String) {
        val mc = Minecraft.getInstance()
        mc.player?.sendSystemMessage(Component.literal(message))
    }
}
