package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.itemresolver.ITEM_ID_TAG
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.metadata.item.ItemEntityMeta
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import java.util.UUID

object FurnitureListener {

    private val logger = logger("FurnitureListener")

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            if (FurnitureWandController.isWandHeld(player)) return@addListener
            val held = player.itemInMainHand
            val placingDef = furnitureFromItem(held)
            if (placingDef != null) {
                event.isCancelled = true
                event.setBlockingItemUse(true)
                if (!placingDef.placement.allows(event.blockFace)) {
                    player.sendMessage(Component.text(
                        "Can't place ${placingDef.id} on the ${event.blockFace.name.lowercase()} face.",
                        NamedTextColor.RED,
                    ))
                    return@addListener
                }
                val anchor = resolveAnchor(event.blockPosition, event.blockFace)
                tryPlace(player, instance, anchor, placingDef, held, event.blockFace)
                return@addListener
            }
            if (event.isCancelled) return@addListener
            val pos = event.blockPosition
            val furniture = PlacedFurnitureStore.atCell(instance, pos.blockX(), pos.blockY(), pos.blockZ())
                ?: return@addListener
            event.isCancelled = true
            interactWith(player, furniture)
        }

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val furniture = PlacedFurnitureStore.byInteractionEntity(instance, event.target.entityId)
                ?: return@addListener
            interactWith(event.player, furniture)
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val heldItem = event.player.itemInMainHand
            if (furnitureFromItem(heldItem) != null) {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.isCancelled) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val furniture = PlacedFurnitureStore.atCell(instance, pos.blockX(), pos.blockY(), pos.blockZ())
                ?: return@addListener
            event.isCancelled = true
            breakFurniture(event.player, furniture)
        }

        eventNode.addListener(net.minestom.server.event.player.PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            FurnitureDisplaySpawner.showAllTo(event.player)
        }
    }

    fun furnitureFromItem(stack: ItemStack): FurnitureDefinition? {
        val itemId = stack.getTag(ITEM_ID_TAG) ?: return null
        return FurnitureRegistry.fromItemId(itemId)
    }

    fun tryPlace(
        player: Player,
        instance: Instance,
        anchor: Point,
        definition: FurnitureDefinition,
        heldItem: ItemStack,
        face: net.minestom.server.instance.block.BlockFace = net.minestom.server.instance.block.BlockFace.TOP,
    ): Boolean {
        val preEvent = FurniturePlacePreEvent(player, definition, anchor)
        MinecraftServer.getGlobalEventHandler().call(preEvent)
        if (preEvent.isCancelled) {
            player.sendMessage(Component.text(
                "Placement blocked here.",
                NamedTextColor.RED,
            ))
            return false
        }

        val anchorX = anchor.blockX()
        val anchorY = anchor.blockY()
        val anchorZ = anchor.blockZ()

        val facingYawRaw = (player.position.yaw() + 180f) % 360f
        val quarterTurns = FootprintRotation.yawToQuarterTurns(facingYawRaw)
        val visualYaw = FootprintRotation.snapYaw(facingYawRaw, definition.visualRotationSnap)
        val euler = FurniturePlacementRotation.eulerFor(face, visualYaw, definition.placement.autoOrient)

        val rotatedFootprint = FootprintRotation.rotate(definition.footprint, quarterTurns)
        val cellPositions = rotatedFootprint.cells.map {
            Triple(anchorX + it.dx, anchorY + it.dy, anchorZ + it.dz)
        }

        for ((cx, cy, cz) in cellPositions) {
            val existing = instance.getBlock(cx, cy, cz)
            if (!existing.isAir) {
                player.sendMessage(Component.text(
                    "Cannot place — ${existing.name()} at ($cx, $cy, $cz).",
                    NamedTextColor.RED,
                ))
                return false
            }
            if (PlacedFurnitureStore.atCell(instance, cx, cy, cz) != null) {
                player.sendMessage(Component.text(
                    "Cannot place — furniture already at ($cx, $cy, $cz).",
                    NamedTextColor.RED,
                ))
                return false
            }
        }

        val display = FurnitureDisplaySpawner.spawn(
            definition, instance, anchorX, anchorY, anchorZ,
            yawDegrees = euler.yawDegrees,
            pitchDegrees = euler.pitchDegrees,
            rollDegrees = euler.rollDegrees,
        )

        val placedCells = mutableListOf<Triple<Int, Int, Int>>()
        val spawnedInteractions = mutableListOf<Entity>()
        try {
            when (definition.collision) {
                FurnitureCollision.Solid -> {
                    for ((i, position) in cellPositions.withIndex()) {
                        val baseCell = definition.footprint.cells[i]
                        val block = blockForCell(definition, baseCell)
                        instance.setBlock(position.first, position.second, position.third, block)
                        placedCells += position
                    }
                }
                FurnitureCollision.NonSolid -> {
                    for ((cx, cy, cz) in cellPositions) {
                        spawnedInteractions += InteractionEntitySpawner.spawnAt(instance, cx, cy, cz)
                    }
                }
            }
        } catch (e: Exception) {
            display.remove()
            for ((cx, cy, cz) in placedCells) instance.setBlock(cx, cy, cz, Block.AIR)
            spawnedInteractions.forEach { it.remove() }
            logger.warn { "Failed to place furniture ${definition.id} at $anchorX,$anchorY,$anchorZ: ${e.message}" }
            return false
        }

        val cellKeys = if (definition.collision == FurnitureCollision.Solid) {
            FurnitureInstance.cellKeysOf(definition, anchorX, anchorY, anchorZ, quarterTurns)
        } else emptyList()

        val furniture = FurnitureInstance(
            uuid = UUID.randomUUID(),
            definitionId = definition.id,
            instance = instance,
            anchorX = anchorX,
            anchorY = anchorY,
            anchorZ = anchorZ,
            yawDegrees = euler.yawDegrees,
            pitchDegrees = euler.pitchDegrees,
            rollDegrees = euler.rollDegrees,
            displayEntityId = display.entityId,
            cellKeys = cellKeys,
            interactionEntityIds = spawnedInteractions.map { it.entityId },
            owner = player.uuid,
        )

        val addResult = PlacedFurnitureStore.add(furniture)
        if (addResult != PlacedFurnitureStore.AddResult.Success) {
            display.remove()
            for ((cx, cy, cz) in placedCells) instance.setBlock(cx, cy, cz, Block.AIR)
            spawnedInteractions.forEach { it.remove() }
            logger.warn { "Store rejected furniture ${definition.id}: $addResult" }
            return false
        }

        if (player.gameMode != GameMode.CREATIVE) {
            val next = if (heldItem.amount() > 1) heldItem.withAmount(heldItem.amount() - 1) else ItemStack.AIR
            player.setItemInMainHand(next)
        }

        DisplayCullController.onPlaced(furniture, display)
        FurnitureLightingController.onPlaced(instance, furniture, definition)
        instance.playSound(
            Sound.sound(Key.key("minecraft", definition.placeSound), Sound.Source.BLOCK, 1f, 1f),
            anchorX + 0.5, anchorY + 0.5, anchorZ + 0.5,
        )
        MinecraftServer.getGlobalEventHandler().call(FurniturePlacedEvent(player, furniture))
        return true
    }

    fun breakFurniture(breaker: Player?, furniture: FurnitureInstance): Boolean {
        val preEvent = FurnitureBreakPreEvent(breaker, furniture)
        MinecraftServer.getGlobalEventHandler().call(preEvent)
        if (preEvent.isCancelled) return false

        val instance = furniture.instance
        FurnitureDisplaySpawner.despawn(instance, furniture.displayEntityId)
        for (key in furniture.cellKeys) {
            val (x, y, z) = FurnitureInstance.unpackKey(key)
            instance.setBlock(x, y, z, Block.AIR)
        }
        for (entityId in furniture.interactionEntityIds) {
            InteractionEntitySpawner.despawn(instance, entityId)
        }
        SeatController.onFurnitureBroken(furniture.uuid)
        DisplayCullController.onBroken(furniture.uuid)
        FurnitureLightingController.onBroken(instance, furniture)
        FurnitureInstanceState.remove(furniture.uuid)
        PlacedFurnitureStore.remove(instance, furniture.uuid)

        val definition = FurnitureRegistry[furniture.definitionId]
        val shouldDropItem = breaker?.gameMode != GameMode.CREATIVE && definition != null
        if (shouldDropItem) {
            val customItem = CustomItemRegistry[definition.itemId]
            if (customItem != null) {
                dropItemAt(instance, furniture.anchorX, furniture.anchorY, furniture.anchorZ, customItem.createStack(1))
            }
        }

        if (definition != null) {
            instance.playSound(
                Sound.sound(Key.key("minecraft", definition.breakSound), Sound.Source.BLOCK, 1f, 1f),
                furniture.anchorX + 0.5, furniture.anchorY + 0.5, furniture.anchorZ + 0.5,
            )
        }
        MinecraftServer.getGlobalEventHandler().call(FurnitureBrokenEvent(breaker, furniture))
        return true
    }

    private fun blockForCell(definition: FurnitureDefinition, baseCell: FootprintCell): Block =
        when (val decision = definition.decisionFor(baseCell)) {
            is CellDecision.Barrier -> Block.BARRIER
            is CellDecision.Shaped -> FurnitureCollisionStates.resolve(decision.hitbox)
        }

    private fun interactWith(player: Player, furniture: FurnitureInstance) {
        val definition = FurnitureRegistry[furniture.definitionId] ?: return
        val interaction = definition.interaction ?: return
        FurnitureInteractionDispatcher.dispatch(player, furniture, definition, interaction)
    }

    private fun resolveAnchor(clickedBlock: Point, face: net.minestom.server.instance.block.BlockFace): Point {
        val dx = when (face) {
            net.minestom.server.instance.block.BlockFace.EAST -> 1
            net.minestom.server.instance.block.BlockFace.WEST -> -1
            else -> 0
        }
        val dy = when (face) {
            net.minestom.server.instance.block.BlockFace.TOP -> 1
            net.minestom.server.instance.block.BlockFace.BOTTOM -> -1
            else -> 0
        }
        val dz = when (face) {
            net.minestom.server.instance.block.BlockFace.SOUTH -> 1
            net.minestom.server.instance.block.BlockFace.NORTH -> -1
            else -> 0
        }
        return Pos(
            (clickedBlock.blockX() + dx).toDouble(),
            (clickedBlock.blockY() + dy).toDouble(),
            (clickedBlock.blockZ() + dz).toDouble(),
        )
    }

    private fun dropItemAt(instance: Instance, x: Int, y: Int, z: Int, stack: ItemStack) {
        val entity = Entity(EntityType.ITEM)
        val itemMeta = entity.entityMeta as ItemEntityMeta
        itemMeta.setNotifyAboutChanges(false)
        itemMeta.setItem(stack)
        itemMeta.setNotifyAboutChanges(true)
        entity.setInstance(instance, Pos(x + 0.5, y + 0.5, z + 0.5))
    }
}
