package me.nebula.orbit.utils.gametest

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.cache.PlayerCache
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.utils.botai.BotAI
import me.nebula.orbit.utils.botai.BotBrain
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.fakeplayer.BOT_TAG
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.tpsmonitor.TPSMonitor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import net.minestom.server.entity.GameMode as MinecraftGameMode

class LiveTestSession(
    val operator: Player,
    val gameMode: GameMode,
    val instance: InstanceContainer,
) {

    private val logger = logger("LiveTest")
    private val spawnedBots = ConcurrentHashMap.newKeySet<UUID>()
    private val botIndex = AtomicInteger(0)
    private val activeTasks = ConcurrentHashMap.newKeySet<Task>()
    private val startTime = System.currentTimeMillis()
    @Volatile private var configNode: EventNode<*>? = null
    val packetInterceptor = PacketInterceptor()

    val botUuids: Set<UUID> get() = spawnedBots.toSet()

    val botPlayers: List<Player>
        get() = spawnedBots.mapNotNull { uuid ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        }

    fun spawnBots(count: Int): List<Player> {
        val uuids = buildBotUuids(count)
        val node = installConfigInterceptor(uuids.toSet())
        configNode = node

        spawnTestBots(uuids)
        val players = waitForBots(uuids)
        spawnedBots.addAll(uuids)

        MinecraftServer.getGlobalEventHandler().removeChild(node)
        configNode = null

        operator.sendMessage(miniMessage.deserialize(
            "<green>Spawned $count bot(s) into live game <dark_gray>(${spawnedBots.size} total)"
        ))

        return players
    }

    fun removeBots() {
        val count = spawnedBots.size
        for (uuid in spawnedBots) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player != null) {
                player.playerConnection.disconnect()
                MinecraftServer.getConnectionManager().removePlayer(player.playerConnection)
            }
            packetInterceptor.remove(uuid)
            runCatching { PlayerCache.evict(uuid) } // noqa: dangling runCatching
        }
        spawnedBots.clear()
        operator.sendMessage(miniMessage.deserialize("<yellow>Removed $count bot(s) from live game"))
    }

    fun context(): GameTestContext = GameTestContext(
        operator = operator,
        initialPlayers = botPlayers,
        instance = instance,
        gameMode = gameMode,
        packets = packetInterceptor,
        mutablePlayers = true,
        liveSession = this,
    )

    fun runScript(definition: GameTestDefinition) {
        val startTime = System.currentTimeMillis()
        operator.sendMessage(miniMessage.deserialize("<yellow>RUNNING</yellow> <gray>${definition.id} (live)"))

        Thread.startVirtualThread {
            try {
                if (definition.playerCount > 0 && spawnedBots.size < definition.playerCount) {
                    val needed = definition.playerCount - spawnedBots.size
                    spawnBots(needed)
                }

                val ctx = context()

                val testThread = Thread.currentThread()
                val timeoutThread = Thread.startVirtualThread {
                    try {
                        Thread.sleep(definition.timeout.inWholeMilliseconds)
                        testThread.interrupt()
                    } catch (_: InterruptedException) {}
                }

                try {
                    definition.setup(ctx)
                } finally {
                    timeoutThread.interrupt()
                }

                val elapsed = System.currentTimeMillis() - startTime
                operator.sendMessage(miniMessage.deserialize(
                    "<green>PASS</green> <gray>${definition.id} <dark_gray>(${elapsed}ms)"
                ))
            } catch (e: GameTestFailure) {
                val elapsed = System.currentTimeMillis() - startTime
                operator.sendMessage(miniMessage.deserialize(
                    "<red>FAIL</red> <gray>${definition.id}: ${e.message} <dark_gray>(${elapsed}ms)"
                ))
                logger.warn { "Live test '${definition.id}' failed: ${e.message}" }
            } catch (_: InterruptedException) {
                val elapsed = System.currentTimeMillis() - startTime
                operator.sendMessage(miniMessage.deserialize(
                    "<red>TIMEOUT</red> <gray>${definition.id}: exceeded ${definition.timeout} <dark_gray>(${elapsed}ms)"
                ))
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                operator.sendMessage(miniMessage.deserialize(
                    "<red>ERROR</red> <gray>${definition.id}: ${e::class.simpleName}: ${e.message} <dark_gray>(${elapsed}ms)"
                ))
                logger.error(e) { "Live test '${definition.id}' threw an exception" }
            }
        }
    }

    fun spawnBots(count: Int, preset: TestBotPreset): List<Player> {
        val players = spawnBots(count)
        for (player in players) {
            preset.equipment.forEach { (slot, item) -> player.setEquipment(slot, item) }
            player.health = preset.health
            player.food = preset.food
            TestBotController.setBehavior(player, preset.behavior, preset.config)
        }
        return players
    }

    fun gradualSpawn(count: Int, intervalTicks: Int = 20, preset: TestBotPreset = TestBotPresets.NAKED_WANDERER): Task {
        var remaining = count
        val taskHolder = arrayOfNulls<Task>(1)
        val task = repeat(intervalTicks) {
            if (remaining <= 0) {
                taskHolder[0]?.cancel()
                return@repeat
            }
            spawnBots(1, preset)
            remaining--
            if (remaining <= 0) {
                operator.sendMessage(miniMessage.deserialize(
                    "<green>Gradual spawn complete <dark_gray>($count bots)"
                ))
                taskHolder[0]?.cancel()
            }
        }
        taskHolder[0] = task
        activeTasks.add(task)
        operator.sendMessage(miniMessage.deserialize(
            "<yellow>Gradual spawn started: $count bots every $intervalTicks ticks"
        ))
        return task
    }

    fun stressTest(botCount: Int, duration: Duration): Task {
        val bots = spawnBots(botCount, TestBotPresets.CHAOTIC)
        val durationMs = duration.inWholeMilliseconds
        val stressStart = System.currentTimeMillis()
        val taskHolder = arrayOfNulls<Task>(1)
        val task = repeat(20) {
            val elapsed = System.currentTimeMillis() - stressStart
            if (elapsed >= durationMs) {
                for (bot in bots) {
                    TestBotController.clearBehavior(bot)
                }
                operator.sendMessage(miniMessage.deserialize(
                    "<yellow>Stress test complete after $duration"
                ))
                taskHolder[0]?.cancel()
            }
        }
        taskHolder[0] = task
        activeTasks.add(task)
        operator.sendMessage(miniMessage.deserialize(
            "<red>Stress test started: $botCount CHAOS bots for $duration"
        ))
        return task
    }

    fun setBotBehavior(player: Player, behavior: TestBehavior, config: BehaviorConfig = BehaviorConfig()) {
        TestBotController.setBehavior(player, behavior, config)
    }

    fun setAllBotBehavior(behavior: TestBehavior, config: BehaviorConfig = BehaviorConfig()) {
        for (bot in botPlayers) {
            TestBotController.setBehavior(bot, behavior, config)
        }
    }

    fun attachAI(player: Player, preset: String = "survival"): BotBrain = when (preset.lowercase()) {
        "combat" -> BotAI.attachCombatAI(player)
        "pvp" -> BotAI.attachPvPAI(player)
        "gatherer" -> BotAI.attachGathererAI(player)
        "passive" -> BotAI.attachPassiveAI(player)
        "miner" -> BotAI.attachMinerAI(player)
        else -> BotAI.attachSurvivalAI(player)
    }

    fun attachAllAI(preset: String = "survival") {
        for (bot in botPlayers) {
            attachAI(bot, preset)
        }
    }

    fun detachAI(player: Player) {
        BotAI.detach(player)
    }

    fun detachAllAI() {
        for (bot in botPlayers) {
            BotAI.detach(bot)
        }
    }

    fun metrics(): LiveMetrics {
        val runtime = Runtime.getRuntime()
        return LiveMetrics(
            botCount = spawnedBots.size,
            totalPlayers = instance.players.size,
            tps = TPSMonitor.averageTPS,
            memoryMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576,
            uptimeMs = System.currentTimeMillis() - startTime,
        )
    }

    fun stop() {
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
        for (uuid in spawnedBots) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player != null) {
                BotAI.detach(player)
                TestBotController.clearBehavior(player)
            }
        }
        configNode?.let {
            runCatching { MinecraftServer.getGlobalEventHandler().removeChild(it) } // noqa: dangling runCatching
        }
        removeBots()
    }

    private fun buildBotUuids(count: Int): List<UUID> =
        (0 until count).map {
            val index = botIndex.getAndIncrement()
            UUID(0x00FE57B0_10000000L or index.toLong(), index.toLong())
        }

    private fun installConfigInterceptor(botUuids: Set<UUID>): EventNode<*> {
        val spawnPos = gameMode.activeSpawnPoint
        val node = EventNode.all("livetest-config-intercept")
        node.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            if (event.player.uuid !in botUuids) return@addListener
            event.spawningInstance = instance
            event.player.respawnPoint = spawnPos
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        return node
    }

    private fun spawnTestBots(uuids: List<UUID>) {
        val connectionManager = MinecraftServer.getConnectionManager()

        for ((i, uuid) in uuids.withIndex()) {
            val name = "LiveBot${botIndex.get() - uuids.size + i}"

            Thread.startVirtualThread {
                runCatching {
                    val connection = TestPlayerConnection(packetInterceptor, uuid)
                    val gameProfile = GameProfile(uuid, name, emptyList())
                    val player = connectionManager.createPlayer(connection, gameProfile)
                    connection.setServerState(ConnectionState.CONFIGURATION)
                    connection.setClientState(ConnectionState.CONFIGURATION)
                    connection.receiveKnownPacksResponse(listOf(SelectKnownPacksPacket.MINECRAFT_CORE))
                    connectionManager.doConfiguration(player, true)
                    connectionManager.transitionConfigToPlay(player)
                    player.setTag(BOT_TAG, true)
                    player.gameMode = MinecraftGameMode.ADVENTURE
                }.onFailure { logger.error(it) { "Failed to spawn live test bot" } }
            }
        }
    }

    private fun waitForBots(uuids: List<UUID>): List<Player> {
        val deadline = System.currentTimeMillis() + 10_000L
        val connectionManager = MinecraftServer.getConnectionManager()
        val result = mutableListOf<Player>()

        for (uuid in uuids) {
            while (true) {
                if (System.currentTimeMillis() >= deadline) {
                    throw GameTestFailure("Timed out waiting for live test bots to join (got ${result.size}/${uuids.size})")
                }
                val player = connectionManager.getOnlinePlayerByUuid(uuid)
                if (player != null && player.isOnline) {
                    result.add(player)
                    break
                }
                Thread.sleep(50L)
            }
        }

        return result
    }
}

data class LiveMetrics(
    val botCount: Int,
    val totalPlayers: Int,
    val tps: Double,
    val memoryMb: Long,
    val uptimeMs: Long,
)
