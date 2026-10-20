package com.asthoonlite.qol

import com.asthoonlite.config.Config
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
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
            fun fromString(str: String): Rarity? = when (str.uppercase()) {
                "COMMON" -> COMMON
                "UNCOMMON" -> UNCOMMON
                "RARE" -> RARE
                "EPIC" -> EPIC
                "LEGENDARY" -> LEGENDARY
                "MYTHIC" -> MYTHIC
                "DIVINE" -> DIVINE
                "SPECIAL" -> SPECIAL
                "VERY_SPECIAL", "VERY SPECIAL" -> VERY_SPECIAL
                else -> null
            }
        }
    }

    fun getRarity(stack: ItemStack): Rarity? {
        if (stack.isEmpty) return null
        val customData = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        val extra = customData?.getCompoundOrEmpty("ExtraAttributes")
        if (extra != null) {
            val rarityStr = extra.getStringOr("rarity", "")
            if (rarityStr.isNotEmpty()) {
                val parsed = Rarity.fromString(rarityStr)
                if (parsed != null) return parsed
            }
        }

        // Check lore lines
        val lore = stack.get(DataComponents.LORE)
        if (lore != null && lore.lines.isNotEmpty()) {
            for (line in lore.lines.reversed()) {
                val str = line.string.uppercase()
                for (r in Rarity.entries) {
                    if (str.contains(r.name.replace("_", " "))) {
                        return r
                    }
                }
            }
        }

        return null
    }

    fun getDungeonQuality(stack: ItemStack): Int {
        if (stack.isEmpty) return 0
        val customData = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        val extra = customData?.getCompoundOrEmpty("ExtraAttributes") ?: return 0
        return extra.getIntOr("baseStatBoostPercentage", 0)
    }

    fun drawSlotBackground(graphics: GuiGraphicsExtractor, slot: Slot) {
        val stack = slot.item
        if (stack.isEmpty) return

        val quality = if (Config.dungeonItemQualityHighlight) getDungeonQuality(stack) else 0
        val rarity = if (Config.itemRarityBackground) getRarity(stack) else null

        val sx = slot.x
        val sy = slot.y

        // Dungeon item quality highlight (less transparent, vibrant)
        if (quality > 0) {
            val (fillColor, borderColor) = when {
                quality >= 50 -> 0x75FFD700.toInt() to 0xE0FFD700.toInt() // 50% max roll: bright gold
                quality >= 40 -> 0x6055FF55.toInt() to 0xC055FF55.toInt() // 40-49%: bright green
                else -> 0x5055FFFF.toInt() to 0xA055FFFF.toInt()          // <40%: cyan
            }
            // Fill
            graphics.fill(sx, sy, sx + 16, sy + 16, fillColor)
            // Border outline
            graphics.fill(sx, sy, sx + 16, sy + 1, borderColor)
            graphics.fill(sx, sy + 15, sx + 16, sy + 16, borderColor)
            graphics.fill(sx, sy, sx + 1, sy + 16, borderColor)
            graphics.fill(sx + 15, sy, sx + 16, sy + 16, borderColor)
            return
        }

        // Standard rarity background (good subtle transparency)
        if (rarity != null) {
            val alpha = (Config.itemRarityOpacity * 255).toInt().coerceIn(10, 255)
            val fillColor = (alpha shl 24) or (rarity.color and 0x00FFFFFF)
            graphics.fill(sx, sy, sx + 16, sy + 16, fillColor)
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
