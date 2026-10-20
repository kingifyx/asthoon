package com.asthoonlite.dungeon

import com.asthoonlite.AsthoonLite
import com.asthoonlite.QuietMode
import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.api.FloorType
import com.asthoonlite.render.fillRoundedRect
import com.asthoonlite.render.outlineRoundedRect
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.util.regex.Pattern

object BossSplitsHud : HudElement {

    data class DualTime(val millis: Long, val ticks: Long) {
        companion object {
            fun now(): DualTime = DualTime(System.currentTimeMillis(), DungeonServerTick.current)
        }
        operator fun minus(other: DualTime): DualDiff =
            DualDiff(maxOf(0L, millis - other.millis), maxOf(0L, ticks - other.ticks))
    }

    data class DualDiff(val millis: Long, val ticks: Long) {
        fun format(): String {
            val totalSec = millis / 1000.0
            val timeStr = if (totalSec >= 60.0) {
                val m = (totalSec / 60).toInt()
                val s = totalSec % 60
                String.format("%d:%04.1f", m, s)
            } else {
                String.format("%.1fs", totalSec)
            }
            val tickSec = ticks / 20.0
            val tickStr = if (tickSec >= 60.0) {
                val tm = (tickSec / 60).toInt()
                val ts = tickSec % 60
                String.format("%d:%04.1f", tm, ts)
            } else {
                String.format("%.1fs", tickSec)
            }
            return "$timeStr §7($tickStr)§r"
        }
    }

    data class Split(val name: String, var start: DualTime? = null, var end: DualTime? = null) {
        fun elapsed(): DualDiff? {
            val s = start ?: return null
            val e = end ?: DualTime.now()
            return e - s
        }
    }

    private val splits = linkedMapOf<String, Split>()
    private var bossStartTime: DualTime? = null
    private var bossEndTime: DualTime? = null

    // P3 Section tracking
    private var p3Section = 1
    private val taskPattern = Pattern.compile("^(\\w+) (?:activated|completed) a (terminal|lever|device)! \\((\\d)/\\d\\)$")

