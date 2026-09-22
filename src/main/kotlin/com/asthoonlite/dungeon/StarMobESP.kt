package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.player.Player

/**
 * Starred-mob ESP implementing Devonian's BoxStarMob styling, categorization,
 * and hitbox calculations:
 * - Armor stand mapping: nametag -> entity id - 1 (-3 for Withermancer)
 * - Fake player detection: Shadow Assassin, Lost Adventurer, Diamond Guy, King Midas
 * - Fels detection: Enderman named Dinnerbone or stand named Fels
 * - Individual category toggles and Devonian color presets
 * - Customizable line width and fill alpha
 */
object StarMobESP {

    enum class MobCategory {
        SHADOW_ASSASSIN,
        MINIBOSS,
        CHONK,
        SKELETON_MASTER,
        FEL,
        REGULAR
    }

    private val starMobs = LinkedHashMap<Int, MobCategory>()
    private val pendingStands = LinkedHashMap<Int, String>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        LevelRenderEvents.END_EXTRACTION.register { queueBoxes() }
    }

    private fun tick() {
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (!Config.starMobEspEnabled || !DungeonContext.inDungeon || level == null) {
            starMobs.clear()
            pendingStands.clear()
            return
        }

        val localPlayer = mc.player ?: return

        // Scan loaded armor stands continuously
        for (stand in level.getEntitiesOfClass(ArmorStand::class.java, localPlayer.boundingBox.inflate(96.0))) {
            val raw = stand.name.string
            val name = ChatFormatting.stripFormatting(raw) ?: continue
            if (!name.contains("✯")) continue
            val normalized = name.uppercase()
            pendingStands[stand.id] = normalized
        }

        val resolved = ArrayList<Int>()
        for ((standId, name) in pendingStands) {
            val stand = level.getEntity(standId) as? ArmorStand ?: continue
            val offset = if (name.contains("WITHERMANCER")) 3 else 1
            val direct = level.getEntity(standId - offset)
            val mob = if (isCandidateMob(direct, mc.player)) direct else {
                level.getEntities(stand, stand.boundingBox.move(0.0, -1.0, 0.0)) { entity ->
                    isCandidateMob(entity, mc.player)
                }.firstOrNull()
            }
            if (mob != null) {
                starMobs[mob.id] = categorize(name)
                resolved += standId
            }
        }
        resolved.forEach { pendingStands.remove(it) }

        // Fake-player minibosses (Shadow Assassin, Lost Adventurer, Diamond Guy, King Midas)
        for (fake in level.getEntitiesOfClass(Player::class.java, localPlayer.boundingBox.inflate(128.0))) {
            if (fake == localPlayer) continue
            val info = mc.connection?.getPlayerInfo(fake.uuid) ?: continue
            categorizePlayer(info.profile.name)?.let { starMobs[fake.id] = it }
        }

        // Fels (Enderman with name "Dinnerbone")
        for (enderman in level.getEntitiesOfClass(EnderMan::class.java, localPlayer.boundingBox.inflate(96.0))) {
            val name = enderman.customName?.string
            if (name == "Dinnerbone") {
                starMobs[enderman.id] = MobCategory.FEL
            }
        }

        // Purge dead or removed entities
        val iterator = starMobs.iterator()
        while (iterator.hasNext()) {
            val (id, _) = iterator.next()
            val entity = level.getEntity(id)
            if (entity == null || entity.isRemoved || (entity is LivingEntity && entity.health <= 0f)) {
                iterator.remove()
            }
        }
    }

    private fun isCandidateMob(entity: Entity?, local: Player?): Boolean = when {
        entity == null || entity is ArmorStand || entity.isRemoved -> false
        entity is net.minecraft.world.entity.ExperienceOrb -> false
        entity is Player -> entity != local && !entity.isInvisible
        entity is LivingEntity -> entity.health > 0f
        else -> false
    }

    private fun categorize(name: String): MobCategory = when {
        name.contains("SHADOW ASSASSIN") -> MobCategory.SHADOW_ASSASSIN
        name.contains("FELS") -> MobCategory.FEL
        name.contains("SKELETON MASTER") -> MobCategory.SKELETON_MASTER
        name.contains("WITHERMANCER") || name.contains("LORD") ||
            name.contains("ZOMBIE COMMANDER") || name.contains("SUPER ARCHER") -> MobCategory.CHONK
        name.contains("ADVENTURER") || name.contains("ANGRY ARCHAEOLOGIST") || name.contains("KING MIDAS") -> MobCategory.MINIBOSS
        else -> MobCategory.REGULAR
    }

    private fun categorizePlayer(name: String): MobCategory? = when (name) {
        "Shadow Assassin" -> MobCategory.SHADOW_ASSASSIN
        "Lost Adventurer", "Diamond Guy", "King Midas" -> MobCategory.MINIBOSS
        else -> null
    }

    fun isCategoryEnabled(category: MobCategory): Boolean {
        if (!Config.starMobEspEnabled) return false
        val anySpecific = Config.starMobSa || Config.starMobMiniboss || Config.starMobChonk ||
            Config.starMobSm || Config.starMobFel || Config.starMobRegular
        if (!anySpecific) return true

        return when (category) {
            MobCategory.SHADOW_ASSASSIN -> Config.starMobSa
            MobCategory.MINIBOSS -> Config.starMobMiniboss
            MobCategory.CHONK -> Config.starMobChonk
            MobCategory.SKELETON_MASTER -> Config.starMobSm
            MobCategory.FEL -> Config.starMobFel
            MobCategory.REGULAR -> Config.starMobRegular
        }
    }

    fun colorFor(category: MobCategory): Int = when {
        !Config.starMobEspByType -> Config.starMobColor
        category == MobCategory.SHADOW_ASSASSIN -> Config.starMobShadowAssassinColor
        category == MobCategory.MINIBOSS -> Config.starMobMinibossColor
        category == MobCategory.CHONK -> Config.starMobChonkColor
        category == MobCategory.SKELETON_MASTER -> Config.starMobSmColor
        category == MobCategory.FEL -> Config.starMobFelColor
        else -> Config.starMobColor
    }

    private fun getHeight(entity: Entity, category: MobCategory): Double = when (category) {
        MobCategory.CHONK -> if (entity is LivingEntity && entity.name.string.contains("Withermancer", ignoreCase = true)) 3.0 else 2.0
        MobCategory.FEL -> if (entity.isInvisible) 0.8 else 3.0
        MobCategory.SHADOW_ASSASSIN -> if (!Config.starMobShowFullShadow && entity.isInvisible) 0.8 else 2.0
        else -> 2.0
    }

    fun shouldForceGlow(entity: Entity): Boolean =
        Config.starMobEspEnabled && DungeonContext.inDungeon && starMobs.containsKey(entity.id) &&
            isCategoryEnabled(starMobs[entity.id] ?: MobCategory.REGULAR)

    fun glowColorFor(entity: Entity): Int {
        if (!shouldForceGlow(entity)) return Int.MIN_VALUE
        return colorFor(starMobs[entity.id] ?: MobCategory.REGULAR)
    }

    private fun queueBoxes() {
        if (!Config.starMobEspEnabled || !DungeonContext.inDungeon) return
        val level = Minecraft.getInstance().level ?: return
        for ((id, category) in starMobs) {
            if (!isCategoryEnabled(category)) continue
            val entity = level.getEntity(id) ?: continue
            val pos = entity.position()
            val height = getHeight(entity, category)
            val color = colorFor(category)
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val halfW = 0.4
            val minX = pos.x - halfW
            val maxX = pos.x + halfW
            val minY = pos.y
            val maxY = pos.y + height
            val minZ = pos.z - halfW
            val maxZ = pos.z + halfW

            val fillA = Config.starMobFillAlpha.toFloat()
            val thickness = (Config.starMobLineWidth * 0.007).coerceIn(0.01, 0.08)
            val phase = Config.starMobEspThroughWalls

            if (fillA > 0f) {
                WorldBoxRenderer.queueFilled(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, fillA, throughWalls = phase)
            }
            WorldBoxRenderer.queueOutline(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.95f, thickness = thickness, throughWalls = phase)
        }
    }

    fun resetRun() {
        starMobs.clear()
        pendingStands.clear()
    }
}
