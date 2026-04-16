package me.nebula.orbit.e2e

import io.mockk.mockk
import me.nebula.gravity.property.PropertyStore
import me.nebula.gravity.queue.PoolConfig
import me.nebula.gravity.queue.PoolConfigStore
import me.nebula.gravity.queue.QueueAssignmentStore
import me.nebula.gravity.queue.QueueProcessor
import me.nebula.gravity.queue.QueueStore
import me.nebula.gravity.rank.PlayerRankStore
import me.nebula.gravity.rank.RankStore
import me.nebula.gravity.rating.RatingStore
import me.nebula.gravity.server.LiveServer
import me.nebula.gravity.server.LiveServerRegistry
import me.nebula.gravity.server.ProtonClient
import me.nebula.gravity.server.ProvisionStore
import me.nebula.gravity.server.ServerType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueAssignmentE2ETest {

    private lateinit var proton: ProtonClient

    @BeforeAll
    fun setUp() {
        Assumptions.assumeTrue(
            NebulaTestFixture.isAvailable(),
            "HAZELCAST_LICENSE env var is required to boot the embedded Hazelcast Enterprise member; skipping.",
        )
        NebulaTestFixture.boot()
        NebulaTestFixture.registerStore(PoolConfigStore)
        NebulaTestFixture.registerStore(ProvisionStore)
        NebulaTestFixture.registerStore(QueueStore)
        NebulaTestFixture.registerStore(QueueAssignmentStore)
        NebulaTestFixture.registerStore(RatingStore)
        NebulaTestFixture.registerStore(RankStore)
        NebulaTestFixture.registerStore(PlayerRankStore)
        PropertyStore.initialize()
        proton = mockk(relaxed = true)
    }

    @AfterEach
    fun reset() {
        QueueStore.clearForTest()
        QueueAssignmentStore.clearForTest()
        PoolConfigStore.clearForTest()
        ProvisionStore.clearForTest()
        LiveServerRegistry.all().forEach { LiveServerRegistry.deregister(it.name) }
    }

    @AfterAll
    fun tearDown() {
        NebulaTestFixture.shutdown()
    }

    private fun seedPool(gameMode: String = "br") {
        PoolConfigStore.save(gameMode, PoolConfig(
            gameMode = gameMode,
            templateSlug = "battleroyale-template",
            targetServerCount = 1,
            maxPlayersPerServer = 16,
            maxPartySize = 4,
            minPlayersToProvision = 2,
        ))
    }

    private fun seedServer(
        gameMode: String = "br",
        name: String = "br-1",
        maxPlayers: Int = 16,
    ): LiveServer {
        val server = LiveServer(
            name = name,
            address = "127.0.0.1",
            port = 25565,
            serverType = ServerType.GAME,
            gameMode = gameMode,
            maxPlayers = maxPlayers,
        )
        LiveServerRegistry.register(server)
        return server
    }

    @Test
    fun `single queued player gets assigned to an active server`() {
        seedPool()
        val server = seedServer()
        val player = UUID.randomUUID()

        QueueStore.enqueue("br", player, listOf(player))
        QueueProcessor.process(proton)

        val assignments = QueueAssignmentStore.all()
        assertEquals(1, assignments.size, "expected exactly one assignment")
        val assignment = assignments.single()
        assertEquals("br", assignment.gameMode)
        assertEquals(server.name, assignment.serverProvisionUuid)
        assertEquals(player, assignment.partyLeader)
        assertEquals(listOf(player), assignment.members)
    }

    @Test
    fun `assigned player is dequeued from the gameMode queue`() {
        seedPool()
        seedServer()
        val player = UUID.randomUUID()
        QueueStore.enqueue("br", player, listOf(player))

        QueueProcessor.process(proton)

        val queue = QueueStore.load("br")
        assertTrue(
            queue == null || queue.entries.none { player in it.members },
            "player should be removed from the queue after assignment",
        )
    }

    @Test
    fun `no active server means no assignment is created`() {
        seedPool()
        val player = UUID.randomUUID()
        QueueStore.enqueue("br", player, listOf(player))

        QueueProcessor.process(proton)

        assertEquals(0, QueueAssignmentStore.all().size, "no server, no assignment")
        val queue = assertNotNull(QueueStore.load("br"))
        assertTrue(queue.entries.any { player in it.members }, "player should remain queued")
    }

    @Test
    fun `multiple players assigned to the same server when capacity allows`() {
        seedPool()
        seedServer(maxPlayers = 16)

        val playerA = UUID.randomUUID()
        val playerB = UUID.randomUUID()
        QueueStore.enqueue("br", playerA, listOf(playerA))
        QueueStore.enqueue("br", playerB, listOf(playerB))

        QueueProcessor.process(proton)

        val assignments = QueueAssignmentStore.all()
        assertEquals(2, assignments.size)
        assertTrue(assignments.all { it.gameMode == "br" })
    }

    @Test
    fun `party-of-three assigned together to one server`() {
        seedPool()
        seedServer(maxPlayers = 16)

        val leader = UUID.randomUUID()
        val mate1 = UUID.randomUUID()
        val mate2 = UUID.randomUUID()
        QueueStore.enqueue("br", leader, listOf(leader, mate1, mate2))

        QueueProcessor.process(proton)

        val assignment = QueueAssignmentStore.all().single()
        assertEquals(leader, assignment.partyLeader)
        assertEquals(setOf(leader, mate1, mate2), assignment.members.toSet())
    }
}
