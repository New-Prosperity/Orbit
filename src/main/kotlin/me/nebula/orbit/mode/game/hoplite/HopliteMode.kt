package me.nebula.orbit.mode.game.hoplite

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.gravity.session.SessionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.PlaceholderResolver
import me.nebula.orbit.mode.config.placeholderResolver
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.mode.game.GameSettings
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.kit.Kit
import me.nebula.orbit.utils.kit.kit
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.utils.matchresult.matchResult
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.stattracker.StatTracker
import me.nebula.orbit.utils.worldborder.ManagedWorldBorder
import me.nebula.orbit.utils.worldborder.managedWorldBorder
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task

class HopliteMode(private val resources: ResourceManager) : GameMode() {

    private val logger = logger("HopliteMode")
    private val config = resources.loadOrCopyDefault<HopliteModeConfig>("hoplite.json", "modes/hoplite.json")

    override val settings: GameSettings = GameSettings(
        worldPath = config.worldPath,
        preloadRadius = config.preloadRadius,
        spawn = config.spawn,
        scoreboard = config.scoreboard,
        tabList = config.tabList,
        lobby = config.lobby,
        hotbar = config.hotbar,
        timing = config.timing,
        cosmetics = config.cosmetics ?: CosmeticConfig(),
    )

    private val lastAttackerTag = Tag.UUID("hoplite_last_attacker")
    private var worldBorder: ManagedWorldBorder? = null
    private var borderShrinkTask: Task? = null
    private var eventNode: EventNode<*>? = null

    override fun buildPlaceholderResolver(): PlaceholderResolver = placeholderResolver {
        global("online") { SessionStore.cachedSize.toString() }
        global("server") { Orbit.serverName }
        global("alive") { tracker.aliveCount.toString() }
        global("phase") { phase.name }
        perPlayer("kills") { player -> StatTracker.get(player, "kills").toString() }
    }

    override fun onGameSetup(players: List<Player>) {
        val points = config.spawnPoints
        require(points.isNotEmpty()) { "HopliteMode requires at least one spawn point" }

        val starterKit = buildKit()
        val shuffled = players.shuffled()

        shuffled.forEachIndexed { index, player ->
            val point = points[index % points.size].toPos()
            player.teleport(point)
            starterKit.apply(player)
            player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
        }

        StatTracker.clear()

        val node = EventNode.all("hoplite-damage")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val target = event.entity as? Player ?: return@addListener
            if (phase != GamePhase.PLAYING || !tracker.isAlive(target.uuid)) return@addListener

            val damage = event.damage
            if (damage is EntityDamage) {
                val source = damage.source
                if (source is Player && source.uuid != target.uuid) {
                    target.setTag(lastAttackerTag, source.uuid)
                }
            }

            if (target.health - event.damage.amount <= 0) {
                event.isCancelled = true
                target.health = target.getAttributeValue(Attribute.MAX_HEALTH).toFloat()

                val killerUuid = target.getTag(lastAttackerTag)
                val killer = killerUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }

                if (killer != null && tracker.isAlive(killer.uuid)) {
                    StatTracker.increment(killer, "kills")
                }

                target.removeTag(lastAttackerTag)
                eliminate(target)
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        worldBorder = defaultInstance.managedWorldBorder {
            diameter(config.border.initialDiameter)
            center(config.border.centerX, config.border.centerZ)
        }
    }

    override fun onPlayingStart() {
        if (config.border.shrinkStartSeconds > 0) {
            borderShrinkTask = delay(config.border.shrinkStartSeconds * 20) {
                worldBorder?.shrinkTo(
                    config.border.finalDiameter,
                    config.border.shrinkDurationSeconds.toDouble(),
                )
            }
        } else {
            worldBorder?.shrinkTo(
                config.border.finalDiameter,
                config.border.shrinkDurationSeconds.toDouble(),
            )
        }
    }

    override fun checkWinCondition(): MatchResult? {
        if (tracker.aliveCount > 1) return null

        val winnerUuid = tracker.alive.firstOrNull()
        val winner = winnerUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }

        return buildResult(winner)
    }

    override fun onPlayerEliminated(player: Player) {
        val killerUuid = player.getTag(lastAttackerTag)
        val killer = killerUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
        val killerName = killer?.username ?: "?"

        defaultInstance.players.forEach { p ->
            p.sendMessage(p.translate(
                "orbit.game.hoplite.elimination",
                "victim" to player.username,
                "killer" to killerName,
            ))
        }
    }

    override fun onGameReset() {
        borderShrinkTask?.cancel()
        borderShrinkTask = null
        worldBorder?.setDiameter(config.border.initialDiameter)
        worldBorder = null
        StatTracker.clear()

        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }

    override fun buildTimeExpiredResult(): MatchResult {
        val topKiller = StatTracker.top("kills", 1).firstOrNull()
        val winner = topKiller?.let { (uuid, _) ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        }

        return buildResult(winner)
    }

    private fun buildResult(winner: Player?): MatchResult {
        val gameDuration = java.time.Duration.ofMillis(System.currentTimeMillis() - gameStartTime)

        return matchResult {
            if (winner != null) {
                winner(winner)
            } else {
                draw()
            }
            duration(gameDuration)
            metadata("mode", "hoplite")

            stat("Kills") {
                StatTracker.players().forEach { uuid ->
                    val kills = StatTracker.get(uuid, "kills")
                    if (kills > 0) {
                        val name = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.username
                            ?: uuid.toString().take(8)
                        player(uuid, name, kills.toDouble())
                    }
                }
            }
        }
    }

    private fun buildKit(): Kit = kit("hoplite-starter") {
        config.kit.helmet?.let { helmet(requireMaterial(it)) }
        config.kit.chestplate?.let { chestplate(requireMaterial(it)) }
        config.kit.leggings?.let { leggings(requireMaterial(it)) }
        config.kit.boots?.let { boots(requireMaterial(it)) }
        config.kit.items.forEach { kitItem ->
            item(kitItem.slot, requireMaterial(kitItem.material), kitItem.amount)
        }
    }

    private fun requireMaterial(key: String): Material =
        requireNotNull(Material.fromKey(key)) { "Unknown material: $key" }
}
