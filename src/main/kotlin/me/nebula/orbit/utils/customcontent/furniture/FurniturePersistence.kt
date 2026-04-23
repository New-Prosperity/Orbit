package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory
import net.minestom.server.item.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

object FurniturePersistence {

    private val logger = logger("FurniturePersistence")
    private val gson = GsonProvider.default

    const val MANIFEST_MAGIC: String = "nebula-furniture"
    const val MANIFEST_VERSION: Int = 2

    data class Manifest(
        val magic: String = MANIFEST_MAGIC,
        val version: Int = MANIFEST_VERSION,
        val pieces: List<PersistedPiece> = emptyList(),
    )

    data class PersistedPiece(
        val uuid: String,
        val definitionId: String,
        val anchorX: Int,
        val anchorY: Int,
        val anchorZ: Int,
        val yawDegrees: Float,
        val pitchDegrees: Float = 0f,
        val rollDegrees: Float = 0f,
        val openCloseOpen: Boolean = false,
        val inventoryBase64: String? = null,
        val owner: String? = null,
    )

    fun serializeInventory(inventory: Inventory): String? {
        val list = ListBinaryTag.builder()
        var any = false
        for (slot in 0 until inventory.size) {
            val stack = inventory.getItemStack(slot)
            if (stack.isAir) continue
            any = true
            list.add(CompoundBinaryTag.builder()
                .putInt("slot", slot)
                .put("item", stack.toItemNBT())
                .build())
        }
        if (!any) return null
        val root = CompoundBinaryTag.builder().put("slots", list.build()).build()
        val baos = ByteArrayOutputStream()
        BinaryTagIO.writer().write(root, baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    fun hydrateInventory(inventory: Inventory, base64: String) {
        val bytes = runCatching { Base64.getDecoder().decode(base64) }.getOrElse { e ->
            logger.warn { "Invalid furniture inventory base64: ${e.message}" }
            return
        }
        val root = runCatching { BinaryTagIO.reader().read(ByteArrayInputStream(bytes)) }.getOrElse { e ->
            logger.warn { "Failed to decode furniture inventory NBT: ${e.message}" }
            return
        }
        val slots = root.getList("slots")
        for (entry in slots) {
            val compound = entry as? CompoundBinaryTag ?: continue
            val slot = compound.getInt("slot", -1)
            if (slot < 0 || slot >= inventory.size) continue
            val itemTag = compound.getCompound("item")
            val stack = runCatching { ItemStack.fromItemNBT(itemTag) }.getOrElse {
                logger.warn { "Dropping unknown item in restored furniture inventory at slot $slot: ${it.message}" }
                continue
            }
            inventory.setItemStack(slot, stack)
        }
    }

    fun encode(instance: Instance): ByteArray {
        val manifest = buildManifest(instance)
        if (manifest.pieces.isEmpty()) return ByteArray(0)
        return gson.toJson(manifest).toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): Manifest? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val manifest = gson.fromJson(bytes.toString(Charsets.UTF_8), Manifest::class.java)
            if (manifest.magic != MANIFEST_MAGIC) {
                logger.warn { "Skipping foreign userData in nebula world (magic=${manifest.magic})" }
                return@runCatching null
            }
            if (manifest.version > MANIFEST_VERSION) {
                logger.warn { "Skipping newer-version furniture manifest (version=${manifest.version}, max=$MANIFEST_VERSION)" }
                return@runCatching null
            }
            manifest
        }.getOrElse { e ->
            logger.warn { "Failed to decode furniture manifest: ${e.message}" }
            null
        }
    }

    fun embed(world: NebulaWorld, instance: Instance): NebulaWorld {
        val bytes = encode(instance)
        return NebulaWorld(
            dataVersion = world.dataVersion,
            minSection = world.minSection,
            maxSection = world.maxSection,
            userData = bytes,
            chunks = world.chunks,
        )
    }

    fun restore(instance: Instance, world: NebulaWorld): RestoreResult {
        val manifest = decode(world.userData) ?: return RestoreResult(0, 0)
        var restored = 0
        var skipped = 0
        for (piece in manifest.pieces) {
            if (restorePiece(instance, piece)) restored++ else skipped++
        }
        logger.info { "Restored $restored furniture pieces ($skipped skipped) in instance ${System.identityHashCode(instance)}" }
        return RestoreResult(restored, skipped)
    }

