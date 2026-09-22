package com.asthoonlite.dungeon

import com.asthoonlite.config.Config
import com.asthoonlite.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/**
 * F7 devices based on the supplied Noamm/Devonian implementations:
 * - Auto I4 / Sharpshooter uses the nine fixed emerald positions and the
 *   same dev-room target vector as Noamm's I4Helper.
 * - Auto Simon Says reconstructs the five-button sequence from the
 *   sea-lantern updates in the four-by-four device.
 */
object F7Devices {
    private val devBlocks = listOf(
        BlockPos(68, 130, 50), BlockPos(66, 130, 50), BlockPos(64, 130, 50),
        BlockPos(68, 128, 50), BlockPos(66, 128, 50), BlockPos(64, 128, 50),
        BlockPos(68, 126, 50), BlockPos(66, 126, 50), BlockPos(64, 126, 50)
    )

    private val ssObsidians = (120..123).flatMap { y -> (92..95).map { z -> BlockPos(111, y, z) } }
    private val ssButtons = (120..123).flatMap { y -> (92..95).map { z -> BlockPos(110, y, z) } }
    private val ssStart = BlockPos(110, 121, 91)

    private var stormStarted = false
    private val emeraldSeen = HashSet<BlockPos>()
    private var activeEmerald: BlockPos? = null
    private var lastI4Click = 0L

