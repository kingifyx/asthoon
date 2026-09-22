package com.asthoonlite.dungeon.map

import com.asthoonlite.dungeon.DungeonContext
import com.asthoonlite.dungeon.api.*
import com.asthoonlite.dungeon.api.mapEnums.CheckmarkTypes
import com.asthoonlite.dungeon.api.mapEnums.DoorTypes
import com.asthoonlite.dungeon.api.mapEnums.RoomTypes
import com.asthoonlite.utils.MathUtils
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapId
import kotlin.math.PI

object DungeonMapScanner {
    private const val COLOR_SIZE = 16384
    private const val SCAN = 128
    private const val ROOM_SPACING = 4
    var roomSize = -1
    var roomGap = -1
    var mapOffsetX = -1
    var mapOffsetZ = -1
    var mapWidth = -1
    var mapHeight = -1
    private val unscannedDoors = mutableSetOf<ComponentPosition>()
    private var lastMapId: MapId? = null
    var playerIcons = mutableListOf<PlayerIcon>()

    data class PlayerIcon(val x: Double, val z: Double, val rot: Double, val name: String?)

    fun reset() {
        roomSize = -1
        roomGap = -1
        mapOffsetX = -1
        mapOffsetZ = -1
        mapWidth = -1
        mapHeight = -1
        unscannedDoors.clear()
        for (x in 0..10) {
            for (z in (x and 1 xor 1)..10 step 2) {
                unscannedDoors.add(ComponentPosition(x, z))
            }
        }
        playerIcons.clear()
        lastMapId = null
    }

    private enum class MapColors(val color: Byte) {
        EMPTY(0),
        CHECK_WHITE(34),
        CHECK_GREEN(30),
        CHECK_FAIL(18),
        CHECK_UNKNOWN(119),

        ROOM_ENTRANCE(30),
        ROOM_NORMAL(63),
        ROOM_UNOPENED(85),
        ROOM_TRAP(62),
        ROOM_BOSS(74),
        ROOM_PUZZLE(66),
        ROOM_FAIRY(82),
        ROOM_BLOOD(18),

        DOOR_WITHER(119),
        DOOR_BLOOD(18);
    }

    private fun scanMapDimensions(colors: ByteArray): Boolean {
        var entranceIdx = 0
        var i = 0
        while (entranceIdx < colors.size && colors[entranceIdx] != MapColors.ROOM_ENTRANCE.color) {
            i++
            entranceIdx = ((i and 7) shl 4) + ((i shr 3) shl 11)
        }

        if (entranceIdx >= colors.size) return false

        var l = entranceIdx
        var r = entranceIdx
        while (l > 0 && colors[l - 1] == MapColors.ROOM_ENTRANCE.color) l--
        while (r < colors.size - 1 && colors[r + 1] == MapColors.ROOM_ENTRANCE.color) r++

        var t = entranceIdx
        var b = entranceIdx
        while (t >= SCAN && colors[t - SCAN] == MapColors.ROOM_ENTRANCE.color) t -= SCAN
        while (b + SCAN < colors.size && colors[b + SCAN] == MapColors.ROOM_ENTRANCE.color) b += SCAN

        l = l and 127
        r = r and 127
        t = t shr 7
        b = b shr 7
        roomSize = r - l + 1
        roomGap = roomSize + ROOM_SPACING

        mapOffsetX = l % roomGap
        mapOffsetZ = t % roomGap

        mapWidth = roomGap * 5 + roomSize
        mapHeight = roomGap * 5 + roomSize

        if (SCAN - mapWidth >= roomGap * 2) mapOffsetX += roomGap
        if (SCAN - mapHeight >= roomGap * 2) mapOffsetZ += roomGap

        return true
    }

    fun onMapPacket(packet: ClientboundMapItemDataPacket) {
        if (!DungeonContext.inDungeon) return
        val mapId = packet.mapId()
        val level = Minecraft.getInstance().level ?: return
        val mapState = level.getMapData(mapId) ?: return
        val colors = mapState.colors
        if (colors.size < COLOR_SIZE) return

        if (colors[0] != MapColors.EMPTY.color) lastMapId = mapId

        if (roomSize == -1 && !scanMapDimensions(colors)) return

        updateRooms(colors)
        updatePlayerIcons(packet.decorations().orElse(emptyList()))
    }

    private fun updatePlayerIcons(decorations: List<MapDecoration>) {
        if (decorations.isEmpty() || roomGap <= 0) return
        val icons = mutableListOf<PlayerIcon>()
        decorations.forEach { dec ->
            if (dec.type().value() == MapDecorationTypes.FRAME.value()) return@forEach
            val x = MathUtils.rescale(
                (dec.x().toDouble() + 128.0) * 0.5,
                mapOffsetX.toDouble(), (mapOffsetX + roomGap * 6).toDouble(),
                0.0, 12.0
            )
            val z = MathUtils.rescale(
                (dec.y().toDouble() + 128.0) * 0.5,
                mapOffsetZ.toDouble(), (mapOffsetZ + roomGap * 6).toDouble(),
                0.0, 12.0
            )
            val r = -(dec.rot() / 16.0 * 360.0 + 90.0) / 180.0 * PI
            val name = dec.name().map { it.string }.orElse(null)
            icons.add(PlayerIcon(x, z, r, name))
        }
        playerIcons = icons
    }

