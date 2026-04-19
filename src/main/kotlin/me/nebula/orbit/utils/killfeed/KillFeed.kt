package me.nebula.orbit.utils.killfeed

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.vanish.VanishManager
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import me.nebula.gravity.translation.Keys

data class KillEvent(
    val killer: Player?,
    val victim: Player,
    val weaponKey: String? = null,
    val distance: Double? = null,
)

enum class KillType { MELEE, RANGED, ENVIRONMENTAL, OTHER }

object WeaponIcons {

    private const val DEFAULT_ICON = "\u2620"

    private val exactIcons = mapOf(
        "fall" to "\u21A7",
        "void" to "\u25CB",
        "explosion" to "\u273A",
        "fire" to "\u2668",
        "drown" to "\u2248",
        "magic" to "\u2726",
    )

    private val keywordIcons = listOf(
        "sword" to "\u2694",
        "axe" to "\u26CF",
        "trident" to "\uD83D\uDD31",
        "crossbow" to "\uD83C\uDFF9",
        "bow" to "\uD83C\uDFF9",
        "mace" to "\u2692",
    )

    fun resolve(weaponKey: String?): String {
        if (weaponKey == null) return DEFAULT_ICON
        val key = weaponKey.lowercase()
        exactIcons[key]?.let { return it }
        for ((keyword, icon) in keywordIcons) {
            if (keyword in key) return icon
        }
        return DEFAULT_ICON
    }

    fun classify(weaponKey: String?, killer: Player?): KillType {
        if (killer == null) return KillType.ENVIRONMENTAL
        if (weaponKey == null) return KillType.OTHER
        val key = weaponKey.lowercase()
        if (exactIcons.containsKey(key)) return KillType.ENVIRONMENTAL
        if ("bow" in key || "crossbow" in key || "trident" in key) return KillType.RANGED
        if ("sword" in key || "axe" in key || "mace" in key) return KillType.MELEE
        return KillType.OTHER
    }
}

private const val LONG_RANGE_THRESHOLD_BLOCKS = 30.0

fun interface KillFeedRenderer {
    fun render(event: KillEvent, viewer: Player): net.kyori.adventure.text.Component
}

fun interface KillFeedEffect {
    fun play(event: KillEvent, viewers: Collection<Player>)
}

class KillFeed @PublishedApi internal constructor(
    private val tracker: PlayerTracker,
    private val renderer: KillFeedRenderer,
    private val effects: List<KillFeedEffect>,
    private val multiKillWindowMillis: Long,
    private val multiKillMessages: Map<Int, String>,
    private val streakMessages: Map<Int, String>,
    private val firstBloodKey: TranslationKey?,
    private val broadcastProvider: () -> Collection<Player>,
) {

    private val multiKillTracker = ConcurrentHashMap<UUID, MultiKillState>()
    private val firstBloodClaimed = AtomicBoolean(false)

    fun reportKill(event: KillEvent) {
        val allViewers = broadcastProvider()
        val viewers = allViewers.filter { viewer ->
            (event.killer == null || VanishManager.canSee(viewer, event.killer)) &&
                VanishManager.canSee(viewer, event.victim)
        }

        for (viewer in viewers) {
            viewer.sendMessage(renderer.render(event, viewer))
        }

        for (effect in effects) {
            effect.play(event, viewers)
        }

        if (event.killer != null) {
            handleFirstBlood(event.killer, viewers)
            handleMultiKill(event.killer, viewers)
            handleStreak(event.killer, viewers)
        }
    }

    fun removePlayer(uuid: UUID) {
        multiKillTracker.remove(uuid)
    }

    fun clear() {
        multiKillTracker.clear()
        firstBloodClaimed.set(false)
    }

    private fun handleFirstBlood(killer: Player, viewers: Collection<Player>) {
        if (firstBloodKey == null) return
        if (!firstBloodClaimed.compareAndSet(false, true)) return
        for (viewer in viewers) {
            viewer.sendMessage(viewer.translate(firstBloodKey, "killer" to killer.username))
        }
        killer.playSound(Sound.sound(Key.key("entity.ender_dragon.growl"), Sound.Source.PLAYER, 0.8f, 1.2f))
    }

    private fun handleMultiKill(killer: Player, viewers: Collection<Player>) {
        if (multiKillMessages.isEmpty()) return
        val now = System.currentTimeMillis()
        val state = checkNotNull(multiKillTracker.compute(killer.uuid) { _, existing ->
            if (existing != null && now - existing.lastKillTime < multiKillWindowMillis) {
                existing.copy(count = existing.count + 1, lastKillTime = now)
            } else {
                MultiKillState(1, now)
            }
        })

        val messageKey = multiKillMessages[state.count] ?: return
        for (viewer in viewers) {
            viewer.sendMessage(viewer.translate(messageKey.asTranslationKey(), "killer" to killer.username, "count" to state.count.toString()))
        }
    }

    private fun handleStreak(killer: Player, viewers: Collection<Player>) {
        if (streakMessages.isEmpty()) return
        val streak = tracker.streakOf(killer.uuid)
        val messageKey = streakMessages[streak] ?: return
        for (viewer in viewers) {
            viewer.sendMessage(viewer.translate(messageKey.asTranslationKey(), "killer" to killer.username, "streak" to streak.toString()))
        }
        killer.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.PLAYER, 1f, 1.2f))
    }
}

