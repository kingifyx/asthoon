package com.asthoonlite.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/**
 * Deterministic kinematic trajectory predictor and solver for Minecraft living entity movement.
 * Replaces heuristic distance thresholds with forward physics simulation, blast vector inversion,
 * and closed-loop mid-air flight guidance.
 */
object KinematicTrajectory {

    // Minecraft entity physics constants
    const val GRAVITY = 0.08
    const val DRAG_AIR_VERTICAL = 0.98
    const val DRAG_AIR_HORIZONTAL = 0.91
    const val BLOCK_FRICTION_DEFAULT = 0.6f
    const val JUMP_VELOCITY = 0.42
    const val SPRINT_JUMP_BOOST = 0.20
    const val AIR_ACCELERATION_SPRINT = 0.026

    // Calibrated Bonzo blast constants from real dungeon captures
    const val BONZO_DETONATION_TICKS = 2 // Projectile impacts / detonates 2 ticks after use
    const val BONZO_IMPULSE_SPEED = 1.25 // ~25 bps impulse magnitude
    const val BONZO_BLAST_OFFSET = 1.35  // Effective blast center distance for knockback
    const val BONZO_UPWARD_VELOCITY = 0.42 // Jump-equivalent upward kick

    data class State(
        val x: Double,
        val y: Double,
        val z: Double,
        val vx: Double,
        val vy: Double,
        val vz: Double,
        val onGround: Boolean
    ) {
        val pos: Vec3 get() = Vec3(x, y, z)
        val vel: Vec3 get() = Vec3(vx, vy, vz)
        val speedH: Double get() = hypot(vx, vz)
        val bpsH: Double get() = speedH * 20.0
    }

    /**
     * Steps 1 tick of Minecraft entity physics forward in air.
     */
    fun stepAirborne(state: State, forwardInput: Double = 0.0, strafeInput: Double = 0.0, yaw: Float = 0f): State {
        var nvx = state.vx
        var nvz = state.vz
        if (abs(forwardInput) > 0.01 || abs(strafeInput) > 0.01) {
            val inputLen = hypot(strafeInput, forwardInput)
            val normFwd = forwardInput / max(inputLen, 1.0)
            val normStr = strafeInput / max(inputLen, 1.0)
            val rad = Math.toRadians(yaw.toDouble())
            val sin = sin(rad)
            val cos = cos(rad)
            val ax = (normStr * cos - normFwd * sin) * AIR_ACCELERATION_SPRINT
            val az = (normFwd * cos + normStr * sin) * AIR_ACCELERATION_SPRINT
            nvx += ax
            nvz += az
        }

        val nx = state.x + nvx
        val ny = state.y + state.vy
        val nz = state.z + nvz

        val postVx = nvx * DRAG_AIR_HORIZONTAL
        val postVz = nvz * DRAG_AIR_HORIZONTAL
        val postVy = (state.vy - GRAVITY) * DRAG_AIR_VERTICAL

        return State(nx, ny, nz, postVx, postVy, postVz, onGround = false)
    }

    /**
     * Predicts where an airborne flight arc will land at the target elevation [targetY].
     * Returns the projected landing position, the velocity at landing, and ticks until landing.
     */
    fun predictAirFlight(
        startPos: Vec3,
        startVel: Vec3,
        targetY: Double,
        forwardInput: Double = 1.0,
        strafeInput: Double = 0.0,
        yaw: Float = 0f,
        maxTicks: Int = 30
    ): Triple<Vec3, Vec3, Int> {
        val maxJumpReach = startPos.y + if (startVel.y > 0.0) (startVel.y * 5.0) else 0.0
        val effectiveTargetY = if (targetY <= maxJumpReach) targetY else startPos.y
        var cur = State(startPos.x, startPos.y, startPos.z, startVel.x, startVel.y, startVel.z, onGround = false)
        for (tick in 1..maxTicks) {
            val nxt = stepAirborne(cur, forwardInput, strafeInput, yaw)
            if (nxt.y <= effectiveTargetY && cur.y > effectiveTargetY) {
                val t = if (abs(nxt.y - cur.y) > 0.001) (cur.y - effectiveTargetY) / (cur.y - nxt.y) else 0.0
                val landX = cur.x + (nxt.x - cur.x) * t
                val landZ = cur.z + (nxt.z - cur.z) * t
                return Triple(Vec3(landX, effectiveTargetY, landZ), nxt.vel, tick)
            }
            cur = nxt
        }
        return Triple(cur.pos, cur.vel, maxTicks)
    }