    private fun updateRooms(colors: ByteArray) {
        val visited = mutableSetOf<DungeonRoom>()
        for (idx in DungeonScanner.rooms.indices) {
            val room_ = DungeonScanner.rooms[idx]
            if (room_ != null && !visited.add(room_)) continue

            val x = idx % 6
            val z = idx / 6
            val mrx = mapOffsetX + x * roomGap
            val mrz = mapOffsetZ + z * roomGap
            val mcx = mrx + roomSize / 2 - 1
            val mcz = mrz + roomSize / 2 - 1 + 2
            val mridx = mrx + mrz * SCAN
            val mcidx = mcx + mcz * SCAN

            val roomCol = colors.getOrNull(mridx) ?: continue
            val centerCol = colors.getOrNull(mcidx) ?: continue

            if (roomCol == MapColors.EMPTY.color) continue

            val room: DungeonRoom
            if (room_ == null) {
                val comp = ComponentPosition(x * 2, z * 2)
                room = DungeonRoom(mutableListOf(comp.withWorld()), 0)
                DungeonScanner.addRoom(comp, room)
            } else room = room_

            room.type = when (roomCol) {
                MapColors.ROOM_ENTRANCE.color -> RoomTypes.ENTRANCE
                MapColors.ROOM_BLOOD.color -> RoomTypes.BLOOD
                MapColors.ROOM_UNOPENED.color ->
                    if (room.type == RoomTypes.UNKNOWN) RoomTypes.UNKNOWN
                    else room.type
                MapColors.ROOM_BOSS.color -> RoomTypes.YELLOW
                MapColors.ROOM_FAIRY.color -> RoomTypes.FAIRY
                MapColors.ROOM_NORMAL.color ->
                    if (room.type == RoomTypes.RARE) RoomTypes.RARE
                    else RoomTypes.NORMAL
                MapColors.ROOM_PUZZLE.color -> RoomTypes.PUZZLE
                MapColors.ROOM_TRAP.color -> RoomTypes.TRAP
                else -> RoomTypes.UNKNOWN
            }

            val nstate = roomCol != MapColors.ROOM_UNOPENED.color
            room.explored = nstate

            if (room.checkmark != CheckmarkTypes.GREEN) {
                room.checkmark = if (roomCol == centerCol) CheckmarkTypes.NONE
                else when (centerCol) {
                    MapColors.CHECK_WHITE.color -> CheckmarkTypes.WHITE
                    MapColors.CHECK_GREEN.color -> CheckmarkTypes.GREEN
                    MapColors.CHECK_FAIL.color -> CheckmarkTypes.FAILED
                    MapColors.CHECK_UNKNOWN.color ->
                        if (room.checkmark == CheckmarkTypes.NONE) CheckmarkTypes.NONE
                        else CheckmarkTypes.UNEXPLORED
                    else -> CheckmarkTypes.NONE
                }
            }
        }

        unscannedDoors.removeIf { comp ->
            val idx = comp.getDoorIdx()
            val mjx = mapOffsetX + (comp.x / 2) * roomGap + (comp.x and 1) * roomSize
            val mjz = mapOffsetZ + (comp.z / 2) * roomGap + (comp.z and 1) * roomSize
            val mdx = mjx + (comp.z and 1) * roomSize / 2
            val mdz = mjz + (comp.x and 1) * roomSize / 2
            val mjidx = mjx + mjz * SCAN
            val mdidx = mdx + mdz * SCAN

            val joinedCol = colors.getOrNull(mjidx) ?: return@removeIf false
            val doorCol = colors.getOrNull(mdidx) ?: return@removeIf false

            if (doorCol == MapColors.EMPTY.color) return@removeIf false

            if (joinedCol == doorCol) {
                DungeonScanner.doors[idx]?.also { d ->
                    d.rooms.forEach { it.doors.remove(d) }
                }
                DungeonScanner.doors[idx] = null
                val neighboring = comp.getNeighboringRooms()
                if (neighboring.size != 2) return@removeIf false
                return@removeIf DungeonScanner.mergeRooms(neighboring[0], neighboring[1])
            }

            val door = DungeonScanner.doors[idx] ?: let {
                val d = DungeonDoor(comp.withWorld())
                DungeonScanner.addDoor(d)
                d
            }

            return@removeIf when (doorCol) {
                MapColors.DOOR_WITHER.color -> {
                    door.type = DoorTypes.WITHER
                    door.opened = false
                    false
                }
                MapColors.DOOR_BLOOD.color -> {
                    door.type = DoorTypes.BLOOD
                    true
                }
                else -> {
                    door.type = DoorTypes.NORMAL
                    door.opened = true
                    true
                }
            }
        }
    }

    fun register() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }
}
