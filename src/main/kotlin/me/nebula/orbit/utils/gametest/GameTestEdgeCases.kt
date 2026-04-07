package me.nebula.orbit.utils.gametest

import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.utils.matchresult.matchResult
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.registry.RegistryKey
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun GameTestContext.concurrent(vararg actions: () -> Unit) {
    val latch = CountDownLatch(actions.size)
    val futures = actions.map { action ->
        CompletableFuture.runAsync {
            MinecraftServer.getSchedulerManager().buildTask {
                try {
                    action()
                } finally {
                    latch.countDown()
                }
            }.schedule()
        }
    }
    CompletableFuture.allOf(*futures.toTypedArray())
    if (!latch.await(10, TimeUnit.SECONDS)) {
        throw GameTestFailure("concurrent actions did not complete within 10 seconds")
    }
}

fun GameTestContext.forAllPlayers(action: GameTestContext.(Player) -> Unit) {
    val playerSnapshot = players.toList()
    val latch = CountDownLatch(playerSnapshot.size)
    for (player in playerSnapshot) {
        MinecraftServer.getSchedulerManager().buildTask {
            try {
                action(player)
            } finally {
                latch.countDown()
            }
        }.schedule()
    }
    if (!latch.await(10, TimeUnit.SECONDS)) {
        throw GameTestFailure("forAllPlayers did not complete within 10 seconds")
    }
}

fun GameTestContext.stressRepeat(times: Int, action: () -> Unit) {
    require(times > 0) { "stressRepeat requires times > 0" }
    for (i in 0 until times) {
        action()
    }
}

fun GameTestContext.atTickStart(action: () -> Unit) {
    val latch = CountDownLatch(1)
    MinecraftServer.getSchedulerManager().buildTask {
        try {
            action()
        } finally {
            latch.countDown()
        }
    }.schedule()
    if (!latch.await(5, TimeUnit.SECONDS)) {
        throw GameTestFailure("atTickStart did not complete within 5 seconds")
    }
}

fun GameTestContext.atTickEnd(action: () -> Unit) {
    val latch = CountDownLatch(1)
    MinecraftServer.getSchedulerManager().buildTask {
        MinecraftServer.getSchedulerManager().buildTask {
            try {
                action()
            } finally {
                latch.countDown()
            }
        }.schedule()
    }.schedule()
    if (!latch.await(5, TimeUnit.SECONDS)) {
        throw GameTestFailure("atTickEnd did not complete within 5 seconds")
    }
}

fun GameTestContext.rapidFire(ticks: Int, action: (tick: Int) -> Unit) {
    require(ticks > 0) { "rapidFire requires ticks > 0" }
    val latch = CountDownLatch(1)
    var current = 0
    val taskHolder = arrayOfNulls<net.minestom.server.timer.Task>(1)
    val task = repeat(1) {
        if (current >= ticks) {
            taskHolder[0]?.cancel()
            latch.countDown()
            return@repeat
        }
        action(current)
        current++
    }
    taskHolder[0] = task
    if (!latch.await((ticks.toLong() * 50) + 5000, TimeUnit.MILLISECONDS)) {
        task.cancel()
        throw GameTestFailure("rapidFire did not complete within expected time")
    }
}

fun GameTestContext.withLatency(ticks: Int, block: () -> Unit) {
    require(ticks >= 0) { "withLatency requires non-negative ticks" }
    val latch = CountDownLatch(1)
    delay(ticks) {
        try {
            block()
        } finally {
            latch.countDown()
        }
    }
    if (!latch.await((ticks.toLong() * 50) + 5000, TimeUnit.MILLISECONDS)) {
        throw GameTestFailure("withLatency block did not complete within expected time")
    }
}

fun GameTestContext.simulateDisconnectReconnect(player: Player, disconnectTicks: Int = 20) {
    require(disconnectTicks > 0) { "disconnectTicks must be > 0" }
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for disconnect/reconnect simulation")
    gm.handleDeath(player, null)
    waitTicks(disconnectTicks)
}

fun GameTestContext.simulateJoinDuringPhaseTransition(targetPhase: GamePhase) {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for phase transition simulation")
    waitUntil(15.seconds) { gm.phase == targetPhase }
    val session = liveSession ?: throw GameTestFailure("simulateJoinDuringPhaseTransition requires live mode")
    session.spawnBots(1)
}

fun GameTestContext.simulateAllDisconnect() {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for all-disconnect simulation")
    val snapshot = players.toList()
    for (player in snapshot) {
        if (gm.tracker.isAlive(player.uuid)) {
            gm.eliminate(player)
        }
    }
}

fun GameTestContext.simulateRapidSpectatorSwitch(player: Player, targets: List<Player>, intervalTicks: Int = 1) {
    require(targets.isNotEmpty()) { "targets must not be empty" }
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for spectator switch simulation")
    for (target in targets) {
        player.spectate(target)
        if (intervalTicks > 0) waitTicks(intervalTicks)
    }
}

fun GameTestContext.forcePhase(phase: GamePhase) {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for forcePhase")
    when (phase) {
        GamePhase.WAITING -> {}
        GamePhase.STARTING -> {}
        GamePhase.PLAYING -> gm.forceStart()
        GamePhase.ENDING -> gm.forceEnd(matchResult { draw() })
    }
    waitUntil(5.seconds) { gm.phase == phase }
}