    data class BonzoAimPlan(
        val isRedirection: Boolean,
        val shotYaw: Float,
        val shotPitch: Float,
        val destYaw: Float,
        val aimedImpactPos: Vec3,
        val expectedLaunchSpeedBps: Double,
        val jumpOnFire: Boolean
    )

    /**
     * Calculates the optimal Bonzo blast launch pitch to guarantee covering the required horizontal
     * distance [distH] and vertical rise [deltaY] under Minecraft air drag (0.91) and gravity (0.08).
     * Uses discrete tick simulation of vertical motion and horizontal impulse kinematics.
     */
    fun calculateOptimalBonzoPitch(
        distH: Double,
        deltaY: Double = 0.0,
        recordedPitch: Float = 0f,
        isRedirection: Boolean = false
    ): Float {
        // Analytical trajectory simulation:
        // Simulate flight duration T until player reaches destination elevation deltaY
        var curY = 0.0
        var curVy = 0.83 // jump boost (0.42) + bonzo knockback impulse (0.41)
        var flightTicks = 20
        for (t in 1..40) {
            curY += curVy
            curVy = (curVy - 0.08) * 0.98
            if (curY <= deltaY && t >= 8) {
                flightTicks = t
                break
            }
        }
        val tEff = flightTicks.coerceIn(12, 35)
        val decaySum = (1.0 - Math.pow(0.91, tEff.toDouble())) / 0.09
        val airSprintTravel = 0.288 * tEff
        val neededImpulseDist = (distH - airSprintTravel).coerceAtLeast(1.0)
        val reqV0 = (neededImpulseDist / decaySum).coerceIn(0.5, 2.2)

        val vApproach = 0.55
        val blastImpulse = 1.45
        val cosTheta = ((reqV0 - vApproach) / blastImpulse).coerceIn(0.20, 0.95)
        val calcPitch = Math.toDegrees(Math.acos(cosTheta)).toFloat()

        val maxPitchForRange = when {
            deltaY > 1.5 -> 65.0f
            distH >= 17.0 -> if (deltaY > 0.0) 38.0f else 42.0f
            distH >= 14.0 -> if (deltaY > 0.0) 44.0f else 48.0f
            distH >= 11.0 -> if (deltaY > 0.0) 52.0f else 56.0f
            distH >= 8.0 -> 64.0f
            else -> 75.0f
        }
        val minPitch = when {
            deltaY > 1.5 -> (48.0f + deltaY.toFloat() * 2.0f).coerceIn(52.0f, 62.0f)
            isRedirection -> 35.0f
            else -> 28.0f
        }
        val boundedPitch = calcPitch.coerceIn(minPitch, maxPitchForRange)

        // If recorded pitch is provided, preserve it
        if (recordedPitch in 20.0f..88.0f) {
            return recordedPitch
        }
        return boundedPitch
    }

