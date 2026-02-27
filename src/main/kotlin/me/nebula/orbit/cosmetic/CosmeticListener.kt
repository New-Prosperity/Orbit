package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.orbit.mode.config.CosmeticConfig
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule

private val TRAIL_TICK_TAG = Tag.Long("cosmetic:trail:last_tick").defaultValue(0L)
private val SHOOTER_TAG = Tag.Integer("mechanic:projectile:shooter")

object CosmeticListener {

    @Volatile var activeConfig = CosmeticConfig()

    fun install(handler: GlobalEventHandler) {
        val node = EventNode.all("cosmetic-listeners")

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val now = System.currentTimeMillis()
            val lastTick = player.getTag(TRAIL_TICK_TAG)
            if (now - lastTick < 200) return@addListener

            val data = loadEquipped(player) ?: return@addListener
            val trailId = data.equipped[CosmeticCategory.TRAIL.name] ?: return@addListener
            if (!isAllowed(CosmeticCategory.TRAIL, trailId)) return@addListener

            player.setTag(TRAIL_TICK_TAG, now)
            val instance = player.instance ?: return@addListener
            CosmeticApplier.spawnTrailParticle(instance, event.player.position.add(0.0, 0.2, 0.0), trailId)
        }

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            val data = loadEquipped(player) ?: return@addListener
            val armorSkinId = data.equipped[CosmeticCategory.ARMOR_SKIN.name] ?: return@addListener
            if (!isAllowed(CosmeticCategory.ARMOR_SKIN, armorSkinId)) return@addListener
            CosmeticApplier.applyArmorSkin(player, armorSkinId)
        }

        handler.addChild(node)

        net.minestom.server.MinecraftServer.getSchedulerManager()
            .buildTask { tickProjectileTrails() }
            .repeat(TaskSchedule.tick(2)).schedule()
    }

    fun onPlayerEliminated(killer: Player, victimPosition: net.minestom.server.coordinate.Pos) {
        val data = CosmeticStore.load(killer.uuid) ?: return
        val killEffectId = data.equipped[CosmeticCategory.KILL_EFFECT.name] ?: return
        if (!isAllowed(CosmeticCategory.KILL_EFFECT, killEffectId)) return
        val instance = killer.instance ?: return
        CosmeticApplier.playKillEffect(instance, victimPosition, killEffectId)
    }

    fun onGameWon(winner: Player) {
        val data = CosmeticStore.load(winner.uuid) ?: return
        val winEffectId = data.equipped[CosmeticCategory.WIN_EFFECT.name] ?: return
        if (!isAllowed(CosmeticCategory.WIN_EFFECT, winEffectId)) return
        CosmeticApplier.playWinEffect(winner, winEffectId)
    }

    private fun isAllowed(category: CosmeticCategory, cosmeticId: String): Boolean =
        category.name in activeConfig.enabledCategories && cosmeticId !in activeConfig.blacklist

    private fun tickProjectileTrails() {
        net.minestom.server.MinecraftServer.getInstanceManager().instances.forEach { instance ->
            instance.entities
                .filter { it.entityType == EntityType.ARROW || it.entityType == EntityType.SPECTRAL_ARROW }
                .forEach { projectile ->
                    val shooterId = runCatching { projectile.getTag(SHOOTER_TAG) }.getOrNull() ?: return@forEach
                    val shooter = instance.players.firstOrNull { it.entityId == shooterId } ?: return@forEach
                    val data = CosmeticStore.load(shooter.uuid) ?: return@forEach
                    val trailId = data.equipped[CosmeticCategory.PROJECTILE_TRAIL.name] ?: return@forEach
                    if (!isAllowed(CosmeticCategory.PROJECTILE_TRAIL, trailId)) return@forEach
                    CosmeticApplier.spawnProjectileTrailParticle(instance, projectile.position, trailId)
                }
        }
    }

    private fun loadEquipped(player: Player): CosmeticPlayerData? =
        CosmeticStore.load(player.uuid)?.takeIf { it.equipped.isNotEmpty() }
}