    private var lastSSClick = 0L
    private var lastSSStartClick = 0L
    private var lastSSState = BooleanArray(ssObsidians.size)
    private var ssLastClientTick = -1L
    private var skipOver = false
    private var i1Started = false
    private var i1StartTick = -1L
    private val ssSequence = ArrayList<BlockPos>()
    private val ssButtonCheck = BlockPos(110, 120, 93)

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (!overlay) onChat(ChatFormatting.stripFormatting(text.string) ?: "")
            true
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
    }

    private fun onChat(msg: String) {
        if (msg == "[BOSS] Storm: I should have known that I stood no chance.") {
            stormStarted = true
            emeraldSeen.clear()
            activeEmerald = null
        }
        if (msg == "[BOSS] Goldor: Who dares trespass into my domain?") {
            resetSimonState()
        }
    }

    private fun tick() {
        if (!DungeonContext.inDungeon || !Config.inAnyDeviceFeatureEnabled()) {
            resetDeviceStateOnly()
            return
        }
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return

        if (Config.autoI4Enabled && stormStarted) tickI4(mc, level, player)
        if (Config.blockWrongDeviceClicks || Config.autoSimonSaysEnabled || Config.instantSimonSaysEnabled) updateSimonSequence(level, player)
        if (Config.instantSimonSaysEnabled) tickI1(mc, level, player)
        if (Config.autoSimonSaysEnabled) tickSimon(mc, level, player)
    }

    private fun tickI4(mc: Minecraft, level: net.minecraft.client.multiplayer.ClientLevel, player: net.minecraft.client.player.LocalPlayer) {
        if (player.distanceToSqr(Vec3(63.0, 127.0, 35.0)) > 20.0) return
        val emerald = devBlocks.firstOrNull { level.getBlockState(it).block == Blocks.EMERALD_BLOCK }
            ?: return
        if (emerald in emeraldSeen && activeEmerald == emerald) return

        activeEmerald = emerald
        emeraldSeen.add(emerald)
        if (System.currentTimeMillis() - lastI4Click < 45L) return
        shootI4(mc, player, emerald)
    }

    private fun shootI4(mc: Minecraft, player: net.minecraft.client.player.LocalPlayer, pos: BlockPos) {
        val i = devBlocks.indexOf(pos).coerceAtLeast(0)
        val col = i % 3
        val row = i / 3
        val targetX = when (col) {
            0 -> 67.5
            2 -> 65.5
            else -> 66.5
        }
        val target = Vec3(targetX, 131.0 - 2.0 * row, 50.0)
        lookAt(player, target)
        val hit = BlockHitResult(target, Direction.SOUTH, pos, false)
        mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        lastI4Click = System.currentTimeMillis()
    }

    /**
     * I1 is deliberately independent of Auto Simon Says / Auto Start SS.
     * It first moves the camera to the Simon Says start button and clicks it,
     * then hands the device buttons to the same target-selection path used by
     * Secret Aura.  This makes I1 work by itself instead of only appearing to
     * work when the other SS toggles are also enabled.
     */
    /**
     * I1 is literally Secret Aura's interaction primitive. It presses the
     * Simon start button once, latches that start, then uses the same
     * useItemOn interaction on the expected button as the lantern sequence
     * arrives. It never rotates the camera and never presses the start button
     * again while the round is active.
     */
    private fun tickI1(mc: Minecraft, level: net.minecraft.client.multiplayer.ClientLevel, player: net.minecraft.client.player.LocalPlayer) {
        if (player.distanceToSqr(Vec3(110.5, 121.5, 93.5)) > 49.0) return

        if (!i1Started) {
            if (ssSequence.isNotEmpty()) {
                i1Started = true
            } else {
                val interacted = SecretAura.tickSimonLike(ssStart, ssButtons, false)
                if (interacted) {
                    i1Started = true
                    i1StartTick = DungeonServerTick.current
                }
                return
            }
        }

        val expected = ssSequence.firstOrNull() ?: return
        SecretAura.tickSimonLike(ssStart, listOf(expected), true)
    }

    private fun updateSimonSequence(level: net.minecraft.client.multiplayer.ClientLevel, player: net.minecraft.client.player.LocalPlayer) {
        if (player.distanceToSqr(Vec3(110.5, 121.5, 93.5)) > 49.0) return

        if (level.getBlockState(ssButtonCheck).block == Blocks.AIR) {
            if (ssSequence.isEmpty()) lastSSState = BooleanArray(ssObsidians.size)
            return
        }
        if (level.getBlockState(ssButtonCheck).block == Blocks.STONE_BUTTON && ssSequence.isEmpty()) {
            skipOver = true
        }

        val now = BooleanArray(ssObsidians.size)
        ssObsidians.forEachIndexed { index, pos ->
            now[index] = level.getBlockState(pos).block == Blocks.SEA_LANTERN
        }

        now.forEachIndexed { index, active ->
            if (!active || lastSSState[index]) return@forEachIndexed
            val button = ssObsidians[index].west()

            // Direct port of NoammAddons' SS-skip queue behavior.
            if (!skipOver && ssSequence.size == 2) {
                ssSequence.removeAt(0)
            }
            if (ssSequence.size < 5 && !ssSequence.contains(button)) {
                ssSequence.add(button)
            }
        }
        lastSSState = now
    }

    private fun tickSimon(mc: Minecraft, level: net.minecraft.client.multiplayer.ClientLevel, player: net.minecraft.client.player.LocalPlayer) {
        if (player.distanceToSqr(Vec3(110.5, 121.5, 93.5)) > 49.0) return

        if (Config.autoSimonSaysStart && level.getBlockState(ssStart).block == Blocks.STONE_BUTTON &&
            System.currentTimeMillis() - lastSSStartClick > 250L) {
            SecretAura.instantInteract(ssStart)
            lastSSStartClick = System.currentTimeMillis()
            return
        }

        val expected = ssSequence.firstOrNull() ?: return
        if (level.getBlockState(expected).block != Blocks.STONE_BUTTON) return
        if (ssLastClientTick == DungeonServerTick.current) return
        if (System.currentTimeMillis() - lastSSClick < 100L) return

        SecretAura.instantInteract(expected)
        ssSequence.removeFirst()
        lastSSClick = System.currentTimeMillis()
        ssLastClientTick = DungeonServerTick.current
    }

    /** Called after an allowed manual interaction. Uses NoammAddons' queue model. */
    fun onSimonClick(pos: BlockPos) {
        if (!DungeonContext.inDungeon) return
        if (pos == ssStart) {
            resetSimonState()
            return
        }
        if (pos !in ssButtons) return
        val expected = ssSequence.firstOrNull() ?: return

        if (pos != expected) {
            if (ssSequence.size == 3 && ssSequence.getOrNull(1) == pos) {
                ssSequence.removeAt(1)
                if (ssSequence.isNotEmpty()) ssSequence.removeAt(0)
            }
            return
        }

        if (ssLastClientTick == DungeonServerTick.current) return
        ssSequence.removeFirst()
        ssLastClientTick = DungeonServerTick.current
    }

    /** Exact NoammAddons-style pre-interaction protection. */
    fun shouldBlockSimonClick(pos: BlockPos): Boolean {
        if (!Config.blockWrongDeviceClicks || !DungeonContext.inDungeon) return false
        val player = Minecraft.getInstance().player ?: return false
        if (player.isCrouching || pos !in ssButtons) return false
        val expected = ssSequence.firstOrNull() ?: return false
        return pos != expected
    }

    private fun lookAt(player: net.minecraft.client.player.LocalPlayer, target: Vec3) {
        val eye = player.eyePosition
        val d = target.subtract(eye)
        val horizontal = sqrt(d.x * d.x + d.z * d.z)
        player.yRot = Math.toDegrees(atan2(-d.x, d.z)).toFloat()
        player.xRot = Math.toDegrees(atan2(-d.y, horizontal)).toFloat()
    }

    private fun resetDeviceStateOnly() {
        stormStarted = false
        emeraldSeen.clear()
        activeEmerald = null
        resetSimonState()
    }

    private fun resetSimonState() {
        ssSequence.clear()
        i1Started = false
        i1StartTick = -1L
        lastSSState = BooleanArray(ssObsidians.size)
        skipOver = false
        ssLastClientTick = -1L
    }

    fun reset() {
        resetDeviceStateOnly()
        lastI4Click = 0L
        lastSSClick = 0L
        lastSSStartClick = 0L
    }
}

private fun Config.inAnyDeviceFeatureEnabled(): Boolean =
    autoI4Enabled || autoSimonSaysEnabled || instantSimonSaysEnabled
