package com.asthoonlite.qol

import com.asthoonlite.config.Config
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ItemProtection {

    fun isProtected(stack: ItemStack): Boolean {
        if ((!Config.itemProtectionEnabled && !Config.starredItemProtection) || stack.isEmpty) return false

        val customData = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        if (customData != null) {
            val extra = customData.getCompoundOrEmpty("ExtraAttributes")
            val uuid = extra.getStringOr("uuid", "")
            if (uuid.isNotEmpty() && uuid in Config.protectedUuids) return true

            val id = extra.getStringOr("id", "")
            if (id.isNotEmpty() && id in Config.protectedIds) return true

            if (Config.protectStarredItems) {
                val upgradeLevel = extra.getIntOr("upgrade_level", 0)
                if (upgradeLevel > 0) return true
                val rarityUpgrades = extra.getIntOr("rarity_upgrades", 0)
                if (rarityUpgrades > 0) return true
            }
        }

        val name = stack.hoverName.string
        if (Config.protectStarredItems && (name.contains("✪") || name.contains("★"))) {
            return true
        }

        return false
    }

    fun toggleProtection(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val customData = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        val extra = customData?.getCompoundOrEmpty("ExtraAttributes")
        val uuid = extra?.getStringOr("uuid", "") ?: ""
        val id = extra?.getStringOr("id", "") ?: ""

        val key = if (uuid.isNotEmpty()) uuid else id
        if (key.isEmpty()) {
            notify("§cItem has no unique ID or UUID to protect.")
            return false
        }

        val isUuid = uuid.isNotEmpty()
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
        if (title.contains("trades") || title.contains("shop") || title.contains("merchant") || title.contains("sell")) {
            return true
        }
        val slots = screen.menu.slots
        val checkLimit = minOf(54, slots.size)
        for (i in 0 until checkLimit) {
            val item = slots[i].item
            if (item.isEmpty) continue
            if (item.`is`(Items.HOPPER) && item.hoverName.string.contains("Sell Item", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun notify(message: String) {
        val mc = Minecraft.getInstance()
        mc.player?.sendSystemMessage(Component.literal(message))
    }
}
