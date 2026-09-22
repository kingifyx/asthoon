package com.asthoonlite.dungeon

import com.asthoonlite.dungeon.solvers.CampHelper

/**
 * Blood/Watcher solver. Delegates to Devonian's CampHelper for exact
 * trajectory prediction, 3D box rendering, countdown timers, and sound alerts.
 */
object BloodRoomSolver {
    fun register() {
        CampHelper.register()
    }

    fun reset() {
        CampHelper.reset()
    }
}
