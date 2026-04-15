package me.nebula.orbit.utils.gametest

import me.nebula.orbit.mode.game.GamePhase
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.potion.PotionEffect
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun GameTestContext.assert(condition: Boolean, message: String = "Assertion failed") {
    if (!condition) throw GameTestFailure(message)
}

internal fun GameTestContext.assertEqual(expected: Any?, actual: Any?, message: String = "") {
    if (expected != actual) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected <$expected> but was <$actual>")
    }
}

internal fun GameTestContext.assertPhase(expected: GamePhase) {
    val actual = phase ?: throw GameTestFailure("No GameMode installed, cannot assert phase")
    if (actual != expected) throw GameTestFailure("Expected phase $expected but was $actual")
}

internal fun GameTestContext.assertAlive(player: Player) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    if (!t.isAlive(player.uuid)) {
        throw GameTestFailure("Expected ${player.username} to be alive but state is ${t.stateOf(player.uuid)}")
    }
}

internal fun GameTestContext.assertDead(player: Player) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    if (t.isAlive(player.uuid)) {
        throw GameTestFailure("Expected ${player.username} to be dead/spectating but was alive")
    }
}

internal fun GameTestContext.assertAliveCount(expected: Int) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.aliveCount
    if (actual != expected) {
        throw GameTestFailure("Expected $expected alive players but found $actual")
    }
}