private data class MultiKillState(val count: Int, val lastKillTime: Long)

class KillFeedBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var tracker: PlayerTracker? = null
    @PublishedApi internal var renderer: KillFeedRenderer = KillFeedRenderer { event, viewer ->
        val killerName = event.killer?.username ?: "?"
        val icon = WeaponIcons.resolve(event.weaponKey)
        val type = WeaponIcons.classify(event.weaponKey, event.killer)
        val distanceSuffix = event.distance
            ?.takeIf { it >= LONG_RANGE_THRESHOLD_BLOCKS }
            ?.let { viewer.translateRaw(Keys.Orbit.Killfeed.DistanceSuffix, "distance" to it.toInt().toString()) }
            ?: ""
        val key = when (type) {
            KillType.MELEE -> "orbit.killfeed.melee"
            KillType.RANGED -> "orbit.killfeed.ranged"
            KillType.ENVIRONMENTAL -> "orbit.killfeed.environmental"
            KillType.OTHER -> "orbit.killfeed.default"
        }
        viewer.translate(key.asTranslationKey(),
            "killer" to killerName,
            "victim" to event.victim.username,
            "weapon" to icon,
            "distance" to distanceSuffix,
        )
    }
    @PublishedApi internal val effects = mutableListOf<KillFeedEffect>()
    @PublishedApi internal var multiKillWindowMillis: Long = 5000L
    @PublishedApi internal val multiKillMessages = mutableMapOf<Int, String>()
    @PublishedApi internal val streakMessages = mutableMapOf<Int, String>()
    @PublishedApi internal var firstBloodKey: TranslationKey? = null
    @PublishedApi internal var broadcastProvider: () -> Collection<Player> = {
        MinecraftServer.getConnectionManager().onlinePlayers
    }

    fun tracker(tracker: PlayerTracker) { this.tracker = tracker }
    fun renderer(renderer: KillFeedRenderer) { this.renderer = renderer }
    fun effect(effect: KillFeedEffect) { effects.add(effect) }
    fun multiKillWindow(millis: Long) { multiKillWindowMillis = millis }
    fun multiKill(count: Int, translationKey: String) { multiKillMessages[count] = translationKey }
    fun streak(count: Int, translationKey: String) { streakMessages[count] = translationKey }
    fun firstBlood(translationKey: String) { firstBloodKey = translationKey.asTranslationKey() }
    fun broadcastTo(provider: () -> Collection<Player>) { broadcastProvider = provider }

    @PublishedApi internal fun build(): KillFeed {
        val t = requireNotNull(tracker) { "PlayerTracker must be set" }
        return KillFeed(t, renderer, effects.toList(), multiKillWindowMillis, multiKillMessages.toMap(), streakMessages.toMap(), firstBloodKey, broadcastProvider)
    }
}

inline fun killFeed(block: KillFeedBuilder.() -> Unit): KillFeed =
    KillFeedBuilder().apply(block).build()
