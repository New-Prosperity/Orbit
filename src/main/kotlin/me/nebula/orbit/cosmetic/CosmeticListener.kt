package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.orbit.mode.config.CosmeticConfig
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
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
            val level = data.owned[trailId] ?: 1
            CosmeticApplier.spawnTrailParticle(instance, player.position.add(0.0, 0.2, 0.0), trailId, level, ownerUuid = player.uuid)
        }

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            val data = loadEquipped(player) ?: return@addListener

            val armorSkinId = data.equipped[CosmeticCategory.ARMOR_SKIN.name]
            if (armorSkinId != null && isAllowed(CosmeticCategory.ARMOR_SKIN, armorSkinId)) {
                val level = data.owned[armorSkinId] ?: 1
                CosmeticApplier.applyArmorSkin(player, armorSkinId, level)
            }

            val spawnEffectId = data.equipped[CosmeticCategory.SPAWN_EFFECT.name]
            if (spawnEffectId != null && isAllowed(CosmeticCategory.SPAWN_EFFECT, spawnEffectId)) {
                val instance = player.instance ?: return@addListener
                val level = data.owned[spawnEffectId] ?: 1
                CosmeticApplier.playSpawnEffect(instance, player.position, spawnEffectId, level, ownerUuid = player.uuid)
            }

            val companionId = data.equipped[CosmeticCategory.COMPANION.name]
            if (companionId != null && isAllowed(CosmeticCategory.COMPANION, companionId)) {
                val level = data.owned[companionId] ?: 1
                CompanionManager.spawn(player, companionId, level)
            }

            val petId = data.equipped[CosmeticCategory.PET.name]
            if (petId != null && isAllowed(CosmeticCategory.PET, petId)) {
                val level = data.owned[petId] ?: 1
                PetManager.spawn(player, petId, level)
            }

            val gadgetId = data.equipped[CosmeticCategory.GADGET.name]
            if (gadgetId != null && isAllowed(CosmeticCategory.GADGET, gadgetId)) {
                val level = data.owned[gadgetId] ?: 1
                GadgetManager.equip(player, gadgetId, level)
            }

            val mountId = data.equipped[CosmeticCategory.MOUNT.name]
            if (mountId != null && isAllowed(CosmeticCategory.MOUNT, mountId)) {
                val level = data.owned[mountId] ?: 1
                CosmeticMountManager.spawn(player, mountId, level)
            }
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            CompanionManager.despawn(event.player.uuid)
            PetManager.despawn(event.player.uuid)
            GadgetManager.unequip(event.player)
            CosmeticMountManager.despawn(event.player.uuid)
        }

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            val slot = event.player.heldSlot.toInt()
            if (slot == GadgetManager.GADGET_SLOT && GadgetManager.isActive(event.player.uuid)) {
                GadgetManager.onUse(event.player)
            }
            if (slot == CosmeticMountManager.MOUNT_SLOT && CosmeticMountManager.isActive(event.player.uuid)) {
                CosmeticMountManager.toggleMount(event.player)
            }
        }

        handler.addChild(node)

        net.minestom.server.MinecraftServer.getSchedulerManager()
            .buildTask { tickProjectileTrails() }
            .repeat(TaskSchedule.tick(2)).schedule()
    }

    fun onPlayerEliminated(killer: Player, victimPosition: Pos) {
        val data = CosmeticStore.load(killer.uuid) ?: return
        val killEffectId = data.equipped[CosmeticCategory.KILL_EFFECT.name] ?: return
        if (!isAllowed(CosmeticCategory.KILL_EFFECT, killEffectId)) return
        val instance = killer.instance ?: return
        val level = data.owned[killEffectId] ?: 1
        CosmeticApplier.playKillEffect(instance, victimPosition, killEffectId, level, ownerUuid = killer.uuid)
    }

    fun onPlayerDeath(player: Player, deathPosition: Pos) {
        val data = CosmeticStore.load(player.uuid) ?: return

        val deathEffectId = data.equipped[CosmeticCategory.DEATH_EFFECT.name]
        if (deathEffectId != null && isAllowed(CosmeticCategory.DEATH_EFFECT, deathEffectId)) {
            val instance = player.instance ?: return
            val level = data.owned[deathEffectId] ?: 1
            CosmeticApplier.playDeathEffect(instance, deathPosition, deathEffectId, level, ownerUuid = player.uuid)
        }

        val gravestoneId = data.equipped[CosmeticCategory.GRAVESTONE.name]
        if (gravestoneId != null && isAllowed(CosmeticCategory.GRAVESTONE, gravestoneId)) {
            val instance = player.instance ?: return
            val level = data.owned[gravestoneId] ?: 1
            GravestoneManager.spawn(instance, deathPosition, gravestoneId, level, playerUuid = player.uuid)
        }
    }

    fun onGameWon(winner: Player) {
        val data = CosmeticStore.load(winner.uuid) ?: return
        val winEffectId = data.equipped[CosmeticCategory.WIN_EFFECT.name] ?: return
        if (!isAllowed(CosmeticCategory.WIN_EFFECT, winEffectId)) return
        val instance = winner.instance ?: return
        val level = data.owned[winEffectId] ?: 1
        CosmeticApplier.playWinEffect(instance, winner, winEffectId, level)
    }

    fun resolveEliminationMessage(killer: Player, victim: Player, defaultKey: String): String {
        val data = CosmeticStore.load(killer.uuid) ?: return defaultKey
        val elimMsgId = data.equipped[CosmeticCategory.ELIMINATION_MESSAGE.name] ?: return defaultKey
        if (!isAllowed(CosmeticCategory.ELIMINATION_MESSAGE, elimMsgId)) return defaultKey
        val definition = CosmeticRegistry[elimMsgId] ?: return defaultKey
        val level = data.owned[elimMsgId] ?: 1
        val resolved = definition.resolveData(level)
        return resolved["formatKey"] ?: defaultKey
    }

    fun resolveJoinMessage(player: Player, defaultKey: String): String {
        val data = CosmeticStore.load(player.uuid) ?: return defaultKey
        val msgId = data.equipped[CosmeticCategory.JOIN_QUIT_MESSAGE.name] ?: return defaultKey
        if (!isAllowed(CosmeticCategory.JOIN_QUIT_MESSAGE, msgId)) return defaultKey
        val definition = CosmeticRegistry[msgId] ?: return defaultKey
        val level = data.owned[msgId] ?: 1
        val resolved = definition.resolveData(level)
        return resolved["joinFormatKey"] ?: defaultKey
    }

    fun resolveQuitMessage(player: Player, defaultKey: String): String {
        val data = CosmeticStore.load(player.uuid) ?: return defaultKey
        val msgId = data.equipped[CosmeticCategory.JOIN_QUIT_MESSAGE.name] ?: return defaultKey
        if (!isAllowed(CosmeticCategory.JOIN_QUIT_MESSAGE, msgId)) return defaultKey
        val definition = CosmeticRegistry[msgId] ?: return defaultKey
        val level = data.owned[msgId] ?: 1
        val resolved = definition.resolveData(level)
        return resolved["quitFormatKey"] ?: defaultKey
    }

    internal fun isAllowed(category: CosmeticCategory, cosmeticId: String): Boolean =
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
                    val level = data.owned[trailId] ?: 1
                    CosmeticApplier.spawnProjectileTrailParticle(instance, projectile.position, trailId, level, ownerUuid = shooter.uuid)
                }
        }
    }

    private fun loadEquipped(player: Player): CosmeticPlayerData? =
        CosmeticStore.load(player.uuid)?.takeIf { it.equipped.isNotEmpty() }
}
