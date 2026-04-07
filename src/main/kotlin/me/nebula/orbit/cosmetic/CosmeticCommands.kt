package me.nebula.orbit.cosmetic

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = logger("CosmeticCommands")

private data class PreviewState(
    val category: CosmeticCategory,
    val cosmeticId: String,
    val previousEquippedId: String?,
)

private val activePreviews = ConcurrentHashMap<UUID, PreviewState>()

fun cosmeticsCommand(): Command = command("cosmetics") {
    aliases("cosmetic")
    stringArrayArgument("args")
    tabComplete { player, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last(), player) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            CosmeticMenu.openCategoryMenu(player)
            return@onPlayerExecute
        }
        val resolved = resolvePlayer(cmdArgs[0], player)
        if (resolved == null) {
            reply("orbit.command.player_not_found", "name" to cmdArgs[0])
            return@onPlayerExecute
        }
        val (targetUuid, targetName) = resolved
        CosmeticShowcaseMenu.open(player, targetUuid, targetName)
    }
}

fun previewCommand(): Command = command("preview") {
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) {
            CosmeticRegistry.all().map { it.id }
                .filter { it.startsWith(tokens.last(), ignoreCase = true) }
        } else emptyList()
    }
    onPlayerExecute {
        if (Orbit.gameMode != null) {
            reply("orbit.cosmetic.preview_hub_only")
            return@onPlayerExecute
        }

        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            replyMM("<red>Usage: /preview <cosmeticId>")
            return@onPlayerExecute
        }

        val cosmeticId = cmdArgs[0]
        val definition = CosmeticRegistry[cosmeticId]
        if (definition == null) {
            replyMM("<red>Unknown cosmetic: <white>$cosmeticId")
            return@onPlayerExecute
        }

        if (activePreviews.containsKey(player.uuid)) {
            endPreview(player)
        }

        val category = definition.category
        val data = CosmeticStore.load(player.uuid)
        val previousEquipped = data?.equipped?.get(category.name)

        activePreviews[player.uuid] = PreviewState(category, cosmeticId, previousEquipped)

        spawnPreview(player, category, cosmeticId)

        player.sendMessage(player.translate("orbit.cosmetic.preview_start",
            "cosmetic" to player.translateRaw(definition.nameKey)))

        delay(600) {
            if (activePreviews.containsKey(player.uuid)) {
                endPreview(player)
                player.sendMessage(player.translate("orbit.cosmetic.preview_end"))
            }
        }
    }
}

private fun spawnPreview(player: Player, category: CosmeticCategory, cosmeticId: String) {
    when (category) {
        CosmeticCategory.PET -> PetManager.spawn(player, cosmeticId, 1)
        CosmeticCategory.COMPANION -> CompanionManager.spawn(player, cosmeticId, 1)
        CosmeticCategory.MOUNT -> CosmeticMountManager.spawn(player, cosmeticId, 1)
        CosmeticCategory.GADGET -> GadgetManager.equip(player, cosmeticId, 1)
        CosmeticCategory.AURA -> {}
        CosmeticCategory.ARMOR_SKIN -> CosmeticApplier.applyArmorSkin(player, cosmeticId, 1)
        CosmeticCategory.SPAWN_EFFECT -> {
            val instance = player.instance ?: return
            CosmeticApplier.playSpawnEffect(instance, player.position, cosmeticId, 1, ownerUuid = player.uuid)
        }
        CosmeticCategory.DEATH_EFFECT -> {
            val instance = player.instance ?: return
            CosmeticApplier.playDeathEffect(instance, player.position, cosmeticId, 1, ownerUuid = player.uuid)
        }
        CosmeticCategory.KILL_EFFECT -> {
            val instance = player.instance ?: return
            CosmeticApplier.playKillEffect(instance, player.position, cosmeticId, 1, ownerUuid = player.uuid)
        }
        CosmeticCategory.WIN_EFFECT -> {
            val instance = player.instance ?: return
            CosmeticApplier.playWinEffect(instance, player, cosmeticId, 1)
        }
        CosmeticCategory.TRAIL -> {}
        CosmeticCategory.PROJECTILE_TRAIL -> {}
        CosmeticCategory.GRAVESTONE -> {
            val instance = player.instance ?: return
            GravestoneManager.spawn(instance, player.position, cosmeticId, 1, playerUuid = player.uuid)
        }
        CosmeticCategory.ELIMINATION_MESSAGE -> {}
        CosmeticCategory.JOIN_QUIT_MESSAGE -> {}
    }
}

fun endPreview(player: Player) {
    val state = activePreviews.remove(player.uuid) ?: return
    when (state.category) {
        CosmeticCategory.PET -> PetManager.despawn(player.uuid)
        CosmeticCategory.COMPANION -> CompanionManager.despawn(player.uuid)
        CosmeticCategory.MOUNT -> CosmeticMountManager.despawn(player.uuid)
        CosmeticCategory.GADGET -> GadgetManager.unequip(player)
        CosmeticCategory.ARMOR_SKIN -> {
            if (state.previousEquippedId != null) {
                val data = CosmeticStore.load(player.uuid)
                val level = data?.owned?.get(state.previousEquippedId) ?: 1
                CosmeticApplier.applyArmorSkin(player, state.previousEquippedId, level)
            } else {
                CosmeticApplier.clearArmorSkin(player)
            }
        }
        else -> {}
    }

    if (state.previousEquippedId != null) {
        when (state.category) {
            CosmeticCategory.PET -> {
                val data = CosmeticStore.load(player.uuid)
                val level = data?.owned?.get(state.previousEquippedId) ?: 1
                PetManager.spawn(player, state.previousEquippedId, level)
            }
            CosmeticCategory.COMPANION -> {
                val data = CosmeticStore.load(player.uuid)
                val level = data?.owned?.get(state.previousEquippedId) ?: 1
                CompanionManager.spawn(player, state.previousEquippedId, level)
            }
            CosmeticCategory.MOUNT -> {
                val data = CosmeticStore.load(player.uuid)
                val level = data?.owned?.get(state.previousEquippedId) ?: 1
                CosmeticMountManager.spawn(player, state.previousEquippedId, level)
            }
            CosmeticCategory.GADGET -> {
                val data = CosmeticStore.load(player.uuid)
                val level = data?.owned?.get(state.previousEquippedId) ?: 1
                GadgetManager.equip(player, state.previousEquippedId, level)
            }
            else -> {}
        }
    }
}

fun installCosmeticInteraction(handler: GlobalEventHandler) {
    val node = EventNode.all("cosmetic-interaction")

    node.addListener(PlayerEntityInteractEvent::class.java) { event ->
        if (Orbit.gameMode != null) return@addListener
        val target = event.target as? Player ?: return@addListener
        CosmeticShowcaseMenu.open(event.player, target.uuid, target.username)
    }

    node.addListener(PlayerDisconnectEvent::class.java) { event ->
        val player = event.player
        if (activePreviews.containsKey(player.uuid)) {
            endPreview(player)
        }
    }

    handler.addChild(node)
}