    /**
     * Calculates the exact ground blast coordinate and look rotation needed to produce
     * the required knockback impulse to propel the player towards [destinationPos].
     */
    fun calculateBonzoAimPlan(
        playerPos: Vec3,
        playerVel: Vec3,
        playerEyeY: Double,
        destinationPos: Vec3,
        recordedPitch: Float = 0f,
        recordedYaw: Float = 0f,
        targetLaunchBps: Double = 24.0,
        groundY: Double? = null,
        hasLookNode: Boolean = false
    ): BonzoAimPlan {
        val destDx = destinationPos.x - playerPos.x
        val destDz = destinationPos.z - playerPos.z
        val destDistH = hypot(destDx, destDz)
        val destYaw = if (destDistH > 0.01) (-Math.toDegrees(atan2(destDx, destDz))).toFloat() else 0f

        val currentHeadingYaw = if (playerVel.horizontalDistance() > 0.04) {
            (-Math.toDegrees(atan2(playerVel.x, playerVel.z))).toFloat()
        } else {
            destYaw
        }

        val turnAngle = Mth.wrapDegrees(destYaw - currentHeadingYaw)

        // Desired horizontal launch velocity vector
        val targetLaunchSpeed = (targetLaunchBps / 20.0).coerceIn(0.9, 1.4)
        val destDir = if (destDistH > 0.01) Vec3(destDx / destDistH, 0.0, destDz / destDistH) else Vec3(0.0, 0.0, 1.0)
        val desiredVel = destDir.scale(targetLaunchSpeed)

        // Required impulse vector from blast: ΔV = V_desired - V_current
        val currentHorizVel = Vec3(playerVel.x, 0.0, playerVel.z)
        val requiredImpulse = desiredVel.subtract(currentHorizVel)
        val impulseLen = requiredImpulse.horizontalDistance()
        val impulseDir = if (impulseLen > 0.01) requiredImpulse.scale(1.0 / impulseLen) else destDir

        val isRedirection = abs(turnAngle) >= 15.0f || (impulseLen > 0.55 && abs(turnAngle) >= 13.0f)

        // Predicted player position at detonation (accounting for forward velocity without overshooting the blast center)
        val blastOffsetDist = if (isRedirection) 1.25 else 1.15
        val forwardDisp = playerVel.x * impulseDir.x + playerVel.z * impulseDir.z
        val maxForward = blastOffsetDist - 0.45
        val actualForward = (forwardDisp * BONZO_DETONATION_TICKS).coerceAtMost(maxForward)
        val perpVx = playerVel.x - forwardDisp * impulseDir.x
        val perpVz = playerVel.z - forwardDisp * impulseDir.z

        val blastImpactPos = Vec3(
            playerPos.x + perpVx * BONZO_DETONATION_TICKS + impulseDir.x * actualForward - impulseDir.x * blastOffsetDist,
            playerPos.y,
            playerPos.z + perpVz * BONZO_DETONATION_TICKS + impulseDir.z * actualForward - impulseDir.z * blastOffsetDist
        )
        val groundImpactY = groundY ?: floor(playerPos.y)

        // Calculate aim angles from current player eye position to the target blast impact point
        val aimDx = blastImpactPos.x - playerPos.x
        val aimDz = blastImpactPos.z - playerPos.z
        val aimDistH = hypot(aimDx, aimDz)
        val aimDy = (groundImpactY - 0.1) - playerEyeY
        val computedYaw = (-Math.toDegrees(atan2(aimDx, aimDz))).toFloat()
        val computedPitch = (-Math.toDegrees(atan2(aimDy, aimDistH.coerceAtLeast(0.2)))).toFloat().coerceIn(45.0f, 75.0f)

        val deltaY = destinationPos.y - playerPos.y
        val finalPitch = if (hasLookNode && recordedPitch in 20.0f..88.0f) {
            recordedPitch
        } else if (isRedirection) {
            if (recordedPitch in 50.0f..88.0f) recordedPitch else computedPitch
        } else if (deltaY > 1.5 && recordedPitch in 20.0f..40.0f) {
            calculateOptimalBonzoPitch(
                distH = destDistH,
                deltaY = deltaY,
                recordedPitch = 0f,
                isRedirection = false
            )
        } else if (recordedPitch in 20.0f..88.0f) {
            recordedPitch
        } else {
            calculateOptimalBonzoPitch(
                distH = destDistH,
                deltaY = deltaY,
                recordedPitch = recordedPitch,
                isRedirection = false
            )
        }

        val finalYaw = if (hasLookNode && recordedYaw != 0f) {
            recordedYaw
        } else if (isRedirection) {
            computedYaw
        } else {
            destYaw
        }

        return BonzoAimPlan(
            isRedirection = isRedirection,
            shotYaw = finalYaw,
            shotPitch = finalPitch,
            destYaw = destYaw,
            aimedImpactPos = Vec3(blastImpactPos.x, groundImpactY, blastImpactPos.z),
            expectedLaunchSpeedBps = targetLaunchSpeed * 20.0,
            jumpOnFire = !isRedirection
        )
    }