    private fun restorePiece(instance: Instance, piece: PersistedPiece): Boolean {
        val definition = FurnitureRegistry[piece.definitionId] ?: run {
            logger.warn { "Skipping unknown furniture definition '${piece.definitionId}' at (${piece.anchorX}, ${piece.anchorY}, ${piece.anchorZ})" }
            return false
        }
        val uuid = runCatching { UUID.fromString(piece.uuid) }.getOrElse { e ->
            logger.warn { "Skipping piece with invalid UUID '${piece.uuid}': ${e.message}" }
            return false
        }

        val quarterTurns = FootprintRotation.yawToQuarterTurns(piece.yawDegrees)
        val display = FurnitureDisplaySpawner.spawn(
            definition, instance,
            piece.anchorX, piece.anchorY, piece.anchorZ,
            yawDegrees = piece.yawDegrees,
            pitchDegrees = piece.pitchDegrees,
            rollDegrees = piece.rollDegrees,
        )

        val interactionEntityIds: List<Int> = when (definition.collision) {
            FurnitureCollision.Solid -> emptyList()
            FurnitureCollision.NonSolid -> {
                val rotated = FootprintRotation.rotate(definition.footprint, quarterTurns)
                rotated.cells.map { cell ->
                    InteractionEntitySpawner.spawnAt(
                        instance,
                        piece.anchorX + cell.dx,
                        piece.anchorY + cell.dy,
                        piece.anchorZ + cell.dz,
                    ).entityId
                }
            }
        }

        val cellKeys = if (definition.collision == FurnitureCollision.Solid) {
            FurnitureInstance.cellKeysOf(definition, piece.anchorX, piece.anchorY, piece.anchorZ, quarterTurns)
        } else emptyList()

        val ownerUuid = piece.owner?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val furniture = FurnitureInstance(
            uuid = uuid,
            definitionId = piece.definitionId,
            instance = instance,
            anchorX = piece.anchorX,
            anchorY = piece.anchorY,
            anchorZ = piece.anchorZ,
            yawDegrees = piece.yawDegrees,
            pitchDegrees = piece.pitchDegrees,
            rollDegrees = piece.rollDegrees,
            displayEntityId = display.entityId,
            cellKeys = cellKeys,
            interactionEntityIds = interactionEntityIds,
            owner = ownerUuid,
        )

        val addResult = PlacedFurnitureStore.add(furniture)
        if (addResult != PlacedFurnitureStore.AddResult.Success) {
            display.remove()
            interactionEntityIds.forEach { InteractionEntitySpawner.despawn(instance, it) }
            logger.warn { "Failed to restore '${piece.definitionId}' at (${piece.anchorX}, ${piece.anchorY}, ${piece.anchorZ}): $addResult" }
            return false
        }
        if (piece.openCloseOpen) FurnitureInstanceState.setOpen(uuid, true)
        if (piece.inventoryBase64 != null) FurnitureInstanceState.setPendingInventory(uuid, piece.inventoryBase64)
        DisplayCullController.onPlaced(furniture, display)
        return true
    }

    private fun buildManifest(instance: Instance): Manifest {
        val pieces = PlacedFurnitureStore.all(instance).map { furniture ->
            val inventoryBase64 = FurnitureInstanceState.inventoryOf(furniture.uuid)?.let(::serializeInventory)
            PersistedPiece(
                uuid = furniture.uuid.toString(),
                definitionId = furniture.definitionId,
                anchorX = furniture.anchorX,
                anchorY = furniture.anchorY,
                anchorZ = furniture.anchorZ,
                yawDegrees = furniture.yawDegrees,
                pitchDegrees = furniture.pitchDegrees,
                rollDegrees = furniture.rollDegrees,
                openCloseOpen = FurnitureInstanceState.isOpen(furniture.uuid),
                inventoryBase64 = inventoryBase64,
                owner = furniture.owner?.toString(),
            )
        }
        return Manifest(pieces = pieces)
    }

    data class RestoreResult(val restored: Int, val skipped: Int)
}
