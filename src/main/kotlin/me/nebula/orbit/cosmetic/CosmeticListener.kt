package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
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
import net.minestom.server.timer.Task

private val TRAIL_TICK_TAG = Tag.Long("cosmetic:trail:last_tick").defaultValue(0L)
private val SHOOTER_TAG = Tag.Integer("mechanic:projectile:shooter")

object CosmeticListener {

    @Volatile var activeConfig = CosmeticConfig()
    lateinit var context: CosmeticContext

    private var eventNode: EventNode<*>? = null
    private var projectileTrailTask: Task? = null

    fun install(handler: GlobalEventHandler) {
        val ctx = context
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

            if (!event.isFirstSpawn) {
                ctx.despawnAll(player)
            }

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
                ctx.companions.spawn(player, companionId, level)
            }

            val petId = data.equipped[CosmeticCategory.PET.name]
            if (petId != null && isAllowed(CosmeticCategory.PET, petId)) {
                val level = data.owned[petId] ?: 1
                ctx.pets.spawn(player, petId, level)
            }

            val gadgetId = data.equipped[CosmeticCategory.GADGET.name]
            if (gadgetId != null && isAllowed(CosmeticCategory.GADGET, gadgetId)) {
                val level = data.owned[gadgetId] ?: 1
                ctx.gadgets.equip(player, gadgetId, level)
            }

            val mountId = data.equipped[CosmeticCategory.MOUNT.name]
            if (mountId != null && isAllowed(CosmeticCategory.MOUNT, mountId)) {
                val level = data.owned[mountId] ?: 1
                ctx.mounts.spawn(player, mountId, level)
            }
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            CosmeticDataCache.invalidate(event.player.uuid)
            ctx.despawnAll(event.player)
        }

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            val slot = event.player.heldSlot.toInt()
            if (slot == GadgetManager.GADGET_SLOT && ctx.gadgets.isActive(event.player.uuid)) {
                ctx.gadgets.onUse(event.player)
            }
            if (slot == CosmeticMountManager.MOUNT_SLOT && ctx.mounts.isActive(event.player.uuid)) {
                ctx.mounts.toggleMount(event.player)
            }
        }

        handler.addChild(node)
        eventNode = node

        projectileTrailTask = repeat(2) { tickProjectileTrails() }
    }

    fun uninstall() {
        projectileTrailTask?.cancel()
        projectileTrailTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        CosmeticDataCache.clear()
    }

    fun onPlayerEliminated(killer: Player, victimPosition: Pos) {
        val data = CosmeticDataCache.get(killer.uuid) ?: return
        val killEffectId = data.equipped[CosmeticCategory.KILL_EFFECT.name] ?: return
        if (!isAllowed(CosmeticCategory.KILL_EFFECT, killEffectId)) return
        val instance = killer.instance ?: return
        val level = data.owned[killEffectId] ?: 1
        CosmeticApplier.playKillEffect(instance, victimPosition, killEffectId, level, ownerUuid = killer.uuid)
    }

    fun onPlayerDeath(player: Player, deathPosition: Pos) {
        val data = CosmeticDataCache.get(player.uuid) ?: return

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
            context.gravestones.spawn(instance, deathPosition, gravestoneId, level, playerUuid = player.uuid)
        }

        context.despawnAll(player.uuid)
    }

    fun onGameWon(winner: Player) {
        val data = CosmeticDataCache.get(winner.uuid) ?: return
        val winEffectId = data.equipped[CosmeticCategory.WIN_EFFECT.name] ?: return
        if (!isAllowed(CosmeticCategory.WIN_EFFECT, winEffectId)) return
        val instance = winner.instance ?: return
        val level = data.owned[winEffectId] ?: 1
        CosmeticApplier.playWinEffect(instance, winner, winEffectId, level)
    }

    fun resolveEliminationMessage(killer: Player, victim: Player, defaultKey: String): String {
        val data = CosmeticDataCache.get(killer.uuid) ?: return defaultKey
        val elimMsgId = data.equipped[CosmeticCategory.ELIMINATION_MESSAGE.name] ?: return defaultKey
        if (!isAllowed(CosmeticCategory.ELIMINATION_MESSAGE, elimMsgId)) return defaultKey
        val definition = CosmeticRegistry[elimMsgId] ?: return defaultKey
        val level = data.owned[elimMsgId] ?: 1
        val resolved = definition.resolveData(level)
        return resolved["formatKey"] ?: defaultKey
    }

    fun resolveJoinMessage(player: Player, defaultKey: String): String {
        val data = CosmeticDataCache.get(player.uuid) ?: return defaultKey
        val msgId = data.equipped[CosmeticCategory.JOIN_QUIT_MESSAGE.name] ?: return defaultKey
        if (!isAllowed(CosmeticCategory.JOIN_QUIT_MESSAGE, msgId)) return defaultKey
        val definition = CosmeticRegistry[msgId] ?: return defaultKey
        val level = data.owned[msgId] ?: 1
        val resolved = definition.resolveData(level)
        return resolved["joinFormatKey"] ?: defaultKey
    }

    fun resolveQuitMessage(player: Player, defaultKey: String): String {
        val data = CosmeticDataCache.get(player.uuid) ?: return defaultKey
        val msgId = data.equipped[CosmeticCategory.JOIN_QUIT_MESSAGE.name] ?: return defaultKey
        if (!isAllowed(CosmeticCategory.JOIN_QUIT_MESSAGE, msgId)) return defaultKey
        val definition = CosmeticRegistry[msgId] ?: return defaultKey
        val level = data.owned[msgId] ?: 1
        val resolved = definition.resolveData(level)
        return resolved["quitFormatKey"] ?: defaultKey
    }

    internal fun isAllowed(category: CosmeticCategory, cosmeticId: String): Boolean {
        val config = activeConfig
        return category.name in config.enabledCategories && cosmeticId !in config.blacklist
    }

    private fun tickProjectileTrails() {
        for (instance in MinecraftServer.getInstanceManager().instances) {
            for (entity in instance.entities) {
                if (entity.entityType != EntityType.ARROW && entity.entityType != EntityType.SPECTRAL_ARROW) continue
                val shooterId = runCatching { entity.getTag(SHOOTER_TAG) }.getOrNull() ?: continue
                val shooter = instance.getEntityById(shooterId) as? Player ?: continue
                val data = CosmeticDataCache.get(shooter.uuid) ?: continue
                val trailId = data.equipped[CosmeticCategory.PROJECTILE_TRAIL.name] ?: continue
                if (!isAllowed(CosmeticCategory.PROJECTILE_TRAIL, trailId)) continue
                val level = data.owned[trailId] ?: 1
                CosmeticApplier.spawnProjectileTrailParticle(instance, entity.position, trailId, level, ownerUuid = shooter.uuid)
            }
        }
    }

    private fun loadEquipped(player: Player): CosmeticPlayerData? =
        CosmeticDataCache.get(player.uuid)?.takeIf { it.equipped.isNotEmpty() }
}
