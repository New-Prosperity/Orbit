package me.nebula.orbit.e2e

import io.mockk.mockk
import me.nebula.gravity.rating.RatingTier
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.RatingManager
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.packet.server.play.SetTitleSubTitlePacket
import net.minestom.server.network.packet.server.play.SetTitleTextPacket
import net.minestom.server.network.packet.server.play.SoundEffectPacket
import net.minestom.server.network.packet.server.play.SystemChatPacket
import net.minestom.server.sound.SoundEvent
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RatingManagerE2ETest {

    private lateinit var instance: InstanceContainer
    private lateinit var ratingManager: RatingManager
    private val bots = mutableListOf<BotHandle>()

    @BeforeAll
    fun setUp() {
        Assumptions.assumeTrue(
            NebulaTestFixture.isAvailable(),
            "HAZELCAST_LICENSE env var is required to boot the embedded Hazelcast Enterprise member; skipping.",
        )
        NebulaTestFixture.boot()
        NebulaTestFixture.installTranslations(mapOf(
            "orbit.rating.placement_complete" to "<gold><bold>PLACEMENTS COMPLETE! <reset><gray>Your rank is now {tier} <gray>(<white>{rating}<gray>)",
            "orbit.rating.placement_reveal_title" to "<gold><bold>PLACEMENTS COMPLETE",
            "orbit.rating.placement_reveal_subtitle" to "<gray>Your rank: {tier} <gray>(<white>{rating}<gray>)",
            "orbit.rating.tier_up" to "<green><bold>TIER UP! <reset><gray>You are now {tier}<gray>!",
            "orbit.rating.tier_down" to "<red>Tier down. <gray>You are now {tier}<gray>.",
        ))
        NebulaTestFixture.bootMinestom()
        instance = NebulaTestFixture.createFlatInstance()
        ratingManager = RatingManager(mockk<GameMode>(relaxed = true))
    }

    @AfterEach
    fun disconnectBots() {
        bots.forEach { NebulaTestFixture.disconnect(it) }
        bots.clear()
    }

    @AfterAll
    fun tearDown() {
        NebulaTestFixture.unregisterInstance(instance)
        NebulaTestFixture.shutdown()
    }

    @Test
    fun `revealPlacement sends title, subtitle, chat, and three sound packets`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)

        ratingManager.revealPlacement(bot.player, RatingTier.GOLD, rating = 1750)

        val titles = bot.interceptor.packetsOf<SetTitleTextPacket>(bot.player)
        val subtitles = bot.interceptor.packetsOf<SetTitleSubTitlePacket>(bot.player)
        val chats = bot.interceptor.packetsOf<SystemChatPacket>(bot.player)
        val sounds = bot.interceptor.packetsOf<SoundEffectPacket>(bot.player)

        assertTrue(titles.isNotEmpty(), "expected SetTitleTextPacket")
        assertTrue(subtitles.isNotEmpty(), "expected SetTitleSubTitlePacket")
        assertTrue(chats.isNotEmpty(), "expected SystemChatPacket carrying placement_complete")
        assertEquals(3, sounds.size, "expected 3 SoundEffectPackets (toast UI_TOAST_CHALLENGE_COMPLETE + ENTITY_PLAYER_LEVELUP + UI_TOAST_CHALLENGE_COMPLETE master)")
    }

    @Test
    fun `revealPlacement chat carries the resolved tier name`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)

        ratingManager.revealPlacement(bot.player, RatingTier.DIAMOND, rating = 2100)

        val chat = bot.interceptor.packetsOf<SystemChatPacket>(bot.player).single()
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(chat.message())
        assertTrue(plain.contains("Diamond"), "chat should mention 'Diamond' tier; got: $plain")
        assertTrue(plain.contains("2100"), "chat should mention rating 2100; got: $plain")
    }

    @Test
    fun `revealPlacement plays the level-up sound`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)

        ratingManager.revealPlacement(bot.player, RatingTier.PLATINUM, rating = 1850)

        val sounds = bot.interceptor.packetsOf<SoundEffectPacket>(bot.player)
        assertTrue(
            sounds.any { it.soundEvent() == SoundEvent.ENTITY_PLAYER_LEVELUP },
            "expected ENTITY_PLAYER_LEVELUP among sounds; got: ${sounds.map { it.soundEvent().key() }}",
        )
        assertTrue(
            sounds.any { it.soundEvent() == SoundEvent.UI_TOAST_CHALLENGE_COMPLETE },
            "expected UI_TOAST_CHALLENGE_COMPLETE among sounds",
        )
    }

    @Test
    fun `notifyTierChange promoted plays level-up and sends tier_up chat`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)

        ratingManager.notifyTierChange(bot.player, RatingTier.MASTER, promoted = true)

        val chat = bot.interceptor.packetsOf<SystemChatPacket>(bot.player).single()
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(chat.message())
        assertTrue(plain.contains("TIER UP"), "promoted chat should contain 'TIER UP'; got: $plain")
        assertTrue(plain.contains("Master"), "promoted chat should mention Master; got: $plain")

        val sounds = bot.interceptor.packetsOf<SoundEffectPacket>(bot.player)
        assertTrue(
            sounds.any { it.soundEvent() == SoundEvent.ENTITY_PLAYER_LEVELUP },
            "promoted should play ENTITY_PLAYER_LEVELUP",
        )
    }

    @Test
    fun `notifyTierChange demoted plays villager-no and sends tier_down chat`() {
        val bot = NebulaTestFixture.spawnBot(instance).also(bots::add)

        ratingManager.notifyTierChange(bot.player, RatingTier.GOLD, promoted = false)

        val chat = bot.interceptor.packetsOf<SystemChatPacket>(bot.player).single()
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(chat.message())
        assertTrue(plain.contains("Tier down"), "demoted chat should contain 'Tier down'; got: $plain")
        assertTrue(plain.contains("Gold"), "demoted chat should mention Gold; got: $plain")

        val sounds = bot.interceptor.packetsOf<SoundEffectPacket>(bot.player)
        assertTrue(
            sounds.any { it.soundEvent() == SoundEvent.ENTITY_VILLAGER_NO },
            "demoted should play ENTITY_VILLAGER_NO",
        )
    }
}
