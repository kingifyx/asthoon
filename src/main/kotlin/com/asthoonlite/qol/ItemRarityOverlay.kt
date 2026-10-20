package com.asthoonlite.qol

import com.asthoonlite.config.Config
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object ItemRarityOverlay {

    enum class Rarity(val color: Int) {
        COMMON(0xFFFFFFFF.toInt()),
        UNCOMMON(0xFF55FF55.toInt()),
        RARE(0xFF5555FF.toInt()),
        EPIC(0xFFAA00AA.toInt()),
        LEGENDARY(0xFFFFAA00.toInt()),
        MYTHIC(0xFFFF55FF.toInt()),
        DIVINE(0xFF55FFFF.toInt()),
        SPECIAL(0xFFFF5555.toInt()),
        VERY_SPECIAL(0xFFAA0000.toInt());

        companion object {
            fun fromString(str: String): Rarity? = when (str.uppercase().replace("_", " ")) {
                "COMMON" -> COMMON
                "UNCOMMON" -> UNCOMMON
                "RARE" -> RARE
                "EPIC" -> EPIC
                "LEGENDARY" -> LEGENDARY
                "MYTHIC" -> MYTHIC
                "DIVINE" -> DIVINE
                "SPECIAL" -> SPECIAL
                "VERY SPECIAL" -> VERY_SPECIAL
                else -> null
            }
        }
    }

    fun getExtraAttributes(stack: ItemStack): CompoundTag? {
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        if (tag.contains("id") || tag.contains("uuid") || tag.contains("baseStatBoostPercentage") || tag.contains("rarity")) {
            return tag
        }
        val sub = tag.getCompoundOrEmpty("ExtraAttributes")
        if (!sub.isEmpty) return sub
        return tag
    }

    fun getRarity(stack: ItemStack): Rarity? {
        if (stack.isEmpty) return null
        val extra = getExtraAttributes(stack)
        if (extra != null) {
            val rarityStr = extra.getStringOr("rarity", "")
            if (rarityStr.isNotEmpty()) {
                val parsed = Rarity.fromString(rarityStr)
                if (parsed != null) return parsed
            }
        }

        // Check lore lines
        val lore = stack.get(DataComponents.LORE)
        if (lore != null) {
            for (line in lore.lines().reversed()) {
                val str = line.string.uppercase()
                for (r in Rarity.entries) {
                    val name = r.name.replace("_", " ")
                    if (str.contains(name)) {
                        return r
                    }
                }
            }
        }

        return null
    }

    fun getDungeonQuality(stack: ItemStack): Int {
        if (stack.isEmpty) return 0
        val extra = getExtraAttributes(stack) ?: return 0
        return extra.getIntOr("baseStatBoostPercentage", 0)
    }

    fun isDungeonItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (getDungeonQuality(stack) > 0) return true
        val extra = getExtraAttributes(stack)
        if (extra != null) {
            if (extra.getIntOr("item_tier", 0) > 0) return true
            if (extra.getIntOr("dungeon_item_level", 0) > 0) return true
            if (extra.getStringOr("dungeon_floor", "").isNotEmpty()) return true
        }
        val lore = stack.get(DataComponents.LORE)
        if (lore != null) {
            for (line in lore.lines()) {
                val s = line.string.uppercase()
                if (s.contains("DUNGEON ITEM") || s.contains("DUNGEON SWORD") ||
                    s.contains("DUNGEON BOW") || s.contains("DUNGEON HELMET") ||
                    s.contains("DUNGEON CHESTPLATE") || s.contains("DUNGEON LEGGINGS") ||
                    s.contains("DUNGEON BOOTS") || s.contains("DUNGEON")) {
                    return true
                }
            }
        }
        return false
    }

    fun drawSlotBackground(graphics: GuiGraphicsExtractor, slot: Slot) {
        val stack = slot.item
        if (stack.isEmpty) return

        val quality = if (Config.dungeonItemQualityHighlight) getDungeonQuality(stack) else 0
        val isDungeon = isDungeonItem(stack)
        val rarity = getRarity(stack)

        val sx = slot.x
        val sy = slot.y

        // Dungeon item quality highlight (vibrant, less transparent like Noamm)
        if (quality > 0) {
            val (fillColor, borderColor) = when {
                quality >= 50 -> 0x90FFD700.toInt() to 0xFFFFD700.toInt() // 50% max roll: bright gold
                quality >= 40 -> 0x8055FF55.toInt() to 0xFF55FF55.toInt() // 40-49%: bright green
                else -> 0x7055FFFF to 0xFF55FFFF.toInt()          // <40%: cyan
            }
            // Fill
            graphics.fill(sx, sy, sx + 16, sy + 16, fillColor)
            // 1px Border outline
            graphics.fill(sx, sy, sx + 16, sy + 1, borderColor)
            graphics.fill(sx, sy + 15, sx + 16, sy + 16, borderColor)
            graphics.fill(sx, sy, sx + 1, sy + 16, borderColor)
            graphics.fill(sx + 15, sy, sx + 16, sy + 16, borderColor)
            return
        }

        // Dungeon mob drop / dungeon item without quality roll (highlighted based on rarity, less transparent)
        if (Config.dungeonItemQualityHighlight && isDungeon && rarity != null) {
            val fillColor = (0x75 shl 24) or (rarity.color and 0x00FFFFFF)
            val borderColor = (0xFF shl 24) or (rarity.color and 0x00FFFFFF)
            graphics.fill(sx, sy, sx + 16, sy + 16, fillColor)
            graphics.fill(sx, sy, sx + 16, sy + 1, borderColor)
            graphics.fill(sx, sy + 15, sx + 16, sy + 16, borderColor)
            graphics.fill(sx, sy, sx + 1, sy + 16, borderColor)
            graphics.fill(sx + 15, sy, sx + 16, sy + 16, borderColor)
            return
        }

        // Standard rarity background (user customizable opacity)
        if (Config.itemRarityBackground && rarity != null) {
            val alpha = (Config.itemRarityOpacity * 255).toInt().coerceIn(30, 255)
            val fillColor = (alpha shl 24) or (rarity.color and 0x00FFFFFF)
            val borderColor = ((alpha * 1.5f).toInt().coerceIn(60, 255) shl 24) or (rarity.color and 0x00FFFFFF)
            graphics.fill(sx, sy, sx + 16, sy + 16, fillColor)
            graphics.fill(sx, sy, sx + 16, sy + 1, borderColor)
            graphics.fill(sx, sy + 15, sx + 16, sy + 16, borderColor)
            graphics.fill(sx, sy, sx + 1, sy + 16, borderColor)
            graphics.fill(sx + 15, sy, sx + 16, sy + 16, borderColor)
        }
    }

    fun drawSlotOverlays(graphics: GuiGraphicsExtractor, slot: Slot) {
        val stack = slot.item
        if (stack.isEmpty) return

        val font = Minecraft.getInstance().font
        val sx = slot.x
        val sy = slot.y

        // Starred protection indicator
        if (ItemProtection.isProtected(stack)) {
            graphics.text(font, "§6✪", sx + 1, sy + 1, 0xFFFFAA00.toInt())
        }

        // Dungeon quality text badge
        if (Config.dungeonItemQualityHighlight) {
            val quality = getDungeonQuality(stack)
            if (quality > 0) {
                val pose = graphics.pose()
                pose.pushMatrix()
                pose.translate(sx.toFloat(), sy.toFloat())
                pose.scale(0.6f, 0.6f)
                val text = if (quality >= 50) "§650%" else "§a$quality%"
                graphics.text(font, text, 1, 16, 0xFFFFFFFF.toInt())
                pose.popMatrix()
            }
        }
    }
}
