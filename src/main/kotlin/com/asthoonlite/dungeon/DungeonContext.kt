package com.asthoonlite.dungeon

import com.asthoonlite.dungeon.api.FloorType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.phys.Vec3
import kotlin.math.round

/** Keeps dungeon-only features from running on islands/normal SkyBlock areas. */
object DungeonContext {
    @Volatile var inDungeon: Boolean = false
        private set
    @Volatile var inBoss: Boolean = false
        private set
    var floor: FloorType = FloorType.None
        private set

    private var scoreboardMissingTicks = 0
    private val floorPattern = Regex("The Catacombs \\(([FM][1-7]|E)\\)", RegexOption.IGNORE_CASE)

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) onChat(ChatFormatting.stripFormatting(text.string) ?: "")
            true
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
    }

    private fun onChat(message: String) {
        when {
            message == "[NPC] Mort: Here, I found this map when I first entered the dungeon." -> activate()
            message.startsWith("[BOSS] Maxor:") ||
                message.startsWith("[BOSS] Storm:") ||
                message.startsWith("[BOSS] Goldor:") ||
                message.startsWith("[BOSS] Necron:") ||
                message.startsWith("[BOSS] Bonzo:") ||
                message.startsWith("[BOSS] Scarf:") ||
                message.startsWith("[BOSS] The Professor:") ||
                message.startsWith("[BOSS] Thorn:") ||
                message.startsWith("[BOSS] Livid:") ||
                message.startsWith("[BOSS] Sadan:") -> {
                activate()
                inBoss = true
            }
            message.startsWith("[BOSS] The Watcher:") ||
                message == "The BLOOD DOOR has been opened!" -> activate()
        }
    }

    private fun activate() {
        inDungeon = true
        scoreboardMissingTicks = 0
    }

    private fun tick() {
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (level == null || mc.player == null) {
            reset()
            return
        }

        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
        updateFromSidebar(sidebarLines(scoreboard, objective))
    }

    internal fun sidebarLines(scoreboard: Scoreboard, objective: Objective?): List<String> =
        objective?.let { scoreboard.listPlayerScores(it) }.orEmpty().filterNot { it.isHidden }.map { score ->
            val name = score.owner
            val line = PlayerTeam.formatNameForTeam(
                scoreboard.getPlayersTeam(name), score.ownerName()
            ).string
            ChatFormatting.stripFormatting(line) ?: line
        }

    internal fun updateFromSidebar(lines: List<String>) {
        val hasDungeonScoreboard = lines.any { line ->
            line.contains("The Catacombs", ignoreCase = true) ||
                line.contains("Completed Rooms:", ignoreCase = true) ||
                line.contains("Secrets Found:", ignoreCase = true)
        }

        if (hasDungeonScoreboard) {
            lines.firstNotNullOfOrNull { floorPattern.find(it) }
                ?.let { floor = FloorType.from(it.groupValues[1]) }
            activate()
        } else if (inDungeon) {
            scoreboardMissingTicks++
            // Allow short scoreboard refresh gaps, but leave the dungeon
            // shortly after the dungeon scoreboard disappears.
            if (scoreboardMissingTicks > 80) reset()
        }
    }

    fun roomKey(pos: Vec3): Pair<Int, Int> =
        round((pos.x + 185.0) / 32.0).toInt() to round((pos.z + 185.0) / 32.0).toInt()

    fun currentRoomKey(): Pair<Int, Int>? =
        Minecraft.getInstance().player?.let { roomKey(it.position()) }

    fun sameRoom(a: Vec3, b: Vec3): Boolean = roomKey(a) == roomKey(b)

    fun reset() {
        inDungeon = false
        inBoss = false
        floor = FloorType.None
        scoreboardMissingTicks = 0
    }
}