    /**
     * Determines whether the player must jump on this exact tick to clear a ledge/gap ahead.
     * Evaluates future trajectory with vs without a jump.
     */
    fun shouldPredictiveLedgeJump(
        level: Level,
        playerPos: Vec3,
        playerVel: Vec3,
        targetPos: Vec3,
        onGround: Boolean
    ): Boolean {
        if (!onGround) return false
        // Never jump when dropping down to a lower destination
        if (targetPos.y < playerPos.y - 0.5) return false
        val currentSpeedH = playerVel.horizontalDistance()
        if (currentSpeedH < 0.15) return false

        val distToTarget = hypot(targetPos.x - playerPos.x, targetPos.z - playerPos.z)
        if (distToTarget <= 0.6) return false
        val maxProbe = (distToTarget - 0.4).coerceAtLeast(0.1)

        val floorY = floor(playerPos.y - 0.1).toInt()
        val velDir = Vec3(playerVel.x, 0.0, playerVel.z).normalize()
        val toTargetDir = Vec3(targetPos.x - playerPos.x, 0.0, targetPos.z - playerPos.z).normalize()
        // Blend probe direction mostly towards target to prevent lateral drift on narrow catwalks from falsely probing off-edge
        val probeDir = velDir.scale(0.3).add(toTargetDir.scale(0.7)).normalize()
        var dropDetectedAtTick = -1

        for (step in 1..4) {
            val dist = (step * currentSpeedH).coerceAtMost(maxProbe)
            if (dist > maxProbe) break
            val probeX = playerPos.x + probeDir.x * dist
            val probeZ = playerPos.z + probeDir.z * dist
            val probePos = BlockPos(floor(probeX).toInt(), floorY, floor(probeZ).toInt())
            val stateAtFloor = level.getBlockState(probePos)
            val stateBelow = level.getBlockState(probePos.below())
            val shapeAtFloor = stateAtFloor.getCollisionShape(level, probePos)
            val shapeBelow = stateBelow.getCollisionShape(level, probePos.below())
            if (shapeAtFloor.isEmpty && (shapeBelow.isEmpty || stateAtFloor.isAir)) {
                dropDetectedAtTick = step
                break
            }
        }

        if (dropDetectedAtTick !in 1..2) return false

        // Check if jumping on this tick clears the gap
        val jumpVel = Vec3(
            playerVel.x + velDir.x * SPRINT_JUMP_BOOST,
            JUMP_VELOCITY,
            playerVel.z + velDir.z * SPRINT_JUMP_BOOST
        )
        val toTargetYaw = (-Math.toDegrees(atan2(velDir.x, velDir.z))).toFloat()
        val (projectedLand, _, _) = predictAirFlight(playerPos, jumpVel, playerPos.y, forwardInput = 1.0, yaw = toTargetYaw, maxTicks = 12)
        val landingBlockPos = BlockPos(floor(projectedLand.x).toInt(), floorY, floor(projectedLand.z).toInt())
        val landingState = level.getBlockState(landingBlockPos)
        return !landingState.getCollisionShape(level, landingBlockPos).isEmpty
    }

    data class AirControl(val forward: Double, val strafe: Double)

    /**
     * Closed-loop proportional guidance controller for airborne trajectory.
     * Computes necessary forward and strafe inputs to steer the flight arc
     * directly into [destinationPos].
     */
    fun computeAirGuidance(
        playerPos: Vec3,
        playerVel: Vec3,
        playerYaw: Float,
        destinationPos: Vec3
    ): AirControl {
        val (projLand, _, _) = predictAirFlight(playerPos, playerVel, destinationPos.y, forwardInput = 0.0, yaw = playerYaw)

        val errX = destinationPos.x - projLand.x
        val errZ = destinationPos.z - projLand.z

        val rad = Math.toRadians(playerYaw.toDouble())
        val cos = cos(rad)
        val sin = sin(rad)

        val localForward = -sin * errX + cos * errZ
        val localStrafe = cos * errX + sin * errZ

        val fwdInput = when {
            localForward > 0.20 -> 1.0
            localForward < -0.20 -> -1.0
            else -> 0.0
        }

        val strafeInput = when {
            localStrafe > 0.20 -> 1.0
            localStrafe < -0.20 -> -1.0
            else -> 0.0
        }

        return AirControl(fwdInput, strafeInput)
    }

    /**
     * Computes the lateral air strafe command (-1.0 to +1.0) to steer an airborne player
     * back toward the center line of a flight corridor (from [originX, originZ] to [destX, destZ]).
     *
     * In Minecraft movement input conventions:
     * - Positive value (> 0) means the player must strafe LEFT (press keyLeft / A).
     * - Negative value (< 0) means the player must strafe RIGHT (press keyRight / D).
     */
    fun computeCorridorAirStrafe(
        playerX: Double,
        playerZ: Double,
        playerYaw: Float,
        originX: Double,
        originZ: Double,
        destX: Double,
        destZ: Double
    ): Double {
        val segX = destX - originX
        val segZ = destZ - originZ
        val segLen = hypot(segX, segZ)
        if (segLen <= 0.01) return 0.0

        val px = playerX - originX
        val pz = playerZ - originZ

        // Normal vector pointing to the RIGHT of the route segment: R = (-segZ / segLen, segX / segLen)
        // crossTrack > 0 means player is displaced to the RIGHT of the route line.
        val crossTrack = (segX * pz - segZ * px) / segLen

        // Vector C points from player back towards corridor centerline:
        // C = -crossTrack * R = (crossTrack * segZ / segLen, -crossTrack * segX / segLen)
        val cx = crossTrack * (segZ / segLen)
        val cz = -crossTrack * (segX / segLen)

        // Project C onto player's local strafe axis (+1 is LEFT, -1 is RIGHT):
        val rad = Math.toRadians(playerYaw.toDouble())
        val cosYaw = cos(rad)
        val sinYaw = sin(rad)
        return cosYaw * cx + sinYaw * cz
    }

