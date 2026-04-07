package me.nebula.orbit.utils.gametest

import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.utils.botai.BotAI
import me.nebula.orbit.utils.botai.BotBrain
import me.nebula.orbit.utils.botai.BotGoal
import me.nebula.orbit.utils.botai.BotPersonality
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.tpsmonitor.TPSMonitor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.registry.RegistryKey
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GameTestFailure(message: String) : AssertionError(message)

internal fun usedMemoryMb(): Long {
    val runtime = Runtime.getRuntime()
    return (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
}

data class TestMetrics(
    val startTps: Double,
    val endTps: Double,
    val avgTps: Double,
    val peakMemoryMb: Long,
    val memoryDeltaMb: Long,
    val durationMs: Long,
    val eventCount: Int,
)

class GameTestContext(
    val operator: Player,
    initialPlayers: List<Player>,
    val instance: Instance,
    val gameMode: GameMode?,
    val events: EventRecorder = EventRecorder(),
    val packets: PacketInterceptor = PacketInterceptor(),
    private val mutablePlayers: Boolean = false,
    val liveSession: LiveTestSession? = null,
    internal val startTps: Double = TPSMonitor.averageTPS,
    internal val startMemoryMb: Long = usedMemoryMb(),
    internal val startTimeMs: Long = System.currentTimeMillis(),
) {

    private val _players = initialPlayers.toMutableList()

    val players: List<Player> get() = if (mutablePlayers && liveSession != null) {
        liveSession.botPlayers
    } else {
        _players
    }

    val tracker: PlayerTracker? get() = gameMode?.tracker

    val phase: GamePhase? get() = gameMode?.phase

    val isLive: Boolean get() = liveSession != null

    fun spawnBots(count: Int): List<Player> {
        val session = liveSession ?: throw GameTestFailure("spawnBots is only available in live mode")
        return session.spawnBots(count)
    }

    fun removeBots() {
        val session = liveSession ?: throw GameTestFailure("removeBots is only available in live mode")
        session.removeBots()
    }

    fun waitTicks(ticks: Int) {
        require(ticks >= 0) { "waitTicks requires non-negative ticks" }
        if (ticks == 0) return
        Thread.sleep(ticks * 50L)
    }

    fun waitUntil(timeout: Duration = 10.seconds, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw GameTestFailure("waitUntil timed out after $timeout")
            }
            Thread.sleep(50L)
        }
    }

    fun waitForPhase(phase: GamePhase, timeout: Duration = 15.seconds) {
        val gm = gameMode ?: throw GameTestFailure("No GameMode installed, cannot wait for phase")
        waitUntil(timeout) { gm.phase == phase }
    }

    fun Player.simulateAttack(target: Player) {
        target.damage(DamageType.PLAYER_ATTACK, 1.0f)
    }

    fun Player.simulateMove(pos: Pos) {
        teleport(pos)
    }

    fun Player.simulateUseItem() {
        swingMainHand()
    }

    fun Player.simulateBreakBlock(pos: Point) {
        val inst = this.instance ?: return
        inst.setBlock(pos, Block.AIR)
    }

    fun Player.simulatePlaceBlock(pos: Point, block: Block) {
        val inst = this.instance ?: return
        inst.setBlock(pos, block)
    }

    fun Player.simulateDamage(amount: Float, type: RegistryKey<DamageType> = DamageType.GENERIC) {
        damage(type, amount)
    }

    fun Player.simulateChat(message: String) {
        val inst = this.instance ?: return
        val event = PlayerChatEvent(this, inst.players, message)
        MinecraftServer.getGlobalEventHandler().call(event)
    }

    fun Player.simulateKill() {
        val gm = this@GameTestContext.gameMode
        if (gm != null) {
            gm.handleDeath(this@simulateKill, null)
        } else {
            damage(DamageType.GENERIC, Float.MAX_VALUE)
        }
    }

    fun Player.simulateSneak(sneaking: Boolean = true) {
        isSneaking = sneaking
    }

    fun Player.simulateSprint(sprinting: Boolean = true) {
        isSprinting = sprinting
    }

    fun Player.simulateJump() {
        velocity = velocity.add(0.0, 8.0, 0.0)
    }

    fun Player.simulateLookAt(target: Point) {
        val pos = position
        val dx = target.x() - pos.x()
        val dy = target.y() - (pos.y() + eyeHeight)
        val dz = target.z() - pos.z()
        val horizontalDist = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horizontalDist)).toFloat()
        setView(yaw, pitch)
    }

    fun Player.simulateLookAt(entity: Entity) {
        simulateLookAt(entity.position.add(0.0, entity.eyeHeight, 0.0))
    }

    fun Player.simulateRespawn() {
        teleport(respawnPoint)
    }

    fun Player.simulateHeldSlot(slot: Int) {
        setHeldItemSlot(slot.toByte())
    }

    fun Player.simulateEquip(slot: EquipmentSlot, item: ItemStack) {
        setEquipment(slot, item)
    }

    fun Player.simulateGiveItem(item: ItemStack) {
        inventory.addItemStack(item)
    }

    fun Player.simulateDropItem() {
        val slot = heldSlot.toInt()
        val stack = inventory.getItemStack(slot)
        if (stack.isAir) return
        inventory.setItemStack(slot, ItemStack.AIR)
        val inst = this.instance ?: return
        val entity = ItemEntity(stack)
        entity.setInstance(inst, position.add(0.0, eyeHeight, 0.0))
    }

    fun Player.simulateSwapHands() {
        val main = itemInMainHand
        val off = itemInOffHand
        setItemInMainHand(off)
        setItemInOffHand(main)
    }

    fun Player.simulateClearInventory() {
        inventory.clear()
    }

    fun Player.simulateInteractBlock(pos: Point) {
        val inst = this.instance ?: return
        val block = inst.getBlock(pos)
        val event = PlayerBlockInteractEvent(
            this,
            PlayerHand.MAIN,
            inst,
            block,
            BlockVec(pos.blockX(), pos.blockY(), pos.blockZ()),
            pos,
            BlockFace.TOP,
        )
        MinecraftServer.getGlobalEventHandler().call(event)
    }

    fun Player.simulateOpenContainer(pos: Point) {
        val inst = this.instance ?: return
        val block = inst.getBlock(pos)
        val handler = block.handler() ?: return
        val blockVec = BlockVec(pos.blockX(), pos.blockY(), pos.blockZ())
        val interaction = BlockHandler.Interaction(
            block, inst, BlockFace.TOP, blockVec, pos, this, PlayerHand.MAIN,
        )
        handler.onInteract(interaction)
    }

    fun Player.simulateAddEffect(effect: PotionEffect, duration: Int, amplifier: Int = 0) {
        addEffect(Potion(effect, amplifier, duration))
    }

    fun Player.simulateRemoveEffect(effect: PotionEffect) {
        removeEffect(effect)
    }

    fun Player.simulateClearEffects() {
        clearEffects()
    }

    fun Player.simulateSetHealth(health: Float) {
        this.health = health
    }

    fun Player.simulateSetFood(food: Int, saturation: Float = 5.0f) {
        this.food = food
        this.foodSaturation = saturation
    }

    fun Player.simulateHeal(amount: Float) {
        val maxHp = getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        health = (health + amount).coerceAtMost(maxHp)
    }

    fun Player.simulateShootProjectile(type: EntityType, speed: Double = 1.5) {
        val inst = this.instance ?: return
        val yaw = Math.toRadians(position.yaw().toDouble())
        val pitch = Math.toRadians(position.pitch().toDouble())
        val dx = -sin(yaw) * cos(pitch)
        val dy = -sin(pitch)
        val dz = cos(yaw) * cos(pitch)
        val spawnPos = position.add(0.0, eyeHeight, 0.0)
        val projectile = Entity(type)
        projectile.setNoGravity(false)
        projectile.setInstance(inst, spawnPos)
        projectile.velocity = Vec(dx * speed * 25, dy * speed * 25, dz * speed * 25)
    }

    fun Player.simulateCommand(command: String) {
        MinecraftServer.getCommandManager().execute(this, command)
    }

    fun assert(condition: Boolean, message: String = "Assertion failed") {
        if (!condition) throw GameTestFailure(message)
    }

    fun assertEqual(expected: Any?, actual: Any?, message: String = "") {
        if (expected != actual) {
            val detail = if (message.isNotEmpty()) "$message: " else ""
            throw GameTestFailure("${detail}expected <$expected> but was <$actual>")
        }
    }

    fun assertPhase(expected: GamePhase) {
        val actual = phase ?: throw GameTestFailure("No GameMode installed, cannot assert phase")
        if (actual != expected) throw GameTestFailure("Expected phase $expected but was $actual")
    }

    fun assertAlive(player: Player) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        if (!t.isAlive(player.uuid)) {
            throw GameTestFailure("Expected ${player.username} to be alive but state is ${t.stateOf(player.uuid)}")
        }
    }

    fun assertDead(player: Player) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        if (t.isAlive(player.uuid)) {
            throw GameTestFailure("Expected ${player.username} to be dead/spectating but was alive")
        }
    }

    fun assertAliveCount(expected: Int) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.aliveCount
        if (actual != expected) {
            throw GameTestFailure("Expected $expected alive players but found $actual")
        }
    }

    fun assertScore(player: Player, expected: Double) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.scoreOf(player.uuid)
        if (actual != expected) {
            throw GameTestFailure("Expected score $expected for ${player.username} but was $actual")
        }
    }

    fun assertKills(player: Player, expected: Int) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.killsOf(player.uuid)
        if (actual != expected) {
            throw GameTestFailure("Expected $expected kills for ${player.username} but was $actual")
        }
    }

    fun assertTeam(player: Player, expected: String) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.teamOf(player.uuid)
        if (actual != expected) {
            throw GameTestFailure("Expected team '$expected' for ${player.username} but was '$actual'")
        }
    }

    fun assertWinner(player: Player) {
        val gm = gameMode ?: throw GameTestFailure("No GameMode installed")
        val placement = gm.placementOf(player.uuid)
        if (placement != 1) {
            throw GameTestFailure("Expected ${player.username} to be winner (placement 1) but was $placement")
        }
    }

    fun assertPosition(player: Player, expected: Pos, tolerance: Double = 0.5) {
        val actual = player.position
        val dist = actual.distance(expected)
        if (dist > tolerance) {
            throw GameTestFailure("Expected ${player.username} at $expected (tolerance $tolerance) but was at $actual (distance $dist)")
        }
    }

    fun assertNear(player: Player, target: Point, radius: Double) {
        val dist = player.position.distance(target)
        if (dist > radius) {
            throw GameTestFailure("Expected ${player.username} within $radius of $target but was $dist away")
        }
    }

    fun assertInInstance(player: Player, inst: Instance) {
        val actual = player.instance
        if (actual != inst) {
            throw GameTestFailure("Expected ${player.username} in instance ${inst.uuid} but was in ${actual?.uuid}")
        }
    }

    fun assertOnGround(player: Player) {
        if (!player.isOnGround) {
            throw GameTestFailure("Expected ${player.username} to be on ground")
        }
    }

    fun assertHasItem(player: Player, material: Material) {
        val inv = player.inventory
        val found = (0 until inv.size).any { inv.getItemStack(it).material() == material }
        if (!found) {
            throw GameTestFailure("Expected ${player.username} to have $material in inventory")
        }
    }

    fun assertHasItemCount(player: Player, material: Material, count: Int) {
        val inv = player.inventory
        val actual = (0 until inv.size).sumOf { slot ->
            val stack = inv.getItemStack(slot)
            if (stack.material() == material) stack.amount() else 0
        }
        if (actual != count) {
            throw GameTestFailure("Expected ${player.username} to have $count of $material but had $actual")
        }
    }

    fun assertItemInSlot(player: Player, slot: Int, material: Material) {
        val actual = player.inventory.getItemStack(slot).material()
        if (actual != material) {
            throw GameTestFailure("Expected $material in slot $slot for ${player.username} but was $actual")
        }
    }

    fun assertEmptyInventory(player: Player) {
        val inv = player.inventory
        val nonEmpty = (0 until inv.size).any { !inv.getItemStack(it).isAir }
        if (nonEmpty) {
            throw GameTestFailure("Expected empty inventory for ${player.username}")
        }
    }

    fun assertHeldItem(player: Player, material: Material) {
        val actual = player.inventory.getItemStack(player.heldSlot.toInt()).material()
        if (actual != material) {
            throw GameTestFailure("Expected ${player.username} to hold $material but was holding $actual")
        }
    }

    fun assertHealth(player: Player, expected: Float, tolerance: Float = 0.01f) {
        val actual = player.health
        if (abs(actual - expected) > tolerance) {
            throw GameTestFailure("Expected health $expected (tolerance $tolerance) for ${player.username} but was $actual")
        }
    }

    fun assertHealthAbove(player: Player, min: Float) {
        val actual = player.health
        if (actual <= min) {
            throw GameTestFailure("Expected health > $min for ${player.username} but was $actual")
        }
    }

    fun assertHealthBelow(player: Player, max: Float) {
        val actual = player.health
        if (actual >= max) {
            throw GameTestFailure("Expected health < $max for ${player.username} but was $actual")
        }
    }

    fun assertFood(player: Player, expected: Int) {
        val actual = player.food
        if (actual != expected) {
            throw GameTestFailure("Expected food $expected for ${player.username} but was $actual")
        }
    }

    fun assertHasEffect(player: Player, effect: PotionEffect) {
        val found = player.activeEffects.any { it.potion().effect() == effect }
        if (!found) {
            throw GameTestFailure("Expected ${player.username} to have effect $effect")
        }
    }

    fun assertNoEffect(player: Player, effect: PotionEffect) {
        val found = player.activeEffects.any { it.potion().effect() == effect }
        if (found) {
            throw GameTestFailure("Expected ${player.username} to not have effect $effect")
        }
    }

    fun assertBlock(pos: Point, expected: Block) {
        val actual = instance.getBlock(pos)
        if (!actual.compare(expected)) {
            throw GameTestFailure("Expected block $expected at $pos but was $actual")
        }
    }

    fun assertBlockMaterial(pos: Point, expected: Material) {
        val block = instance.getBlock(pos)
        val blockKey = block.name()
        val materialKey = expected.key().value()
        if (blockKey != materialKey) {
            throw GameTestFailure("Expected block material $materialKey at $pos but was $blockKey")
        }
    }

    fun assertAir(pos: Point) {
        val block = instance.getBlock(pos)
        if (!block.isAir) {
            throw GameTestFailure("Expected air at $pos but was ${block.name()}")
        }
    }

    fun assertEntityCount(type: EntityType, expected: Int) {
        val actual = instance.entities.count { it.entityType == type }
        if (actual != expected) {
            throw GameTestFailure("Expected $expected entities of type $type but found $actual")
        }
    }

    fun assertEntityNear(pos: Point, radius: Double, type: EntityType, minCount: Int = 1) {
        val count = instance.entities.count { it.entityType == type && it.position.distance(pos) <= radius }
        if (count < minCount) {
            throw GameTestFailure("Expected at least $minCount entities of type $type within $radius of $pos but found $count")
        }
    }

    fun assertDeaths(player: Player, expected: Int) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.deathsOf(player.uuid)
        if (actual != expected) {
            throw GameTestFailure("Expected $expected deaths for ${player.username} but was $actual")
        }
    }

    fun assertStreak(player: Player, expected: Int) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.streakOf(player.uuid)
        if (actual != expected) {
            throw GameTestFailure("Expected streak $expected for ${player.username} but was $actual")
        }
    }

    fun assertTeamAlive(team: String, expected: Int) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.aliveInTeam(team).size
        if (actual != expected) {
            throw GameTestFailure("Expected $expected alive in team '$team' but was $actual")
        }
    }

    fun assertTeamEliminated(team: String) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        if (!t.isTeamEliminated(team)) {
            val alive = t.aliveInTeam(team).size
            throw GameTestFailure("Expected team '$team' to be eliminated but has $alive alive members")
        }
    }

    fun assertPlacement(player: Player, expected: Int) {
        val gm = gameMode ?: throw GameTestFailure("No GameMode installed")
        val actual = gm.placementOf(player.uuid)
        if (actual != expected) {
            throw GameTestFailure("Expected placement $expected for ${player.username} but was $actual")
        }
    }

    fun assertAliveCountAtLeast(min: Int) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.aliveCount
        if (actual < min) {
            throw GameTestFailure("Expected at least $min alive players but found $actual")
        }
    }

    fun assertAliveCountAtMost(max: Int) {
        val t = tracker ?: throw GameTestFailure("No tracker available")
        val actual = t.aliveCount
        if (actual > max) {
            throw GameTestFailure("Expected at most $max alive players but found $actual")
        }
    }

    fun assertWithinTicks(ticks: Int, block: () -> Unit) {
        val start = System.currentTimeMillis()
        block()
        val elapsed = System.currentTimeMillis() - start
        val maxMillis = ticks * 50L
        if (elapsed > maxMillis) {
            throw GameTestFailure("Block took ${elapsed}ms but limit was ${maxMillis}ms ($ticks ticks)")
        }
    }

    fun assertEventually(timeout: Duration = 5.seconds, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw GameTestFailure("Condition was not met within $timeout")
            }
            Thread.sleep(50L)
        }
    }

    inline fun <reified E : Event> assertEventFired(message: String = "") {
        val capture = events.captureOf(E::class.java)
        if (capture == null || capture.count == 0) {
            val detail = if (message.isNotEmpty()) "$message: " else ""
            throw GameTestFailure("${detail}expected ${E::class.simpleName} to be fired but it was not")
        }
    }

    inline fun <reified E : Event> assertEventNotFired(message: String = "") {
        val capture = events.captureOf(E::class.java)
        if (capture != null && capture.count > 0) {
            val detail = if (message.isNotEmpty()) "$message: " else ""
            throw GameTestFailure("${detail}expected ${E::class.simpleName} not to be fired but it was fired ${capture.count} time(s)")
        }
    }

    inline fun <reified E : Event> assertEventCount(expected: Int, message: String = "") {
        val capture = events.captureOf(E::class.java)
        val actual = capture?.count ?: 0
        if (actual != expected) {
            val detail = if (message.isNotEmpty()) "$message: " else ""
            throw GameTestFailure("${detail}expected ${E::class.simpleName} to be fired $expected time(s) but was $actual")
        }
    }

    inline fun <reified E : Event> capturedEvents(): EventCapture<E> {
        val capture = events.captureOf(E::class.java)
            ?: throw GameTestFailure("No capture registered for ${E::class.simpleName}. Call events.record<${E::class.simpleName}>() first.")
        return capture
    }

    fun collectMetrics(): TestMetrics {
        val now = System.currentTimeMillis()
        val endTps = TPSMonitor.averageTPS
        val currentMemory = usedMemoryMb()
        return TestMetrics(
            startTps = startTps,
            endTps = endTps,
            avgTps = (startTps + endTps) / 2.0,
            peakMemoryMb = maxOf(startMemoryMb, currentMemory),
            memoryDeltaMb = currentMemory - startMemoryMb,
            durationMs = now - startTimeMs,
            eventCount = events.totalEventCount(),
        )
    }

    fun Player.setBehavior(behavior: TestBehavior, config: BehaviorConfig = BehaviorConfig()) {
        TestBotController.setBehavior(this, behavior, config)
    }

    fun setAllBehavior(behavior: TestBehavior, config: BehaviorConfig = BehaviorConfig()) {
        for (player in players) {
            TestBotController.setBehavior(player, behavior, config)
        }
    }

    fun Player.attachAI(preset: String = "survival"): BotBrain = when (preset.lowercase()) {
        "combat" -> BotAI.attachCombatAI(this)
        "pvp" -> BotAI.attachPvPAI(this)
        "gatherer" -> BotAI.attachGathererAI(this)
        "passive" -> BotAI.attachPassiveAI(this)
        "miner" -> BotAI.attachMinerAI(this)
        else -> BotAI.attachSurvivalAI(this)
    }

    fun Player.attachAI(personality: BotPersonality, vararg goals: BotGoal): BotBrain =
        BotAI.attach(this, *goals, personality = personality)

    fun Player.detachAI() {
        BotAI.detach(this)
    }

    fun Player.brain(): BotBrain? = BotAI.getBrain(this)

    fun Player.setPersonality(personality: BotPersonality) {
        BotAI.detach(this)
        BotAI.attach(this, personality = personality)
    }

    fun attachAllAI(preset: String = "survival") {
        for (player in players) {
            player.attachAI(preset)
        }
    }

    fun detachAllAI() {
        for (player in players) {
            BotAI.detach(player)
        }
    }

    fun fillBlocks(from: Point, to: Point, block: Block) {
        val minX = min(from.blockX(), to.blockX())
        val minY = min(from.blockY(), to.blockY())
        val minZ = min(from.blockZ(), to.blockZ())
        val maxX = max(from.blockX(), to.blockX())
        val maxY = max(from.blockY(), to.blockY())
        val maxZ = max(from.blockZ(), to.blockZ())
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    instance.setBlock(x, y, z, block)
                }
            }
        }
    }

    fun setBlock(pos: Point, block: Block) {
        instance.setBlock(pos, block)
    }

    fun platform(centerX: Int, centerZ: Int, radius: Int, y: Int, block: Block = Block.STONE) {
        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                instance.setBlock(x, y, z, block)
            }
        }
    }

    fun arena(
        center: Point,
        radiusX: Int,
        radiusZ: Int,
        height: Int,
        wallBlock: Block = Block.BARRIER,
        floorBlock: Block = Block.STONE,
    ) {
        val cx = center.blockX()
        val cy = center.blockY()
        val cz = center.blockZ()
        for (x in (cx - radiusX)..(cx + radiusX)) {
            for (z in (cz - radiusZ)..(cz + radiusZ)) {
                instance.setBlock(x, cy, z, floorBlock)
            }
        }
        for (dy in 1..height) {
            for (x in (cx - radiusX)..(cx + radiusX)) {
                instance.setBlock(x, cy + dy, cz - radiusZ, wallBlock)
                instance.setBlock(x, cy + dy, cz + radiusZ, wallBlock)
            }
            for (z in (cz - radiusZ)..(cz + radiusZ)) {
                instance.setBlock(cx - radiusX, cy + dy, z, wallBlock)
                instance.setBlock(cx + radiusX, cy + dy, z, wallBlock)
            }
        }
    }

    fun spawnItem(pos: Point, item: ItemStack): Entity {
        val entity = ItemEntity(item)
        entity.setInstance(instance, Pos(pos.x(), pos.y(), pos.z()))
        return entity
    }

    fun spawnEntity(type: EntityType, pos: Point): Entity {
        val entity = Entity(type)
        entity.setInstance(instance, Pos(pos.x(), pos.y(), pos.z()))
        return entity
    }

    fun placeChest(pos: Point, items: Map<Int, ItemStack>) {
        instance.setBlock(pos, Block.CHEST)
        val inventory = Inventory(InventoryType.CHEST_3_ROW, "Chest")
        for ((slot, stack) in items) {
            inventory.setItemStack(slot, stack)
        }
    }

    fun clearEntities() {
        for (entity in instance.entities.toList()) {
            if (entity is Player) continue
            entity.remove()
        }
    }

    fun blockAt(pos: Point): Block = instance.getBlock(pos)

    fun snapshot(): GameStateSnapshot {
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

    fun compareSnapshots(before: GameStateSnapshot, after: GameStateSnapshot): SnapshotDiff =
        diffSnapshots(before, after)

    fun restorePlayerState(player: Player, snapshot: PlayerSnapshot) {
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

    fun withSnapshot(block: () -> Unit): SnapshotDiff {
        val before = snapshot()
        block()
        val after = snapshot()
        return compareSnapshots(before, after)
    }

    fun assertChanged(block: () -> Unit, assertion: SnapshotDiff.() -> Unit) {
        val diff = withSnapshot(block)
        diff.assertion()
    }

    fun log(message: String) {
        operator.sendMessage(miniMessage.deserialize("<gray>[GameTest] $message"))
    }
}