    fun register() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(AsthoonLite.MOD_ID, "boss_splits_hud"), this
        )
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }

        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) {
                val clean = ChatFormatting.stripFormatting(text.string)?.trim() ?: ""
                onChat(clean)
            }
            true
        }
    }

    fun reset() {
        splits.clear()
        bossStartTime = null
        bossEndTime = null
        p3Section = 1
    }

    private fun markStart(name: String) {
        val split = splits.getOrPut(name) { Split(name) }
        if (split.start == null) split.start = DualTime.now()
    }

    private fun markEnd(name: String) {
        val split = splits.getOrPut(name) { Split(name) }
        if (split.end == null) {
            if (split.start == null) split.start = DualTime.now()
            split.end = DualTime.now()
        }
    }

    private fun onChat(message: String) {
        if (!DungeonContext.inDungeon) return

        when {
            // Run start
            message.contains("Here, I found this map when I first entered the dungeon") ||
            message.contains("The Dungeon starts in 1 second") -> {
                reset()
                markStart("Blood Open")
            }

            // Blood Clear (Watcher finished) - check BEFORE generic Watcher dialog
            message.contains("You have proven yourself. You may pass.") -> {
                markEnd("Blood Clear")
                markStart("Blood Done")
            }

            // Blood Open (Blood door opened or Watcher dialogue started)
            message.contains("The BLOOD DOOR has been opened!") ||
            message.startsWith("[BOSS] The Watcher:") -> {
                if (splits["Blood Clear"]?.start == null) {
                    markEnd("Blood Open")
                    markStart("Blood Clear")
                }
            }

            // Blood Done / Boss Entry (any floor boss message)
            message.startsWith("[BOSS] Maxor: WELL! WELL! WELL!") ||
            message.startsWith("[BOSS] Bonzo:") ||
            message.startsWith("[BOSS] Scarf:") ||
            message.startsWith("[BOSS] The Professor:") ||
            message.startsWith("[BOSS] Thorn:") ||
            message.startsWith("[BOSS] Livid:") ||
            message.startsWith("[BOSS] Sadan:") -> {
                markEnd("Blood Done")
                if (message.startsWith("[BOSS] Maxor:")) {
                    bossStartTime = DualTime.now()
                    markStart("P1")
                }
            }

            // P1 End / P2 (Storm) Start
            message.startsWith("[BOSS] Storm: Pathetic Maxor, just like expected.") -> {
                markEnd("P1")
                markStart("P2")
            }

            // P2 End / Waiting P3 Start
            message.startsWith("[BOSS] Storm: I should have known that I stood no chance.") -> {
                markEnd("P2")
                markStart("Wait P3")
            }

            // Wait P3 End / P3 (Goldor) Start
            message.startsWith("[BOSS] Goldor: Who dares trespass into my domain?") -> {
                markEnd("Wait P3")
                p3Section = 1
                markStart("P3")
                markStart("S1")
            }

            // P3 Sections
            message.contains("The gate has been destroyed!") -> {
                when (p3Section) {
                    1 -> { markEnd("S1"); p3Section = 2; markStart("S2") }
                    2 -> { markEnd("S2"); p3Section = 3; markStart("S3") }
                    3 -> { markEnd("S3"); p3Section = 4; markStart("S4") }
                }
            }

            // P3 End / P4 Start
            message.contains("The Core entrance is opening!") -> {
                markEnd("S4")
                markEnd("P3")
                markStart("P4")
            }

            // P4 End / P5 Start (or Boss End on F7)
            message.startsWith("[BOSS] Necron: You went further than any human before") ||
            message.startsWith("[BOSS] Necron: All this, for nothing...") -> {
                markEnd("P4")
                if (DungeonContext.floor == FloorType.M7) {
                    markStart("P5")
                } else {
                    bossEndTime = DualTime.now()
                }
            }

            // Run End / Kaeman defeat
            message.contains("> EXTRA STATS <") ||
            message.contains("Defeated Kaeman in") ||
            message.contains("Defeated Necron in") -> {
                markEnd("P5")
                if (bossEndTime == null) bossEndTime = DualTime.now()
            }
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (QuietMode.suppressing()) return
        if (!Config.bossSplitsEnabled || !DungeonContext.inDungeon) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return

        val lines = ArrayList<String>()

        splits["Blood Open"]?.elapsed()?.let { lines += "§4Blood Open: §f${it.format()}" }
        splits["Blood Clear"]?.elapsed()?.let { lines += "§cBlood Clear: §f${it.format()}" }
        splits["Blood Done"]?.elapsed()?.let { lines += "§dBlood Done: §f${it.format()}" }
        splits["P1"]?.elapsed()?.let { lines += "§5P1 (Maxor): §f${it.format()}" }
        splits["P2"]?.elapsed()?.let { lines += "§9P2 (Storm): §f${it.format()}" }
        splits["Wait P3"]?.elapsed()?.let { lines += "§6Wait P3: §f${it.format()}" }

        val p3Diff = splits["P3"]?.elapsed()
        if (p3Diff != null) {
            lines += "§eP3 (Goldor): §f${p3Diff.format()}"
            splits["S1"]?.elapsed()?.let { lines += "  §7S1: §f${it.format()}" }
            splits["S2"]?.elapsed()?.let { lines += "  §7S2: §f${it.format()}" }
            splits["S3"]?.elapsed()?.let { lines += "  §7S3: §f${it.format()}" }
            splits["S4"]?.elapsed()?.let { lines += "  §7S4: §f${it.format()}" }
        }

        splits["P4"]?.elapsed()?.let { lines += "§cP4 (Core): §f${it.format()}" }
        splits["P5"]?.elapsed()?.let { lines += "§bP5 (Kaeman): §f${it.format()}" }

        val bStart = bossStartTime
        if (bStart != null) {
            val bEnd = bossEndTime ?: DualTime.now()
            val total = bEnd - bStart
            lines += "§aTotal Boss: §f${total.format()}"
        }

        if (lines.isEmpty()) return

        val scale = Config.bossSplitsScale.coerceIn(0.5f, 3.0f)
        val pose = context.pose()
        pose.pushMatrix()

        val baseX = if (Config.bossSplitsX >= 0) Config.bossSplitsX.toFloat() else 10f
        val baseY = if (Config.bossSplitsY >= 0) Config.bossSplitsY.toFloat() else 140f
        pose.translate(baseX, baseY)
        pose.scale(scale, scale)

        val font = mc.font
        var maxWidth = 0
        for (line in lines) {
            maxWidth = maxOf(maxWidth, font.width(line))
        }

        var curY = 0
        for (line in lines) {
            context.text(font, line, 0, curY, 0xFFFFFFFF.toInt())
            curY += font.lineHeight + 2
        }

        pose.popMatrix()
    }
}
