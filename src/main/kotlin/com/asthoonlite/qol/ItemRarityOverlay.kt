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

    private val dungeonLootBlacklist = setOf(
        "BONZO_MASK", "STARRED_BONZO_MASK",
        "BONZO_STAFF", "STARRED_BONZO_STAFF",
        "DUNGEONBREAKER",
        "ICE_SPRAY_WAND", "STARRED_ICE_SPRAY_WAND",
        "SPIRIT_SCEPTRE", "STARRED_SPIRIT_SCEPTRE",
        "SPIRIT_BOW", "STARRED_SPIRIT_BOW",
        "HYPERION", "VALKYRIE", "ASTRAEA", "SCYLLA", "NECRON_BLADE",
        "TERMINATOR", "JUJU_SHORTBOW",
        "DARK_CLAYMORE", "GIANTS_SWORD",
        "LIVID_DAGGER", "SHADOW_FURY",
        "SHADOW_ASSASSIN_CHESTPLATE", "SHADOW_ASSASSIN_HELMET",
        "SHADOW_ASSASSIN_LEGGINGS", "SHADOW_ASSASSIN_BOOTS",
        "NECRON_CHESTPLATE", "NECRON_HELMET", "NECRON_LEGGINGS", "NECRON_BOOTS",
        "STORM_CHESTPLATE", "STORM_HELMET", "STORM_LEGGINGS", "STORM_BOOTS",
        "MAXOR_CHESTPLATE", "MAXOR_HELMET", "MAXOR_LEGGINGS", "MAXOR_BOOTS",
        "GOLDOR_CHESTPLATE", "GOLDOR_HELMET", "GOLDOR_LEGGINGS", "GOLDOR_BOOTS",
        "WITHER_CLOAK"
    )

    fun isRedundantMobDrop(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val id = ItemProtection.getSkyblockId(stack)
        if (id in dungeonLootBlacklist) return false

        // Do not highlight starred items (player gear)
        val name = stack.hoverName.string
        if (name.contains("✪") || name.contains("★")) return false

        val extra = getExtraAttributes(stack) ?: return false
        // Skip items with player upgrades
        if (extra.getIntOr("upgrade_level", 0) > 0) return false

        // Mob drops have baseStatBoostPercentage
        val boost = extra.getIntOr("baseStatBoostPercentage", 0)
        return boost > 0
    }

    fun isDungeonItem(stack: ItemStack): Boolean = isRedundantMobDrop(stack)

    fun drawSlotBackground(graphics: GuiGraphicsExtractor, slot: Slot) {
        val stack = slot.item
        if (stack.isEmpty) return

        val isMobDrop = isRedundantMobDrop(stack)
        val quality = if (Config.dungeonItemQualityHighlight && isMobDrop) getDungeonQuality(stack) else 0
        val rarity = getRarity(stack)

        val sx = slot.x
        val sy = slot.y

        // Dungeon item quality highlight (only for redundant mob drops: deep crimson red)
        if (Config.dungeonItemQualityHighlight && isMobDrop && quality > 0) {
            val fillColor = 0x70B8001F.toInt()
            val borderColor = 0xFFDC143C.toInt()
            // Fill
            graphics.fill(sx, sy, sx + 16, sy + 16, fillColor)
            // 1px Border outline
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
        if (Config.dungeonItemQualityHighlight && isRedundantMobDrop(stack)) {
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
