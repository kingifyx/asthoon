package com.asthoonlite.dungeon

import com.asthoonlite.AsthoonLite
import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.api.*
import com.asthoonlite.dungeon.api.mapEnums.CheckmarkTypes
import com.asthoonlite.dungeon.api.mapEnums.DoorTypes
import com.asthoonlite.dungeon.api.mapEnums.RoomTypes
import com.asthoonlite.dungeon.map.DungeonMapScanner
import com.asthoonlite.dungeon.map.DungeonScanner
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import kotlin.math.cos
import kotlin.math.sin

object DungeonMap : HudElement {
    private const val BASE_SIZE = 100f
    private const val GRID_SIZE = 6

    fun register() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(AsthoonLite.MOD_ID, "dungeon_map"),
            this
        )
    }

    fun resetRun() {
        DungeonScanner.reset()
        DungeonMapScanner.reset()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!Config.dungeonMapEnabled || !DungeonContext.inDungeon) return
        if (Config.dungeonMapHideInBoss && DungeonContext.inBoss) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        if (!Config.dungeonMapAlwaysShow && !isHoldingMap(player)) return

        val scale = Config.dungeonMapScale.coerceIn(1f, 6f)
        val mapW = (BASE_SIZE * scale).toInt()
        val mapH = (BASE_SIZE * scale).toInt()
        val startX = Config.dungeonMapX
        val startY = Config.dungeonMapY

        context.pose().pushMatrix()
        context.pose().translate(startX.toFloat(), startY.toFloat())

        // Modern background panel with clean border
        context.fill(-2, -2, mapW + 2, mapH + 2, 0x99000000.toInt())
        context.fill(0, 0, mapW, mapH, 0xDD0D1F35.toInt())

        val cellGap = 3f * (scale / 2f).coerceAtLeast(1f)
        val cellW = (mapW - cellGap * (GRID_SIZE + 1)) / GRID_SIZE
        val cellH = (mapH - cellGap * (GRID_SIZE + 1)) / GRID_SIZE

        fun cellX(gx: Int): Float = cellGap + gx * (cellW + cellGap)
        fun cellY(gz: Int): Float = cellGap + gz * (cellH + cellGap)

        // 1. Draw Rooms
        for (gz in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                val idx = gz * 6 + gx
                val room = DungeonScanner.rooms.getOrNull(idx)

                val x0 = cellX(gx)
                val y0 = cellY(gz)

                if (room == null || !room.explored) {
                    if (Config.dungeonMapFullGrid && room != null) {
                        val color = dim(colorForRoom(room.type), 0.35f)
                        context.fill(x0.toInt(), y0.toInt(), (x0 + cellW).toInt(), (y0 + cellH).toInt(), color)
                    } else if (Config.dungeonMapFullGrid) {
                        context.fill(x0.toInt(), y0.toInt(), (x0 + cellW).toInt(), (y0 + cellH).toInt(), 0x33404040)
                    }
                    continue
                }

                val color = colorForRoom(room.type)
                context.fill(x0.toInt(), y0.toInt(), (x0 + cellW).toInt(), (y0 + cellH).toInt(), color)

                // Join components of same room
                if (gx + 1 < GRID_SIZE) {
                    val right = DungeonScanner.rooms.getOrNull(gz * 6 + gx + 1)
                    if (right === room) {
                        val jx0 = x0 + cellW
                        val jy0 = y0
                        context.fill(jx0.toInt(), jy0.toInt(), (jx0 + cellGap + 1).toInt(), (jy0 + cellH).toInt(), color)
                    }
                }
                if (gz + 1 < GRID_SIZE) {
                    val down = DungeonScanner.rooms.getOrNull((gz + 1) * 6 + gx)
                    if (down === room) {
                        val jx0 = x0
                        val jy0 = y0 + cellH
                        context.fill(jx0.toInt(), jy0.toInt(), (jx0 + cellW).toInt(), (jy0 + cellGap + 1).toInt(), color)
                    }
                }
            }
        }

        // 2. Draw Doors
        for (door in DungeonScanner.doors) {
            if (door == null) continue
            val r1 = door.roomComp1
            val r2 = door.roomComp2
            val isHorizontal = r1.z == r2.z

            val color = when (door.type) {
                DoorTypes.WITHER -> 0xFF000000.toInt()
                DoorTypes.BLOOD -> 0xFFFF2222.toInt()
                DoorTypes.ENTRANCE -> 0xFF148500.toInt()
                DoorTypes.NORMAL -> if (door.opened) 0xFF5C340E.toInt() else continue
            }

            if (isHorizontal) {
                val minX = minOf(r1.x, r2.x)
                val gz = r1.z
                val dx0 = cellX(minX) + cellW
                val dy0 = cellY(gz) + cellH * 0.35f
                val dx1 = dx0 + cellGap
                val dy1 = dy0 + cellH * 0.3f
                context.fill(dx0.toInt(), dy0.toInt(), (dx1 + 1).toInt(), dy1.toInt(), color)
            } else {
                val minZ = minOf(r1.z, r2.z)
                val gx = r1.x
                val dx0 = cellX(gx) + cellW * 0.35f
                val dy0 = cellY(minZ) + cellH
                val dx1 = dx0 + cellW * 0.3f
                val dy1 = dy0 + cellGap
                context.fill(dx0.toInt(), dy0.toInt(), dx1.toInt(), (dy1 + 1).toInt(), color)
            }
        }

        // 3. Draw Room Text / Checkmarks / Secrets
        val visitedRooms = HashSet<DungeonRoom>()
        for (room in DungeonScanner.rooms) {
            if (room == null || !room.explored || !visitedRooms.add(room)) continue
            val centerComp = room.comps.minByOrNull { it.cx + it.cz } ?: continue
            val gx = centerComp.cx / 2
            val gz = centerComp.cz / 2
            val cx = cellX(gx) + cellW * 0.5f
            val cy = cellY(gz) + cellH * 0.5f

            // Checkmark
            if (Config.dungeonMapShowCheckmarks && room.type != RoomTypes.ENTRANCE) {
                if (!(Config.dungeonMapDontRenderFairyCheckmark && room.type == RoomTypes.FAIRY)) {
                    val (checkStr, checkCol) = when (room.checkmark) {
                        CheckmarkTypes.GREEN -> "✔" to 0xFF00FF00.toInt()
                        CheckmarkTypes.WHITE -> "✔" to 0xFFFFFFFF.toInt()
                        CheckmarkTypes.FAILED -> "✖" to 0xFFFF2222.toInt()
                        else -> null to 0
                    }
                    if (checkStr != null) {
                        context.centeredText(mc.font, checkStr, cx.toInt(), (cy - 4).toInt(), checkCol)
                    }
                }
            }

            // Room Name
            if (Config.dungeonMapShowNames && room.name != null && room.type != RoomTypes.ENTRANCE && room.type != RoomTypes.BLOOD && room.type != RoomTypes.FAIRY) {
                val skipYellow = Config.dungeonMapDontRenderYellowName && room.type == RoomTypes.YELLOW
                val skipCommon = Config.dungeonMapDontRenderCommonNames && room.type == RoomTypes.NORMAL
                if (!skipYellow && !skipCommon) {
                    val shortName = abbreviateName(room.name!!)
                    val nameY = if (Config.dungeonMapShowCheckmarks && room.checkmark != CheckmarkTypes.NONE) cy + 3 else cy - 3
                    context.centeredText(mc.font, shortName, cx.toInt(), nameY.toInt(), 0xFFE0E0E0.toInt())
                }
            }

            // Secret Count
            if (Config.dungeonMapShowSecrets && room.totalSecrets > 0) {
                val secStr = if (room.secretsCompleted >= 0) "${room.secretsCompleted}/${room.totalSecrets}" else "${room.totalSecrets}s"
                context.centeredText(mc.font, secStr, cx.toInt(), (cy + cellH * 0.3f).toInt(), 0xFFFFAA00.toInt())
            }
        }

        // 4. Draw Teammate & Self Player Icons
        val selfGx = ((player.x - cornerStart.x - halfRoomSize) / roomDoorCombinedSize).toFloat().coerceIn(0f, 5f)
        val selfGz = ((player.z - cornerStart.z - halfRoomSize) / roomDoorCombinedSize).toFloat().coerceIn(0f, 5f)
        val selfPx = cellX(0) + selfGx * (cellW + cellGap) + cellW * 0.5f
        val selfPz = cellY(0) + selfGz * (cellH + cellGap) + cellH * 0.5f

        val showNames = Config.dungeonMapPlayerNames && (!Config.dungeonMapNamesOnlyLeap || isHoldingLeap(player))

        // Self icon
        if (Config.dungeonMapMarkerSelf || !Config.dungeonMapPlayerHeads) {
            drawPlayerArrow(context, selfPx, selfPz, player.yRot.toDouble(), scale * Config.dungeonMapMarkerScale, 0xFF00FF00.toInt())
        } else {
            val selfSkin = player.skin
            drawPlayerHead(context, selfSkin, selfPx, selfPz, player.yRot.toDouble(), scale, 0xFF00FF00.toInt())
        }

        // Teammates from map scanner icons
        for (icon in DungeonMapScanner.playerIcons) {
            val roomCenter = DungeonMapScanner.roomSize.toFloat() / (2 * DungeonMapScanner.roomGap)
            val tx = cellX(0) + (icon.x.toFloat() / 2f - roomCenter) * (cellW + cellGap) + cellW * 0.5f
            val tz = cellY(0) + (icon.z.toFloat() / 2f - roomCenter) * (cellH + cellGap) + cellH * 0.5f
            val yawDeg = Math.toDegrees(icon.rot)
            val skin = getPlayerSkin(icon.name)

            if (Config.dungeonMapPlayerHeads && skin != null) {
                drawPlayerHead(context, skin, tx, tz, yawDeg, scale, 0xFF1E90FF.toInt())
            } else {
                drawPlayerArrow(context, tx, tz, yawDeg, scale * 0.8f * Config.dungeonMapMarkerScale, 0xFF1E90FF.toInt())
            }

            if (showNames && icon.name != null) {
                val shortName = icon.name.take(4)
                context.centeredText(mc.font, shortName, tx.toInt(), (tz + 7).toInt(), 0xFFFFFFFF.toInt())
            }
        }

        context.pose().popMatrix()
    }

    private fun getPlayerSkin(name: String?): net.minecraft.world.entity.player.PlayerSkin? {
        if (name.isNullOrBlank()) return null
        val mc = Minecraft.getInstance()
        val info = mc.connection?.onlinePlayers?.firstOrNull { it.profile.name.equals(name, true) }
        return info?.skin
    }

    private fun isHoldingLeap(player: net.minecraft.world.entity.player.Player): Boolean {
        val mainName = player.mainHandItem.hoverName.string
        val offName = player.offhandItem.hoverName.string
        return mainName.contains("Spirit Leap", true) || offName.contains("Spirit Leap", true)
    }

    private fun drawPlayerHead(
        context: GuiGraphicsExtractor,
        skin: net.minecraft.world.entity.player.PlayerSkin,
        x: Float, z: Float,
        yawDeg: Double,
        scale: Float,
        borderColor: Int
    ) {
        val headSize = (9 * (scale / 1.66f) * Config.dungeonMapPlayerHeadScale).toInt().coerceIn(8, 24)
        val half = headSize / 2
        val hx = (x - half).toInt()
        val hz = (z - half).toInt()

        context.fill(hx - 1, hz - 1, hx + headSize + 1, hz + headSize + 1, borderColor)
        net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(context, skin, hx, hz, headSize)

        val yaw = Math.toRadians(yawDeg)
        val dx = (-sin(yaw) * (half + 2)).toInt()
        val dz = (cos(yaw) * (half + 2)).toInt()
        context.fill((x + dx - 1).toInt(), (z + dz - 1).toInt(), (x + dx + 1).toInt(), (z + dz + 1).toInt(), 0xFFFFFFFF.toInt())
    }

    private fun abbreviateName(name: String): String = when {
        name.length <= 6 -> name
        name.equals("Three Weirdos", true) -> "Weirdos"
        name.equals("Higher Lower", true) || name.equals("Higher Blaze", true) -> "Blaze"
        name.equals("Water Board", true) -> "Water"
        name.equals("Ice Path", true) || name.equals("Ice Fill", true) -> "Ice"
        name.equals("Creeper Beams", true) -> "Beams"
        name.equals("Teleport Maze", true) -> "Maze"
        else -> name.take(5)
    }

    private fun drawPlayerArrow(
        context: GuiGraphicsExtractor,
        x: Float, z: Float,
        yawDeg: Double,
        scale: Float,
        color: Int
    ) {
        val arrow = (4.0 * scale).coerceAtLeast(3.0)
        context.pose().pushMatrix()
        context.pose().translate(x, z)
        context.pose().rotate(Math.toRadians(yawDeg).toFloat())
        for (row in (-arrow * 0.65).toInt()..arrow.toInt()) {
            val half = ((arrow - row) * 0.4).toInt()
            context.fill(-half, row, half + 1, row + 1, color)
        }
        context.fill(-1, -1, 2, 2, 0xFF000000.toInt())
        context.pose().popMatrix()
    }

    private fun colorForRoom(type: RoomTypes): Int = when (type) {
        RoomTypes.ENTRANCE -> 0xFF148500.toInt()
        RoomTypes.NORMAL   -> 0xFF6B3A11.toInt()
        RoomTypes.FAIRY    -> 0xFFE000FF.toInt()
        RoomTypes.BLOOD    -> 0xFFFF2222.toInt()
        RoomTypes.PUZZLE   -> 0xFF750085.toInt()
        RoomTypes.TRAP     -> 0xFFD87F33.toInt()
        RoomTypes.YELLOW   -> 0xFFFEDF00.toInt()
        RoomTypes.RARE     -> 0xFFECEFF1.toInt()
        RoomTypes.UNKNOWN  -> 0xFF414141.toInt()
    }

    private fun dim(argb: Int, factor: Float): Int {
        val r = (((argb ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xEE shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun isHoldingMap(player: net.minecraft.world.entity.player.Player): Boolean {
        val main = player.mainHandItem
        if (main.get(DataComponents.MAP_ID) != null) return true
        val off = player.offhandItem
        return off.get(DataComponents.MAP_ID) != null
    }
}
