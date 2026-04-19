package me.nebula.orbit.e2e

import com.hazelcast.core.HazelcastInstance
import me.nebula.ether.utils.hazelcast.HazelcastTestFixture
import me.nebula.ether.utils.hazelcast.Store
import me.nebula.ether.utils.translation.TranslationRegistry
import me.nebula.ether.utils.translation.translationRegistry
import me.nebula.orbit.Orbit
import me.nebula.orbit.notification.OrbitNotifications
import me.nebula.orbit.utils.gametest.PacketInterceptor
import me.nebula.orbit.utils.gametest.TestPlayerConnection
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket
import net.minestom.server.network.player.GameProfile
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Boots an embedded single-node Hazelcast member in the test JVM, wires
 * [Ether.hazelcast], and optionally populates [Orbit.translations] with
 * provided key/value entries. Designed for integration tests that need
 * real Hazelcast semantics (replicated-map-backed translations, entry
 * processors, distributed locks) that the in-memory offline backend
 * does not emulate.
 *
 * Typical lifecycle:
 * ```
 * @BeforeAll fun setUp() { NebulaTestFixture.boot() ; NebulaTestFixture.installTranslations(mapOf(...)) }
 * @AfterAll fun tearDown() { NebulaTestFixture.shutdown() }
 * ```
 *
 * Stores are registered via [NebulaTestFixture.registerStore] which
 * delegates to [Store.installHazelcastBackendForTest].
 */
object NebulaTestFixture {

    @Volatile private var minestomBooted = false
    private val botIndex = AtomicInteger(0)

    /** Delegates to [HazelcastTestFixture.boot] — see that for semantics. */
    fun boot(): HazelcastInstance = HazelcastTestFixture.boot()

    /** Delegates to [HazelcastTestFixture.isAvailable]. */
    fun isAvailable(): Boolean = HazelcastTestFixture.isAvailable()

    /** Delegates to [HazelcastTestFixture.shutdown]. */
    fun shutdown() = HazelcastTestFixture.shutdown()

    /** Delegates to [HazelcastTestFixture.registerStore]. */
    fun registerStore(store: Store<*, *>) = HazelcastTestFixture.registerStore(store)

    /**
     * Builds a [TranslationRegistry] backed by the fixture's Hazelcast instance, assigns it to
     * [Orbit.translations], and populates it with [entries] under the default locale.
     * [locale] defaults to `"en"`. Must be called after [boot].
     */
    fun installTranslations(entries: Map<String, String>, locale: String = "en") {
        HazelcastTestFixture.boot() // ensure available
        val registry = translationRegistry { defaultLocale(locale) }
        Orbit.translations = registry
        if (entries.isNotEmpty()) registry.putAll(locale, entries)
    }

    /**
     * Boots Minestom's server process in test mode and binds an ephemeral local port so the tick
     * scheduler runs. Sets [ServerFlag.INSIDE_TEST] so bot spawn can happen from any thread and
     * `transitionConfigToPlay` joins on the spawn future synchronously. Idempotent.
     */
    fun bootMinestom() {
        if (minestomBooted) return
        synchronized(this) {
            if (minestomBooted) return
            ServerFlag.INSIDE_TEST = true
            if (MinecraftServer.process() == null) {
                val server = MinecraftServer.init()
                server.start("127.0.0.1", 0)
            }
            OrbitNotifications.install()
            minestomBooted = true
        }
    }

    /**
     * Creates a flat test instance with a bedrock → stone → grass generator. Call after
     * [bootMinestom].
     */
    fun createFlatInstance(): InstanceContainer {
        checkNotNull(MinecraftServer.process()) { "Call NebulaTestFixture.bootMinestom() first" }
        return MinecraftServer.getInstanceManager().createInstanceContainer().apply {
            setGenerator { unit ->
                unit.modifier().fillHeight(0, 1, Block.BEDROCK)
                unit.modifier().fillHeight(1, 63, Block.STONE)
                unit.modifier().fillHeight(63, 64, Block.GRASS_BLOCK)
            }
        }
    }

    fun unregisterInstance(instance: InstanceContainer) {
        runCatching { MinecraftServer.getInstanceManager().unregisterInstance(instance) }
    }

    /**
     * Spawns a fully online test bot in [instance] with a captured packet stream. Requires
     * [bootMinestom]. The spawn protocol runs on a virtual thread; `transitionConfigToPlay`
     * joins the spawn future internally (via [ServerFlag.INSIDE_TEST]) so the returned
     * [BotHandle] is guaranteed online.
     */
    fun spawnBot(
        instance: InstanceContainer,
        spawn: Pos = Pos(0.0, 64.0, 0.0),
        interceptor: PacketInterceptor = PacketInterceptor(),
    ): BotHandle {
        bootMinestom()
        val index = botIndex.getAndIncrement()
        val uuid = UUID(0x00FE57B0_00000000L or index.toLong(), index.toLong())
        val name = "E2EBot$index"

        val configNode = EventNode.all("e2e-spawn-$index")
        configNode.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            if (event.player.uuid != uuid) return@addListener
            event.spawningInstance = instance
            event.player.respawnPoint = spawn
            // Skip the known-packs registry-data step. doConfiguration would otherwise block
            // for KNOWN_PACKS_RESPONSE_TIMEOUT (~30s) waiting on a client response that never
            // comes from a TestPlayerConnection.
            event.setSendRegistryData(false)
        }
        MinecraftServer.getGlobalEventHandler().addChild(configNode)

        val connection = TestPlayerConnection(interceptor, uuid)
        val profile = GameProfile(uuid, name, emptyList())

        val error = AtomicReference<Throwable>()
        val spawnThread = Thread.startVirtualThread {
            runCatching {
                val player = MinecraftServer.getConnectionManager().createPlayer(connection, profile)
                connection.setServerState(ConnectionState.CONFIGURATION)
                connection.setClientState(ConnectionState.CONFIGURATION)
                connection.receiveKnownPacksResponse(listOf(SelectKnownPacksPacket.MINECRAFT_CORE))
                MinecraftServer.getConnectionManager().doConfiguration(player, true)
                MinecraftServer.getConnectionManager().transitionConfigToPlay(player)
                player.gameMode = GameMode.ADVENTURE
            }.onFailure { error.set(it) }
        }
        spawnThread.join(10_000L)
        error.get()?.let { throw IllegalStateException("Bot $uuid spawn failed", it) }

        // transitionConfigToPlay only enqueues the player; ConnectionManager.tick drains the
        // playWaitingPlayers queue on the next server tick (~50ms cadence). Poll until the
        // player is registered in the play set.
        val deadline = System.currentTimeMillis() + 5_000L
        var player: Player? = null
        while (System.currentTimeMillis() < deadline) {
            player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player != null && player.isOnline) break
            Thread.sleep(25L)
        }
        if (player == null || !player.isOnline) {
            error("Bot $uuid did not transition to play state within 5s after transitionConfigToPlay")
        }

        return BotHandle(player, interceptor, configNode)
    }

    /** Disconnects a bot and removes its spawn event listener. Safe to call multiple times. */
    fun disconnect(handle: BotHandle) {
        val conn = handle.player.playerConnection
        runCatching { conn.disconnect() }
        runCatching { MinecraftServer.getConnectionManager().removePlayer(conn) }
        runCatching { MinecraftServer.getGlobalEventHandler().removeChild(handle.configNode) }
    }
}

data class BotHandle(
    val player: Player,
    val interceptor: PacketInterceptor,
    val configNode: EventNode<*>,
)
