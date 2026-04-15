package me.nebula.orbit.utils.gametest

import net.minestom.server.entity.Player

internal fun GameTestContext.snapshot(): GameStateSnapshot {
    val playerSnapshots = buildMap {
        for (player in instance.players) {
            put(player.uuid, capturePlayerSnapshot(player))
        }
    }
    val trackerSnapshot = tracker?.let { t ->
        TrackerSnapshot(
            alive = t.alive,
            spectating = t.spectating,
            disconnected = t.disconnected,
            kills = t.alive.union(t.spectating).union(t.disconnected).associateWith { t.killsOf(it) },
            deaths = t.alive.union(t.spectating).union(t.disconnected).associateWith { t.deathsOf(it) },
            scores = t.alive.union(t.spectating).union(t.disconnected).associateWith { t.scoreOf(it) },
            teams = t.alive.union(t.spectating).union(t.disconnected).mapNotNull { uuid ->
                t.teamOf(uuid)?.let { uuid to it }
            }.toMap(),
        )
    }
    return GameStateSnapshot(
        phase = phase,
        tick = (System.currentTimeMillis() - startTimeMs) / 50L,
        players = playerSnapshots,
        trackerState = trackerSnapshot,
    )
}

internal fun GameTestContext.compareSnapshots(before: GameStateSnapshot, after: GameStateSnapshot): SnapshotDiff =
    diffSnapshots(before, after)

internal fun GameTestContext.restorePlayerState(player: Player, snapshot: PlayerSnapshot) {
    player.teleport(snapshot.position)
    player.health = snapshot.health
    player.food = snapshot.food
    player.foodSaturation = snapshot.saturation
    player.gameMode = snapshot.gameMode
    player.inventory.clear()
    for ((slot, stack) in snapshot.inventory) {
        player.inventory.setItemStack(slot, stack)
    }
    for ((slot, item) in snapshot.equipment) {
        player.setEquipment(slot, item)
    }
    player.clearEffects()
    for (potion in snapshot.effects) {
        player.addEffect(potion)
    }
}

internal fun GameTestContext.withSnapshot(block: () -> Unit): SnapshotDiff {
    val before = snapshot()
    block()
    val after = snapshot()
    return compareSnapshots(before, after)
}

internal fun GameTestContext.assertChanged(block: () -> Unit, assertion: SnapshotDiff.() -> Unit) {
    val diff = withSnapshot(block)
    diff.assertion()
}
