package me.nebula.orbit.e2e

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.packet.server.play.SetTitleTextPacket
import net.minestom.server.network.packet.server.play.SystemChatPacket
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BotSpawnFixtureTest {

    private lateinit var instance: InstanceContainer
    private val bots = mutableListOf<BotHandle>()

    @BeforeAll
    fun setUp() {
        NebulaTestFixture.bootMinestom()
        instance = NebulaTestFixture.createFlatInstance()
    }

    @AfterEach
    fun disconnectBots() {
        bots.forEach { NebulaTestFixture.disconnect(it) }
        bots.clear()
    }

    @AfterAll
    fun tearDown() {
        NebulaTestFixture.unregisterInstance(instance)
    }

    @Test
    fun `spawned bot is online after transitionConfigToPlay`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)
        assertTrue(bot.player.isOnline, "bot should be online after spawn returns")
    }

    @Test
    fun `bot receives sendMessage chat packet via interceptor`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)

        bot.player.sendMessage(Component.text("hello e2e"))

        val chats = bot.interceptor.packetsOf<SystemChatPacket>(bot.player)
        assertTrue(chats.isNotEmpty(), "expected at least one SystemChatPacket")
    }

    @Test
    fun `bot receives title via interceptor`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)

        bot.player.showTitle(Title.title(
            Component.text("test title"),
            Component.text("subtitle"),
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofMillis(100)),
        ))

        val titles = bot.interceptor.packetsOf<SetTitleTextPacket>(bot.player)
        assertTrue(titles.isNotEmpty(), "expected at least one SetTitleTextPacket")
    }

    @Test
    fun `each bot has an isolated packet buffer`() {
        val a = NebulaTestFixture.spawnBot(instance).also(bots::add)
        val b = NebulaTestFixture.spawnBot(instance).also(bots::add)

        a.player.sendMessage(Component.text("for a only"))

        val aChats = a.interceptor.packetsOf<SystemChatPacket>(a.player)
        val bChats = b.interceptor.packetsOf<SystemChatPacket>(b.player)
        assertTrue(aChats.isNotEmpty(), "bot A should have captured its chat")
        assertEquals(0, bChats.size, "bot B's buffer should not see bot A's chat")
    }
}