    /**
     * Checks whether an airborne sprint-jump from [playerPos] with current [playerVel]
     * is guaranteed to clear the gap and land safely on a solid platform near [targetPos].
     */
    fun canPredictiveChasmJump(
        level: Level,
        playerPos: Vec3,
        playerVel: Vec3,
        targetPos: Vec3,
        onGround: Boolean,
        minTakeoffSpeedBps: Double = 11.5
    ): Boolean {
        if (!onGround) return false
        val speedH = playerVel.horizontalDistance()
        if (speedH * 20.0 < minTakeoffSpeedBps) return false

        val targetFloorY = floor(targetPos.y - 0.1).toInt()
        val velDir = if (speedH > 0.01) Vec3(playerVel.x / speedH, 0.0, playerVel.z / speedH) else Vec3.ZERO
        val toTarget = Vec3(targetPos.x - playerPos.x, 0.0, targetPos.z - playerPos.z)
        val distToTarget = toTarget.horizontalDistance()
        if (distToTarget < 2.0 || distToTarget > 12.0) return false

        // Player heading must be oriented within ~45 degrees of destination
        val toTargetDir = toTarget.normalize()
        if (velDir.dot(toTargetDir) < 0.65) return false

        val jumpVel = Vec3(
            playerVel.x + velDir.x * SPRINT_JUMP_BOOST,
            JUMP_VELOCITY,
            playerVel.z + velDir.z * SPRINT_JUMP_BOOST
        )

        // Predict landing position at target elevation
        val toTargetYaw = (-Math.toDegrees(atan2(toTarget.x, toTarget.z))).toFloat()
        val (projectedLand, _, ticks) = predictAirFlight(playerPos, jumpVel, targetPos.y, forwardInput = 1.0, yaw = toTargetYaw, maxTicks = 16)
        if (ticks < 5) return false

        // Check if landing position lands on solid platform
        val landBlock = BlockPos(floor(projectedLand.x).toInt(), targetFloorY, floor(projectedLand.z).toInt())
        val isSolidFloor: (BlockPos) -> Boolean = { pos ->
            val s = level.getBlockState(pos)
            !s.isAir && !s.`is`(net.minecraft.world.level.block.Blocks.LAVA) && !s.`is`(net.minecraft.world.level.block.Blocks.WATER) && !s.getCollisionShape(level, pos).isEmpty
        }
        val isLandSolid = isSolidFloor(landBlock) || (level.getBlockState(landBlock).isAir && isSolidFloor(landBlock.below()))
        if (!isLandSolid) return false

        // Landing must land on or near the destination platform (within 3.2m of target)
        val distLandToTarget = hypot(projectedLand.x - targetPos.x, projectedLand.z - targetPos.z)
        return distLandToTarget <= 3.2
    }

