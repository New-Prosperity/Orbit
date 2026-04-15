package me.nebula.orbit.utils.gametest

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.cache.PlayerCache
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.fakeplayer.BOT_TAG
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode as MinecraftGameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket
import net.minestom.server.network.player.GameProfile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class GameTestResult(
    val testId: String,
    val passed: Boolean,
    val message: String,
    val durationMs: Long,
)

object GameTestRunner {

    private val logger = logger("GameTest")
    private val activeTests = ConcurrentHashMap<UUID, RunningTest>()
    private val liveSessions = ConcurrentHashMap<UUID, LiveTestSession>()
    private val botIndex = AtomicInteger(0)

    private class RunningTest(
        val thread: Thread,
        val instance: InstanceContainer,
        val botUuids: List<UUID>,
        val gameMode: GameMode?,
        val configNode: EventNode<*>?,
    )

    fun run(operator: Player, testId: String) {
        val definition = GameTestRegistry.get(testId)
        if (definition == null) {
            operator.sendMessage(miniMessage.deserialize("<red>Unknown test: <white>$testId"))
            return
        }

        if (activeTests.containsKey(operator.uuid)) {
            operator.sendMessage(miniMessage.deserialize("<red>You already have a running test. Use <white>/gametest cancel<red> first."))
            return
        }

        operator.sendMessage(miniMessage.deserialize("<yellow>RUNNING</yellow> <gray>${definition.id}</gray>"))

        Thread.startVirtualThread {
            executeTest(operator, definition)
        }
    }

    fun runAll(operator: Player) {
        if (activeTests.containsKey(operator.uuid)) {
            operator.sendMessage(miniMessage.deserialize("<red>You already have a running test. Use <white>/gametest cancel<red> first."))
            return
        }

        val tests = GameTestRegistry.all()
        if (tests.isEmpty()) {
            operator.sendMessage(miniMessage.deserialize("<red>No tests registered."))
            return
        }

        operator.sendMessage(miniMessage.deserialize("<yellow>Running ${tests.size} test(s)..."))

        Thread.startVirtualThread {
            val results = mutableListOf<GameTestResult>()
            for ((_, definition) in tests) {
                val result = executeTest(operator, definition)
                results.add(result)
            }

            val passed = results.count { it.passed }
            val failed = results.size - passed
            val totalMs = results.sumOf { it.durationMs }

            operator.sendMessage(miniMessage.deserialize(
                "<gray>-------------------------------------------"
            ))
            operator.sendMessage(miniMessage.deserialize(
                "<gray>Results: <green>$passed passed</green> <red>$failed failed</red> <gray>(${totalMs}ms)"
            ))
        }
    }

    fun runByTag(operator: Player, tag: String) {
        if (activeTests.containsKey(operator.uuid)) {
            operator.sendMessage(miniMessage.deserialize("<red>You already have a running test. Use <white>/gametest cancel<red> first."))
            return
        }

        val tests = GameTestRegistry.byTag(tag)
        if (tests.isEmpty()) {
            operator.sendMessage(miniMessage.deserialize("<red>No tests with tag <white>$tag<red>."))
            return
        }

        operator.sendMessage(miniMessage.deserialize("<yellow>Running ${tests.size} test(s) with tag <white>$tag<yellow>..."))

        Thread.startVirtualThread {
            val results = mutableListOf<GameTestResult>()
            for ((_, definition) in tests) {
                val result = executeTest(operator, definition)
                results.add(result)
            }

            val passed = results.count { it.passed }
            val failed = results.size - passed
            val totalMs = results.sumOf { it.durationMs }

            operator.sendMessage(miniMessage.deserialize(
                "<gray>-------------------------------------------"
            ))
            operator.sendMessage(miniMessage.deserialize(
                "<gray>Results [$tag]: <green>$passed passed</green> <red>$failed failed</red> <gray>(${totalMs}ms)"
            ))
        }
    }

    fun cancel(operator: Player) {
        val running = activeTests.remove(operator.uuid)
        if (running == null) {
            operator.sendMessage(miniMessage.deserialize("<red>No running test to cancel."))
            return
        }
        running.thread.interrupt()
        cleanup(running)
        operator.sendMessage(miniMessage.deserialize("<yellow>Test cancelled."))
    }

    fun startLive(operator: Player): LiveTestSession? {
        if (liveSessions.containsKey(operator.uuid)) {
            operator.sendMessage(miniMessage.deserialize("<red>You already have an active live session. Use <white>/orbit test live stop<red> first."))
            return null
        }

        val mode = Orbit.mode as? GameMode
        if (mode == null) {
            operator.sendMessage(miniMessage.deserialize("<red>No GameMode active on this server."))
            return null
        }

        val instance = mode.activeInstance
        val session = LiveTestSession(operator, mode, instance)
        liveSessions[operator.uuid] = session
        operator.sendMessage(miniMessage.deserialize("<green>Live test session started. <gray>Attached to ${mode::class.simpleName} on current instance."))
        return session
    }