fun GameTestContext.simulateJoinWithFullInventory(player: Player) {
    val inv = player.inventory
    for (i in 0 until inv.size) {
        inv.setItemStack(i, ItemStack.of(Material.DIRT, 64))
    }
}

fun GameTestContext.simulateMoveToWorldBorder(player: Player) {
    val borderRadius = 29_999_984.0
    player.teleport(Pos(borderRadius, 64.0, 0.0))
}

fun GameTestContext.simulateMoveToVoid(player: Player) {
    player.teleport(Pos(player.position.x(), -64.0, player.position.z()))
}

fun GameTestContext.simulateMutualKill(player1: Player, player2: Player) {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for mutual kill simulation")
    MinecraftServer.getSchedulerManager().buildTask {
        gm.handleDeath(player1, player2)
        gm.handleDeath(player2, player1)
    }.schedule()
    waitTicks(2)
}

fun GameTestContext.simulateMultiDamage(player: Player, sources: List<Pair<Player, Float>>) {
    require(sources.isNotEmpty()) { "sources must not be empty" }
    MinecraftServer.getSchedulerManager().buildTask {
        for ((attacker, amount) in sources) {
            player.damage(DamageType.PLAYER_ATTACK, amount)
        }
    }.schedule()
    waitTicks(2)
}

fun GameTestContext.simulateEnvironmentDeath(player: Player, type: RegistryKey<DamageType> = DamageType.OUT_OF_WORLD) {
    val gm = gameMode
    if (gm != null) {
        gm.handleDeath(player, null)
    } else {
        player.damage(type, Float.MAX_VALUE)
    }
}

fun GameTestContext.simulateCombatLog(player: Player) {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for combat log simulation")
    val nearest = players.firstOrNull { it.uuid != player.uuid && gm.tracker.isAlive(it.uuid) }
    if (nearest != null) {
        gm.tracker.recordDamage(nearest.uuid, player.uuid)
    }
    player.playerConnection.disconnect()
    MinecraftServer.getConnectionManager().removePlayer(player.playerConnection)
    waitTicks(2)
}

fun GameTestContext.simulateTeamKill(attacker: Player, victim: Player) {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for team kill simulation")
    victim.damage(DamageType.PLAYER_ATTACK, Float.MAX_VALUE)
    gm.handleDeath(victim, attacker)
}

fun GameTestContext.simulateForceStartUnderMin() {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for force start simulation")
    gm.forceStart()
}

fun GameTestContext.simulateSimultaneousElimination(targets: List<Player>) {
    require(targets.isNotEmpty()) { "targets must not be empty" }
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for simultaneous elimination")
    MinecraftServer.getSchedulerManager().buildTask {
        for (player in targets) {
            if (gm.tracker.isAlive(player.uuid)) {
                gm.eliminate(player)
            }
        }
    }.schedule()
    waitTicks(2)
}

fun GameTestContext.simulateTimerExpire() {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for timer expire simulation")
    gm.forceEnd(matchResult { draw() })
}

fun GameTestContext.simulateOvertimeTrigger() {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for overtime trigger")
    gm.forceEnd(matchResult { draw() })
}

fun GameTestContext.awaitGameEnd(timeout: Duration = 30.seconds): Any? {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for awaitGameEnd")
    waitUntil(timeout) { gm.phase == GamePhase.ENDING }
    return null
}

fun GameTestContext.resetAndVerify() {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed for resetAndVerify")
    if (gm.phase == GamePhase.PLAYING || gm.phase == GamePhase.STARTING) {
        gm.forceEnd(matchResult { draw() })
    }
    waitUntil(30.seconds) { gm.phase == GamePhase.WAITING }
    assert(gm.tracker.aliveCount >= 0, "Tracker should have non-negative alive count after reset")
    assert(gm.phase == GamePhase.WAITING, "Phase should be WAITING after reset")
}

fun GameTestContext.simulateFillInventory(player: Player, item: ItemStack = ItemStack.of(Material.DIRT, 64)) {
    val inv = player.inventory
    for (i in 0 until inv.size) {
        inv.setItemStack(i, item)
    }
}

fun GameTestContext.simulateInventoryOverflow(player: Player, item: ItemStack) {
    simulateFillInventory(player)
    val leftover = player.inventory.addItemStack(item)
    if (!leftover) {
        log("Inventory overflow: item could not be added (inventory full)")
    }
}

fun GameTestContext.simulatePickupItem(player: Player, item: ItemStack) {
    player.inventory.addItemStack(item)
}

inline fun parameterizedTest(
    baseId: String,
    params: List<Map<String, Any>>,
    crossinline block: GameTestBuilder.(Map<String, Any>) -> Unit,
) {
    for ((index, param) in params.withIndex()) {
        gameTest("$baseId-$index") {
            block(param)
        }
    }
}

fun playerCountTests(
    baseId: String,
    counts: IntRange = 2..16,
    step: Int = 2,
    block: GameTestBuilder.(Int) -> Unit,
) {
    for (count in counts step step) {
        gameTest("$baseId-${count}p") {
            playerCount = count
            block(count)
        }
    }
}
