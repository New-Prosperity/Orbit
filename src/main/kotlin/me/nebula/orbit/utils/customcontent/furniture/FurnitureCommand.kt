package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.commandbuilder.furnitureArgument
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val selectedByStaff = ConcurrentHashMap<UUID, UUID>()

internal fun CommandBuilderDsl.installFurnitureSubcommands() {
    subCommand("furniture") {
        permission("orbit.furniture")

        subCommand("give") {
            furnitureArgument("id")
            intArgument("amount")
            onPlayerExecute {
                val id = argOrNull("id") ?: run {
                    replyMM("<red>Usage: /cc furniture give <id> [amount]")
                    return@onPlayerExecute
                }
                val amount = intArgOrNull("amount") ?: 1
                if (!Furniture.giveItem(player, id, amount)) {
                    replyMM("<red>Unknown furniture: <white>$id")
                    return@onPlayerExecute
                }
                replyMM("<green>Given <white>$amount × $id")
            }
        }

        subCommand("list") {
            onPlayerExecute {
                val defs = FurnitureRegistry.all().sortedBy { it.id }
                if (defs.isEmpty()) {
                    replyMM("<gray>No furniture registered.")
                    return@onPlayerExecute
                }
                replyMM("<gold><bold>Furniture</bold></gold> <dark_gray>(${defs.size})")
                defs.forEach { def ->
                    val footprintLabel = "${def.footprint.size} cell${if (def.footprint.size == 1) "" else "s"}"
                    val interactionLabel = def.interaction?.let { it::class.simpleName?.lowercase() } ?: "none"
                    val lightLabel = if (def.lightLevel > 0) " <yellow>✦${def.lightLevel}" else ""
                    replyMM("<gray>- <white>${def.id} <dark_gray>[$footprintLabel, $interactionLabel, ${def.collision.name.lowercase()}]$lightLabel")
                }
            }
        }

        subCommand("placed") {
            onPlayerExecute {
                val instance = player.instance ?: return@onPlayerExecute
                val placed = PlacedFurnitureStore.all(instance).sortedBy { it.definitionId }
                if (placed.isEmpty()) {
                    replyMM("<gray>No furniture placed in this instance.")
                    return@onPlayerExecute
                }
                replyMM("<gold><bold>Placed furniture</bold></gold> <dark_gray>(${placed.size}, rendered ${DisplayCullController.renderedCount()})")
                placed.take(30).forEach { f ->
                    val ownerName = f.owner?.let { "<white>${it.toString().take(8)}" } ?: "<dark_gray>unowned"
                    replyMM("<gray>- <white>${f.definitionId} <dark_gray>[${f.anchorX},${f.anchorY},${f.anchorZ}] yaw=${"%.1f".format(f.yawDegrees)}° <dark_gray>owner=$ownerName")
                }
                if (placed.size > 30) replyMM("<dark_gray>...and ${placed.size - 30} more.")
            }
        }

        subCommand("select") {
            onPlayerExecute {
                val instance = player.instance ?: return@onPlayerExecute
                val furniture = furnitureUnderCrosshair(player, instance)
                if (furniture == null) {
                    replyMM("<red>No furniture within 6 blocks of your crosshair.")
                    selectedByStaff.remove(player.uuid)
                    return@onPlayerExecute
                }
                selectedByStaff[player.uuid] = furniture.uuid
                replyMM("<green>Selected <white>${furniture.definitionId} <dark_gray>[${furniture.anchorX},${furniture.anchorY},${furniture.anchorZ}]")
            }
        }

        subCommand("rotate") {
            enumArgument("axis", "yaw", "pitch", "roll")
            floatArgument("degrees")
            onPlayerExecute {
                val instance = player.instance ?: return@onPlayerExecute
                val selected = selectedByStaff[player.uuid]?.let { PlacedFurnitureStore.byUuid(instance, it) }
                if (selected == null) {
                    replyMM("<red>Nothing selected. Use <white>/cc furniture select</white> first.")
                    return@onPlayerExecute
                }
                val axis = argOrNull("axis") ?: return@onPlayerExecute
                val degrees = runCatching { floatArg("degrees") }.getOrElse {
                    replyMM("<red>Usage: /cc furniture rotate <axis> <degrees>")
                    return@onPlayerExecute
                }
                val newYaw = if (axis == "yaw") degrees else selected.yawDegrees
                val newPitch = if (axis == "pitch") degrees else selected.pitchDegrees
                val newRoll = if (axis == "roll") degrees else selected.rollDegrees
                val updated = selected.copy(yawDegrees = newYaw, pitchDegrees = newPitch, rollDegrees = newRoll)
                PlacedFurnitureStore.updateTransform(instance, selected.uuid, updated)
                FurnitureDisplaySpawner.setRotation(
                    instance, selected.displayEntityId, newYaw, newPitch, newRoll,
                )
                replyMM("<green>Rotated <white>${selected.definitionId}</white> $axis=${"%.1f".format(degrees)}°")
            }
        }

        subCommand("delete") {
            onPlayerExecute {
                val instance = player.instance ?: return@onPlayerExecute
                val selectedUuid = selectedByStaff.remove(player.uuid)
                val furniture = selectedUuid?.let { PlacedFurnitureStore.byUuid(instance, it) }
                    ?: furnitureUnderCrosshair(player, instance)
                if (furniture == null) {
                    replyMM("<red>Nothing selected or within crosshair.")
                    return@onPlayerExecute
                }
                FurnitureListener.breakFurniture(player, furniture)
                replyMM("<green>Deleted <white>${furniture.definitionId}")
            }
        }

        subCommand("orphans") {
            onPlayerExecute {
                val instance = player.instance ?: return@onPlayerExecute
                val result = FurnitureOrphanReconciler.scan(instance)
                replyMM("<gold><bold>Orphan scan</bold></gold>")
                replyMM("<gray>Stored without barrier: <white>${result.storedWithoutBarrier.size}")
                replyMM("<gray>Barrier without store: <white>${result.barrierWithoutStore.size}")
                if (result.hasIssues) {
                    replyMM("<yellow>Run <white>/cc furniture repair</white> to fix.")
                }
            }
        }

        subCommand("repair") {
            onPlayerExecute {
                val instance = player.instance ?: return@onPlayerExecute
                val result = FurnitureOrphanReconciler.scanAndRepair(instance)
                replyMM("<green>Repaired <white>${result.storedWithoutBarrier.size + result.barrierWithoutStore.size}</white> orphan(s)")
            }
        }

        subCommand("wand") {
            onPlayerExecute {
                player.inventory.addItemStack(FurnitureWandController.createWandItem())
                replyMM("<green>Wand given. Right-click a furniture to grab it. See the item lore for controls.")
            }
        }

        onPlayerExecute {
            replyMM("<gold><bold>/cc furniture</bold></gold>")
            replyMM("<white> /cc furniture give <id> [amount]")
            replyMM("<white> /cc furniture list <dark_gray>- List registered furniture")
            replyMM("<white> /cc furniture placed <dark_gray>- List placed furniture in this instance")
            replyMM("<white> /cc furniture select <dark_gray>- Select the furniture at your crosshair")
            replyMM("<white> /cc furniture rotate <axis> <degrees> <dark_gray>- Rotate selected piece")
            replyMM("<white> /cc furniture delete <dark_gray>- Delete selected piece")
            replyMM("<white> /cc furniture wand <dark_gray>- Get the live-edit wand")
            replyMM("<white> /cc furniture orphans <dark_gray>- Scan for orphan barriers / store entries")
            replyMM("<white> /cc furniture repair <dark_gray>- Fix orphans in this instance")
        }
    }
}

private fun furnitureUnderCrosshair(player: Player, instance: Instance): FurnitureInstance? {
    val origin = player.position.add(0.0, player.eyeHeight, 0.0)
    val direction = player.position.direction()
    val maxDistance = 6.0
    var step = 0.0
    while (step <= maxDistance) {
        val x = (origin.x() + direction.x() * step).toInt()
        val y = (origin.y() + direction.y() * step).toInt()
        val z = (origin.z() + direction.z() * step).toInt()
        val hit = PlacedFurnitureStore.atCell(instance, x, y, z)
        if (hit != null) return hit
        step += 0.2
    }
    return null
}
