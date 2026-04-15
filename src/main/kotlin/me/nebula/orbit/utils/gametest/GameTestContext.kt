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
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.item.ItemStack
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.registry.RegistryKey
import kotlin.math.atan2
import kotlin.math.cos
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

    fun log(message: String) {
        operator.sendMessage(miniMessage.deserialize("<gray>[GameTest] $message"))
    }
}