    fun stopLive(operator: Player) {
        val session = liveSessions.remove(operator.uuid)
        if (session == null) {
            operator.sendMessage(miniMessage.deserialize("<red>No active live session."))
            return
        }
        session.stop()
        operator.sendMessage(miniMessage.deserialize("<yellow>Live test session stopped."))
    }

    fun getLiveSession(operator: Player): LiveTestSession? = liveSessions[operator.uuid]

    private fun executeTest(operator: Player, definition: GameTestDefinition): GameTestResult {
        val startTime = System.currentTimeMillis()
        var instance: InstanceContainer? = null
        var configNode: EventNode<*>? = null
        var gameMode: GameMode? = null
        var running: RunningTest? = null
        var context: GameTestContext? = null
        val eventRecorder = EventRecorder()
        val packetInterceptor = PacketInterceptor()

        try {
            instance = createTestInstance()
            gameMode = definition.gameModeFactory?.invoke()

            val targetInstance = gameMode?.activeInstance ?: instance
            val spawnPos = gameMode?.activeSpawnPoint ?: Pos(0.0, 64.0, 0.0)

            val botUuids = buildBotUuids(definition.playerCount)

            configNode = installConfigInterceptor(botUuids.toSet(), targetInstance, spawnPos)

            if (gameMode != null) {
                gameMode.install(MinecraftServer.getGlobalEventHandler())
            }

            eventRecorder.install()

            spawnTestBots(botUuids, packetInterceptor)
            val botPlayers = waitForBots(botUuids)

            if (gameMode != null) {
                waitForTracked(gameMode, botUuids, botPlayers)
            }

            running = RunningTest(Thread.currentThread(), instance, botUuids, gameMode, configNode)
            activeTests[operator.uuid] = running

            context = GameTestContext(
                operator = operator,
                initialPlayers = botPlayers,
                instance = targetInstance,
                gameMode = gameMode,
                events = eventRecorder,
                packets = packetInterceptor,
            )

            definition.beforeEach?.invoke(context)

            val testThread = Thread.currentThread()
            val timeoutThread = Thread.startVirtualThread {
                try {
                    Thread.sleep(definition.timeout.inWholeMilliseconds)
                    testThread.interrupt()
                } catch (_: InterruptedException) {}
            }

            try {
                definition.setup(context)
            } finally {
                timeoutThread.interrupt()
                runCatching { definition.afterEach?.invoke(context) } // noqa: dangling runCatching
            }

            val metrics = context.collectMetrics()
            val elapsed = System.currentTimeMillis() - startTime
            operator.sendMessage(miniMessage.deserialize(
                "<green>PASS</green> <gray>${definition.id} <dark_gray>(${elapsed}ms | TPS: ${"%.1f".format(metrics.avgTps)} | Mem: ${metrics.memoryDeltaMb}MB | Events: ${metrics.eventCount})"
            ))

            return GameTestResult(definition.id, true, "Passed", elapsed)
        } catch (e: GameTestFailure) {
            val elapsed = System.currentTimeMillis() - startTime
            operator.sendMessage(miniMessage.deserialize(
                "<red>FAIL</red> <gray>${definition.id}: ${e.message} <dark_gray>(${elapsed}ms)"
            ))
            context?.let { ctx ->
                for (line in formatFailureContext(ctx)) {
                    operator.sendMessage(miniMessage.deserialize("<dark_gray>  $line"))
                }
            }
            logger.warn { "Test '${definition.id}' failed: ${e.message}" }
            return GameTestResult(definition.id, false, e.message ?: "Unknown failure", elapsed)
        } catch (_: InterruptedException) {
            val elapsed = System.currentTimeMillis() - startTime
            operator.sendMessage(miniMessage.deserialize(
                "<red>TIMEOUT</red> <gray>${definition.id}: exceeded ${definition.timeout} <dark_gray>(${elapsed}ms)"
            ))
            return GameTestResult(definition.id, false, "Timeout", elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            operator.sendMessage(miniMessage.deserialize(
                "<red>ERROR</red> <gray>${definition.id}: ${e::class.simpleName}: ${e.message} <dark_gray>(${elapsed}ms)"
            ))
            context?.let { ctx ->
                for (line in formatFailureContext(ctx)) {
                    operator.sendMessage(miniMessage.deserialize("<dark_gray>  $line"))
                }
            }
            logger.error(e) { "Test '${definition.id}' threw an exception" }
            return GameTestResult(definition.id, false, "${e::class.simpleName}: ${e.message}", elapsed)
        } finally {
            eventRecorder.uninstall()
            activeTests.remove(operator.uuid)
            if (running != null) {
                cleanup(running)
            } else {
                configNode?.let { runCatching { MinecraftServer.getGlobalEventHandler().removeChild(it) } } // noqa: dangling runCatching
                runCatching { gameMode?.shutdown() } // noqa: dangling runCatching
                instance?.let { runCatching { MinecraftServer.getInstanceManager().unregisterInstance(it) } } // noqa: dangling runCatching
            }
        }
    }

    private fun formatFailureContext(context: GameTestContext): List<String> {
        val lines = mutableListOf<String>()
        val snap = runCatching { context.snapshot() }.getOrNull() ?: return lines // noqa: runCatching{}.getOrNull() as null check

        lines.add("Phase: ${snap.phase ?: "N/A"}")

        snap.trackerState?.let { t ->
            val aliveNames = snap.players.filterKeys { it in t.alive }.values.map { it.username }
            val deadNames = snap.players.filterKeys { it in t.spectating }.values.map { it.username }
            val total = t.alive.size + t.spectating.size + t.disconnected.size
            lines.add("Alive: ${t.alive.size}/$total ${aliveNames.take(10)}")
            if (t.spectating.isNotEmpty()) {
                lines.add("Dead: ${t.spectating.size}/$total ${deadNames.take(10)}")
            }
        }

        for ((uuid, ps) in snap.players) {
            lines.add("${ps.username}: pos=${ps.position.blockX()},${ps.position.blockY()},${ps.position.blockZ()} hp=${"%.1f".format(ps.health)}")
        }

        val eventSummary = context.events.let { recorder ->
            val total = recorder.totalEventCount()
            if (total > 0) "Recent events: $total total" else null
        }
        eventSummary?.let { lines.add(it) }

        return lines
    }

    private fun createTestInstance(): InstanceContainer {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { unit ->
            unit.modifier().fillHeight(0, 1, Block.BEDROCK)
            unit.modifier().fillHeight(1, 63, Block.STONE)
            unit.modifier().fillHeight(63, 64, Block.GRASS_BLOCK)
        }
        return instance
    }

    private fun buildBotUuids(count: Int): List<UUID> =
        (0 until count).map { _ ->
            val index = botIndex.getAndIncrement()
            UUID(0x00FE57B0_00000000L or index.toLong(), index.toLong())
        }

    private fun installConfigInterceptor(
        botUuids: Set<UUID>,
        targetInstance: InstanceContainer,
        spawnPos: Pos,
    ): EventNode<*> {
        val node = EventNode.all("gametest-config-intercept")
        node.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            if (event.player.uuid !in botUuids) return@addListener
            event.spawningInstance = targetInstance
            event.player.respawnPoint = spawnPos
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        return node
    }

    private fun spawnTestBots(uuids: List<UUID>, interceptor: PacketInterceptor) {
        val connectionManager = MinecraftServer.getConnectionManager()

        for ((i, uuid) in uuids.withIndex()) {
            val name = "TestBot${botIndex.get() - uuids.size + i}"

            Thread.startVirtualThread {
                runCatching {
                    val connection = TestPlayerConnection(interceptor, uuid)
                    val gameProfile = GameProfile(uuid, name, emptyList())
                    val player = connectionManager.createPlayer(connection, gameProfile)
                    connection.setServerState(ConnectionState.CONFIGURATION)
                    connection.setClientState(ConnectionState.CONFIGURATION)
                    connection.receiveKnownPacksResponse(listOf(SelectKnownPacksPacket.MINECRAFT_CORE))
                    connectionManager.doConfiguration(player, true)
                    connectionManager.transitionConfigToPlay(player)
                    player.setTag(BOT_TAG, true)
                    player.gameMode = MinecraftGameMode.ADVENTURE
                }.onFailure { logger.error(it) { "Failed to spawn test bot" } }
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
                    throw GameTestFailure("Timed out waiting for test bots to join (got ${result.size}/${uuids.size})")
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

    private fun waitForTracked(gameMode: GameMode, uuids: List<UUID>, players: List<Player>) {
        val deadline = System.currentTimeMillis() + 10_000L
        val tracker = gameMode.tracker

        for (uuid in uuids) {
            while (true) {
                if (System.currentTimeMillis() >= deadline) {
                    val tracked = uuids.count { tracker.contains(it) }
                    throw GameTestFailure("Timed out waiting for players to be tracked (got $tracked/${uuids.size})")
                }
                if (tracker.contains(uuid)) break
                Thread.sleep(50L)
            }
        }
    }

    private fun cleanup(running: RunningTest) {
        running.configNode?.let {
            runCatching { MinecraftServer.getGlobalEventHandler().removeChild(it) } // noqa: dangling runCatching
        }

        for (uuid in running.botUuids) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player != null) {
                player.playerConnection.disconnect()
                MinecraftServer.getConnectionManager().removePlayer(player.playerConnection)
            }
            runCatching { PlayerCache.evict(uuid) } // noqa: dangling runCatching
        }

        runCatching { running.gameMode?.shutdown() } // noqa: dangling runCatching
        runCatching { MinecraftServer.getInstanceManager().unregisterInstance(running.instance) } // noqa: dangling runCatching
    }
}