    /**
     * Searches a local connected walkable ground corridor between [playerPos] and [targetPos].
     * If direct line of sight has a void/gap, this finds the next intermediate aim point
     * along the connected solid floor (e.g. following an L-shaped catwalk around a corner)
     * so the player never walks off an edge.
     */
    fun findLocalCorridorAim(
        level: Level,
        playerPos: Vec3,
        targetPos: Vec3,
        maxRadius: Int = 16
    ): Vec3? {
        fun findFloorY(x: Int, initialY: Int, z: Int): Int? {
            for (dy in 0 downTo -3) {
                val p = BlockPos(x, initialY + dy, z)
                val s = level.getBlockState(p)
                if (!s.isAir && !s.`is`(net.minecraft.world.level.block.Blocks.LAVA) && !s.`is`(net.minecraft.world.level.block.Blocks.WATER) && !s.getCollisionShape(level, p).isEmpty) {
                    val a1 = level.getBlockState(p.above())
                    val a2 = level.getBlockState(p.above(2))
                    if (a1.getCollisionShape(level, p.above()).isEmpty && a2.getCollisionShape(level, p.above(2)).isEmpty) {
                        return p.y
                    }
                }
            }
            return null
        }

        val startFloorY = findFloorY(floor(playerPos.x).toInt(), floor(playerPos.y).toInt(), floor(playerPos.z).toInt()) ?: floor(playerPos.y - 0.1).toInt()
        val targetFloorY = findFloorY(floor(targetPos.x).toInt(), floor(targetPos.y).toInt(), floor(targetPos.z).toInt()) ?: floor(targetPos.y - 0.1).toInt()
        val startPos = BlockPos(floor(playerPos.x).toInt(), startFloorY, floor(playerPos.z).toInt())
        val goalPos = BlockPos(floor(targetPos.x).toInt(), targetFloorY, floor(targetPos.z).toInt())

        if (startPos == goalPos) return null

        fun isWalkable(pos: BlockPos): Boolean {
            val floorState = level.getBlockState(pos)
            if (floorState.isAir || floorState.`is`(net.minecraft.world.level.block.Blocks.LAVA) || floorState.`is`(net.minecraft.world.level.block.Blocks.WATER)) return false
            if (floorState.getCollisionShape(level, pos).isEmpty) return false
            val above1 = level.getBlockState(pos.above())
            val above2 = level.getBlockState(pos.above(2))
            return above1.getCollisionShape(level, pos.above()).isEmpty && above2.getCollisionShape(level, pos.above(2)).isEmpty
        }

        // BFS to find connected walkable path
        val queue = ArrayDeque<BlockPos>()
        val cameFrom = HashMap<BlockPos, BlockPos>()
        queue.add(startPos)
        cameFrom[startPos] = startPos

        var closestToGoal = startPos
        var closestDistSq = startPos.distSqr(goalPos)

        val cardinalDirs = listOf(
            BlockPos(1, 0, 0), BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1), BlockPos(0, 0, -1)
        )

        var iterations = 0
        while (queue.isNotEmpty() && iterations < 90) {
            iterations++
            val current = queue.removeFirst()
            if (current == goalPos) {
                closestToGoal = current
                break
            }

            for (dir in cardinalDirs) {
                val candidates = listOf(
                    current.offset(dir.x, 0, dir.z),
                    current.offset(dir.x, 1, dir.z),
                    current.offset(dir.x, -1, dir.z)
                )
                for (next in candidates) {
                    if (abs(next.x - startPos.x) > maxRadius || abs(next.z - startPos.z) > maxRadius) continue
                    if (next !in cameFrom && isWalkable(next)) {
                        cameFrom[next] = current
                        queue.add(next)
                        val dSq = next.distSqr(goalPos)
                        if (dSq < closestDistSq) {
                            closestDistSq = dSq
                            closestToGoal = next
                        }
                    }
                }
            }
        }

        if (closestToGoal == startPos) return null

        // Reconstruct path from closestToGoal back to startPos
        val path = mutableListOf<BlockPos>()
        var curr = closestToGoal
        while (curr != startPos) {
            path.add(curr)
            curr = cameFrom[curr] ?: break
        }
        path.reverse()
        if (path.isEmpty()) return null

        // Find the furthest block in the path that has direct unbroken line-of-sight on the ground from player
        for (i in path.indices.reversed()) {
            val targetBlock = path[i]
            val bx = targetBlock.x + 0.5
            val bz = targetBlock.z + 0.5
            val segDx = bx - playerPos.x
            val segDz = bz - playerPos.z
            val segDist = hypot(segDx, segDz)
            val steps = (segDist / 0.6).toInt().coerceIn(1, 12)
            val hasGapOnSegment = (1..steps).any { s ->
                val frac = s.toDouble() / (steps + 1)
                val testPos = BlockPos(floor(playerPos.x + segDx * frac).toInt(), targetBlock.y, floor(playerPos.z + segDz * frac).toInt())
                !isWalkable(testPos) && !isWalkable(testPos.below()) && !isWalkable(testPos.above())
            }
            if (!hasGapOnSegment) {
                return Vec3(bx, targetBlock.y + 1.0, bz)
            }
        }

        val firstBlock = path.first()
        return Vec3(firstBlock.x + 0.5, firstBlock.y + 1.0, firstBlock.z + 0.5)
    }
}

