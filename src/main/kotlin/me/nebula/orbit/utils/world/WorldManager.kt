package me.nebula.orbit.utils.world

import me.nebula.orbit.translation.translateDefault
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import java.util.concurrent.ConcurrentHashMap

enum class GameRule(val defaultValue: Any) {
    DO_DAYLIGHT_CYCLE(true),
    DO_WEATHER_CYCLE(true),
    DO_MOB_SPAWNING(true),
    DO_MOB_LOOT(true),
    DO_TILE_DROPS(true),
    DO_FIRE_TICK(true),
    DO_ENTITY_DROPS(true),
    KEEP_INVENTORY(false),
    MOB_GRIEFING(true),
    PVP(true),
    FALL_DAMAGE(true),
    FIRE_DAMAGE(true),
    DROWNING_DAMAGE(true),
    FREEZE_DAMAGE(true),
    NATURAL_REGENERATION(true),
    DO_IMMEDIATE_RESPAWN(false),
    SHOW_DEATH_MESSAGES(true),
    ANNOUNCE_ADVANCEMENTS(true),
    DO_INSOMNIA(true),
    RANDOM_TICK_SPEED(3),
    SPAWN_RADIUS(10),
    MAX_ENTITY_CRAMMING(24),
    SPECTATORS_GENERATE_CHUNKS(true),
    DO_PATROL_SPAWNING(true),
    DO_TRADER_SPAWNING(true),
    DO_WARDEN_SPAWNING(true),
    FORGIVE_DEAD_PLAYERS(true),
    UNIVERSAL_ANGER(false),
}

class GameRuleStorage {

    private val rules = ConcurrentHashMap<GameRule, Any>()

    fun <T : Any> set(rule: GameRule, value: T) { rules[rule] = value }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(rule: GameRule): T = (rules[rule] ?: rule.defaultValue) as T

    fun getBoolean(rule: GameRule): Boolean = get(rule)
    fun getInt(rule: GameRule): Int = get(rule)

    fun setAll(vararg pairs: Pair<GameRule, Any>) { pairs.forEach { (r, v) -> rules[r] = v } }

    fun entries(): Map<GameRule, Any> = rules.toMap()
}

val Instance.gameRules: GameRuleStorage
    get() = gameRuleCache.getOrPut(uuid) { GameRuleStorage() }

private val gameRuleCache = ConcurrentHashMap<java.util.UUID, GameRuleStorage>()

object WorldManager {

    private val worlds = ConcurrentHashMap<String, InstanceContainer>()
    private val instanceManager: InstanceManager get() = MinecraftServer.getInstanceManager()

    fun create(name: String, block: WorldBuilder.() -> Unit = {}): InstanceContainer {
        require(!worlds.containsKey(name)) { "World '$name' already exists" }
        val builder = WorldBuilder().apply(block)
        val instance = instanceManager.createInstanceContainer()
        builder.apply(instance)
        worlds[name] = instance
        return instance
    }

    fun get(name: String): InstanceContainer? = worlds[name]

    fun require(name: String): InstanceContainer =
        requireNotNull(worlds[name]) { "World '$name' not found" }

    fun delete(name: String) {
        val instance = worlds.remove(name) ?: return
        gameRuleCache.remove(instance.uuid)
        instance.players.forEach { it.kick(translateDefault("orbit.util.world.deleted")) }
        instanceManager.unregisterInstance(instance)
    }

    fun all(): Map<String, InstanceContainer> = worlds.toMap()

    fun names(): Set<String> = worlds.keys.toSet()

    fun createVoid(name: String): InstanceContainer = create(name)

    fun createFlat(name: String, height: Int = 40, material: Block = Block.GRASS_BLOCK): InstanceContainer =
        create(name) { flat(height, material) }
}

class WorldBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var generator: ((GenerationUnit) -> Unit)? = null
    @PublishedApi internal var spawnPoint: Pos? = null
    @PublishedApi internal var time: Long? = null
    @PublishedApi internal var timeRate: Int? = null
    @PublishedApi internal var freezeTime: Boolean = false
    @PublishedApi internal var weather: Weather? = null
    @PublishedApi internal var weatherTransitionTicks: Int = 0
    @PublishedApi internal val gameRules = mutableMapOf<GameRule, Any>()
    @PublishedApi internal var borderDiameter: Double? = null
    @PublishedApi internal var borderCenter: Pos? = null

    fun generator(gen: (GenerationUnit) -> Unit) { generator = gen }

    fun spawn(pos: Pos) { spawnPoint = pos }
    fun spawn(x: Double, y: Double, z: Double) { spawnPoint = Pos(x, y, z) }
    fun spawn(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) { spawnPoint = Pos(x, y, z, yaw, pitch) }

    fun void() { generator = null }

    fun flat(height: Int = 40, material: Block = Block.GRASS_BLOCK) {
        generator = { unit -> unit.modifier().fillHeight(0, height, material) }
    }

    fun superFlat(layers: List<Pair<Int, Block>>) {
        generator = { unit ->
            var y = 0
            for ((height, block) in layers) {
                unit.modifier().fillHeight(y, y + height, block)
                y += height
            }
        }
    }

    fun time(ticks: Long) { time = ticks }
    fun noon() { time = 6000 }
    fun midnight() { time = 18000 }
    fun sunrise() { time = 0 }
    fun sunset() { time = 12000 }
    fun freezeTime() { freezeTime = true }
    fun timeRate(ticksPerSecond: Int) { timeRate = ticksPerSecond }

    fun weather(w: Weather) { weather = w }
    fun clear() { weather = Weather.CLEAR }
    fun rain() { weather = Weather.RAIN }
    fun thunder() { weather = Weather.THUNDER }
    fun transitionWeather(w: Weather, ticks: Int) { weather = w; weatherTransitionTicks = ticks }

    fun gameRule(rule: GameRule, value: Any) { gameRules[rule] = value }
    fun pvp(enabled: Boolean) { gameRules[GameRule.PVP] = enabled }
    fun keepInventory(enabled: Boolean) { gameRules[GameRule.KEEP_INVENTORY] = enabled }
    fun doDaylightCycle(enabled: Boolean) { gameRules[GameRule.DO_DAYLIGHT_CYCLE] = enabled }
    fun doWeatherCycle(enabled: Boolean) { gameRules[GameRule.DO_WEATHER_CYCLE] = enabled }
    fun doMobSpawning(enabled: Boolean) { gameRules[GameRule.DO_MOB_SPAWNING] = enabled }
    fun doMobLoot(enabled: Boolean) { gameRules[GameRule.DO_MOB_LOOT] = enabled }
    fun doTileDrops(enabled: Boolean) { gameRules[GameRule.DO_TILE_DROPS] = enabled }
    fun doFireTick(enabled: Boolean) { gameRules[GameRule.DO_FIRE_TICK] = enabled }
    fun fallDamage(enabled: Boolean) { gameRules[GameRule.FALL_DAMAGE] = enabled }
    fun fireDamage(enabled: Boolean) { gameRules[GameRule.FIRE_DAMAGE] = enabled }
    fun drowningDamage(enabled: Boolean) { gameRules[GameRule.DROWNING_DAMAGE] = enabled }
    fun freezeDamage(enabled: Boolean) { gameRules[GameRule.FREEZE_DAMAGE] = enabled }
    fun naturalRegeneration(enabled: Boolean) { gameRules[GameRule.NATURAL_REGENERATION] = enabled }
    fun immediateRespawn(enabled: Boolean) { gameRules[GameRule.DO_IMMEDIATE_RESPAWN] = enabled }
    fun mobGriefing(enabled: Boolean) { gameRules[GameRule.MOB_GRIEFING] = enabled }
    fun announceAdvancements(enabled: Boolean) { gameRules[GameRule.ANNOUNCE_ADVANCEMENTS] = enabled }

    fun border(diameter: Double, center: Pos = Pos.ZERO) {
        borderDiameter = diameter
        borderCenter = center
    }

    fun lobby() {
        pvp(false)
        keepInventory(true)
        doDaylightCycle(false)
        doWeatherCycle(false)
        doMobSpawning(false)
        doMobLoot(false)
        doTileDrops(false)
        doFireTick(false)
        fallDamage(false)
        fireDamage(false)
        drowningDamage(false)
        freezeDamage(false)
        naturalRegeneration(true)
        immediateRespawn(true)
        mobGriefing(false)
        noon()
        freezeTime()
        clear()
    }

    fun arena() {
        pvp(true)
        keepInventory(false)
        doDaylightCycle(false)
        doWeatherCycle(false)
        doMobSpawning(false)
        doMobLoot(true)
        doTileDrops(false)
        doFireTick(false)
        naturalRegeneration(false)
        immediateRespawn(true)
        mobGriefing(false)
        noon()
        freezeTime()
        clear()
    }

    fun survival() {
        pvp(true)
        keepInventory(false)
        doDaylightCycle(true)
        doWeatherCycle(true)
        doMobSpawning(true)
        doMobLoot(true)
        doTileDrops(true)
        doFireTick(true)
        naturalRegeneration(true)
        mobGriefing(true)
    }

    @PublishedApi internal fun apply(instance: InstanceContainer) {
        generator?.let { instance.setGenerator(it) }
        time?.let { instance.time = it }
        if (freezeTime) instance.timeRate = 0
        timeRate?.let { instance.timeRate = it }
        weather?.let { instance.setWeather(it, weatherTransitionTicks) }

        if (gameRules.isNotEmpty()) {
            val storage = instance.gameRules
            gameRules.forEach { (rule, value) -> storage.set(rule, value) }
        }

        borderDiameter?.let { diameter ->
            val wb = instance.worldBorder
            val center = borderCenter ?: Pos.ZERO
            instance.worldBorder = wb.withCenter(center.x(), center.z()).withDiameter(diameter)
        }
    }
}

fun world(name: String, block: WorldBuilder.() -> Unit): InstanceContainer =
    WorldManager.create(name, block)

fun Instance.configure(block: WorldBuilder.() -> Unit) {
    val builder = WorldBuilder().apply(block)
    builder.apply(this as InstanceContainer)
}

fun Instance.configureWorld(block: (WorldBuilder) -> Unit) {
    val builder = WorldBuilder().also(block)
    builder.apply(this as InstanceContainer)
}

fun Instance.setGameRule(rule: GameRule, value: Any) { gameRules.set(rule, value) }
fun Instance.getGameRule(rule: GameRule): Any = gameRules.get(rule)
fun Instance.getGameRuleBoolean(rule: GameRule): Boolean = gameRules.getBoolean(rule)
fun Instance.getGameRuleInt(rule: GameRule): Int = gameRules.getInt(rule)