internal fun GameTestContext.assertScore(player: Player, expected: Double) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.scoreOf(player.uuid)
    if (actual != expected) {
        throw GameTestFailure("Expected score $expected for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertKills(player: Player, expected: Int) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.killsOf(player.uuid)
    if (actual != expected) {
        throw GameTestFailure("Expected $expected kills for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertTeam(player: Player, expected: String) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.teamOf(player.uuid)
    if (actual != expected) {
        throw GameTestFailure("Expected team '$expected' for ${player.username} but was '$actual'")
    }
}

internal fun GameTestContext.assertWinner(player: Player) {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed")
    val placement = gm.placementOf(player.uuid)
    if (placement != 1) {
        throw GameTestFailure("Expected ${player.username} to be winner (placement 1) but was $placement")
    }
}

internal fun GameTestContext.assertPosition(player: Player, expected: Pos, tolerance: Double = 0.5) {
    val actual = player.position
    val dist = actual.distance(expected)
    if (dist > tolerance) {
        throw GameTestFailure("Expected ${player.username} at $expected (tolerance $tolerance) but was at $actual (distance $dist)")
    }
}

internal fun GameTestContext.assertNear(player: Player, target: Point, radius: Double) {
    val dist = player.position.distance(target)
    if (dist > radius) {
        throw GameTestFailure("Expected ${player.username} within $radius of $target but was $dist away")
    }
}

internal fun GameTestContext.assertInInstance(player: Player, inst: Instance) {
    val actual = player.instance
    if (actual != inst) {
        throw GameTestFailure("Expected ${player.username} in instance ${inst.uuid} but was in ${actual?.uuid}")
    }
}

internal fun GameTestContext.assertOnGround(player: Player) {
    if (!player.isOnGround) {
        throw GameTestFailure("Expected ${player.username} to be on ground")
    }
}

internal fun GameTestContext.assertHasItem(player: Player, material: Material) {
    val inv = player.inventory
    val found = (0 until inv.size).any { inv.getItemStack(it).material() == material }
    if (!found) {
        throw GameTestFailure("Expected ${player.username} to have $material in inventory")
    }
}

internal fun GameTestContext.assertHasItemCount(player: Player, material: Material, count: Int) {
    val inv = player.inventory
    val actual = (0 until inv.size).sumOf { slot ->
        val stack = inv.getItemStack(slot)
        if (stack.material() == material) stack.amount() else 0
    }
    if (actual != count) {
        throw GameTestFailure("Expected ${player.username} to have $count of $material but had $actual")
    }
}

internal fun GameTestContext.assertItemInSlot(player: Player, slot: Int, material: Material) {
    val actual = player.inventory.getItemStack(slot).material()
    if (actual != material) {
        throw GameTestFailure("Expected $material in slot $slot for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertEmptyInventory(player: Player) {
    val inv = player.inventory
    val nonEmpty = (0 until inv.size).any { !inv.getItemStack(it).isAir }
    if (nonEmpty) {
        throw GameTestFailure("Expected empty inventory for ${player.username}")
    }
}

internal fun GameTestContext.assertHeldItem(player: Player, material: Material) {
    val actual = player.inventory.getItemStack(player.heldSlot.toInt()).material()
    if (actual != material) {
        throw GameTestFailure("Expected ${player.username} to hold $material but was holding $actual")
    }
}

internal fun GameTestContext.assertHealth(player: Player, expected: Float, tolerance: Float = 0.01f) {
    val actual = player.health
    if (abs(actual - expected) > tolerance) {
        throw GameTestFailure("Expected health $expected (tolerance $tolerance) for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertHealthAbove(player: Player, min: Float) {
    val actual = player.health
    if (actual <= min) {
        throw GameTestFailure("Expected health > $min for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertHealthBelow(player: Player, max: Float) {
    val actual = player.health
    if (actual >= max) {
        throw GameTestFailure("Expected health < $max for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertFood(player: Player, expected: Int) {
    val actual = player.food
    if (actual != expected) {
        throw GameTestFailure("Expected food $expected for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertHasEffect(player: Player, effect: PotionEffect) {
    val found = player.activeEffects.any { it.potion().effect() == effect }
    if (!found) {
        throw GameTestFailure("Expected ${player.username} to have effect $effect")
    }
}

internal fun GameTestContext.assertNoEffect(player: Player, effect: PotionEffect) {
    val found = player.activeEffects.any { it.potion().effect() == effect }
    if (found) {
        throw GameTestFailure("Expected ${player.username} to not have effect $effect")
    }
}

internal fun GameTestContext.assertBlock(pos: Point, expected: Block) {
    val actual = instance.getBlock(pos)
    if (!actual.compare(expected)) {
        throw GameTestFailure("Expected block $expected at $pos but was $actual")
    }
}

internal fun GameTestContext.assertBlockMaterial(pos: Point, expected: Material) {
    val block = instance.getBlock(pos)
    val blockKey = block.name()
    val materialKey = expected.key().value()
    if (blockKey != materialKey) {
        throw GameTestFailure("Expected block material $materialKey at $pos but was $blockKey")
    }
}

internal fun GameTestContext.assertAir(pos: Point) {
    val block = instance.getBlock(pos)
    if (!block.isAir) {
        throw GameTestFailure("Expected air at $pos but was ${block.name()}")
    }
}

internal fun GameTestContext.assertEntityCount(type: EntityType, expected: Int) {
    val actual = instance.entities.count { it.entityType == type }
    if (actual != expected) {
        throw GameTestFailure("Expected $expected entities of type $type but found $actual")
    }
}

internal fun GameTestContext.assertEntityNear(pos: Point, radius: Double, type: EntityType, minCount: Int = 1) {
    val count = instance.entities.count { it.entityType == type && it.position.distance(pos) <= radius }
    if (count < minCount) {
        throw GameTestFailure("Expected at least $minCount entities of type $type within $radius of $pos but found $count")
    }
}

internal fun GameTestContext.assertDeaths(player: Player, expected: Int) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.deathsOf(player.uuid)
    if (actual != expected) {
        throw GameTestFailure("Expected $expected deaths for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertStreak(player: Player, expected: Int) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.streakOf(player.uuid)
    if (actual != expected) {
        throw GameTestFailure("Expected streak $expected for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertTeamAlive(team: String, expected: Int) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.aliveInTeam(team).size
    if (actual != expected) {
        throw GameTestFailure("Expected $expected alive in team '$team' but was $actual")
    }
}

internal fun GameTestContext.assertTeamEliminated(team: String) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    if (!t.isTeamEliminated(team)) {
        val alive = t.aliveInTeam(team).size
        throw GameTestFailure("Expected team '$team' to be eliminated but has $alive alive members")
    }
}

internal fun GameTestContext.assertPlacement(player: Player, expected: Int) {
    val gm = gameMode ?: throw GameTestFailure("No GameMode installed")
    val actual = gm.placementOf(player.uuid)
    if (actual != expected) {
        throw GameTestFailure("Expected placement $expected for ${player.username} but was $actual")
    }
}

internal fun GameTestContext.assertAliveCountAtLeast(min: Int) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.aliveCount
    if (actual < min) {
        throw GameTestFailure("Expected at least $min alive players but found $actual")
    }
}

internal fun GameTestContext.assertAliveCountAtMost(max: Int) {
    val t = tracker ?: throw GameTestFailure("No tracker available")
    val actual = t.aliveCount
    if (actual > max) {
        throw GameTestFailure("Expected at most $max alive players but found $actual")
    }
}

internal fun GameTestContext.assertWithinTicks(ticks: Int, block: () -> Unit) {
    val start = System.currentTimeMillis()
    block()
    val elapsed = System.currentTimeMillis() - start
    val maxMillis = ticks * 50L
    if (elapsed > maxMillis) {
        throw GameTestFailure("Block took ${elapsed}ms but limit was ${maxMillis}ms ($ticks ticks)")
    }
}

internal fun GameTestContext.assertEventually(timeout: Duration = 5.seconds, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
    while (!condition()) {
        if (System.currentTimeMillis() >= deadline) {
            throw GameTestFailure("Condition was not met within $timeout")
        }
        Thread.sleep(50L)
    }
}

internal inline fun <reified E : Event> GameTestContext.assertEventFired(message: String = "") {
    val capture = events.captureOf(E::class.java)
    if (capture == null || capture.count == 0) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected ${E::class.simpleName} to be fired but it was not")
    }
}

internal inline fun <reified E : Event> GameTestContext.assertEventNotFired(message: String = "") {
    val capture = events.captureOf(E::class.java)
    if (capture != null && capture.count > 0) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected ${E::class.simpleName} not to be fired but it was fired ${capture.count} time(s)")
    }
}

internal inline fun <reified E : Event> GameTestContext.assertEventCount(expected: Int, message: String = "") {
    val capture = events.captureOf(E::class.java)
    val actual = capture?.count ?: 0
    if (actual != expected) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected ${E::class.simpleName} to be fired $expected time(s) but was $actual")
    }
}

internal inline fun <reified E : Event> GameTestContext.capturedEvents(): EventCapture<E> {
    val capture = events.captureOf(E::class.java)
        ?: throw GameTestFailure("No capture registered for ${E::class.simpleName}. Call events.record<${E::class.simpleName}>() first.")
    return capture
}
