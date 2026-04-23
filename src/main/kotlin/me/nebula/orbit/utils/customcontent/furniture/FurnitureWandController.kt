package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.scheduler.repeat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerStartSneakingEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FurnitureWandController {

    private val logger = logger("FurnitureWandController")
    val WAND_TAG: Tag<Boolean> = Tag.Boolean("nebula:furniture_wand")

    private const val STEP_DEGREES = 1f
    private const val MOVE_RAY_MAX_DISTANCE = 16.0
    private const val MOVE_RAY_STEP = 0.2

    enum class Mode { STEPPED, MOVE }
    enum class Axis { YAW, PITCH, ROLL }

    private data class State(
        val furnitureUuid: UUID,
        val instanceRef: Instance,
        val entityId: Int,
        val originalAnchorX: Int,
        val originalAnchorY: Int,
        val originalAnchorZ: Int,
        val originalYaw: Float,
        val originalPitch: Float,
        val originalRoll: Float,
        var yaw: Float,
        var pitch: Float,
        var roll: Float,
        var mode: Mode = Mode.STEPPED,
        var axis: Axis = Axis.YAW,
        var targetAnchorX: Int = 0,
        var targetAnchorY: Int = 0,
        var targetAnchorZ: Int = 0,
        var targetFace: BlockFace = BlockFace.TOP,
        var targetValid: Boolean = false,
    )

    private val states = ConcurrentHashMap<UUID, State>()
    private var tickTask: Task? = null
    private var eventNode: EventNode<Event>? = null

    fun install() {
        if (eventNode != null) return
        val node = EventNode.all("furniture-wand")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (!isWandHeld(event.player)) return@addListener
            event.isCancelled = true
            event.setBlockingItemUse(true)
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val clicked = PlacedFurnitureStore.atCell(instance, pos.blockX(), pos.blockY(), pos.blockZ())
                ?: return@addListener
            val existing = states[event.player.uuid]
            if (existing != null && existing.furnitureUuid == clicked.uuid) return@addListener
            if (existing != null) {
                revertOnly(existing)
                states.remove(event.player.uuid)
            }
            grab(event.player, clicked)
        }

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            if (!isWandHeld(event.player)) return@addListener
            val state = states[event.player.uuid] ?: return@addListener
            event.isCancelled = true
            commit(event.player, state)
        }

        node.addListener(PlayerChangeHeldSlotEvent::class.java) { event ->
            val state = states[event.player.uuid] ?: return@addListener
            if (!isWandHeld(event.player)) return@addListener
            event.isCancelled = true
            val delta = scrollDelta(event.oldSlot.toInt(), event.newSlot.toInt())
            applyScroll(event.player, state, delta)
        }

        node.addListener(PlayerStartSneakingEvent::class.java) { event ->
            val state = states[event.player.uuid] ?: return@addListener
            if (state.mode == Mode.STEPPED) {
                state.axis = when (state.axis) {
                    Axis.YAW -> Axis.PITCH
                    Axis.PITCH -> Axis.ROLL
                    Axis.ROLL -> Axis.YAW
                }
                event.player.sendActionBar(buildHud(state))
            }
        }

        node.addListener(PlayerSwapItemEvent::class.java) { event ->
            val state = states[event.player.uuid] ?: return@addListener
            event.isCancelled = true
            toggleMode(event.player, state)
        }

        node.addListener(ItemDropEvent::class.java) { event ->
            val state = states[event.player.uuid] ?: return@addListener
            event.isCancelled = true
            cancel(event.player, state)
        }

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val state = states[event.player.uuid] ?: return@addListener
            if (state.mode != Mode.MOVE) return@addListener
            recomputeMoveTarget(event.player, state)
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            val state = states.remove(event.player.uuid) ?: return@addListener
            revertOnly(state)
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
        tickTask = repeat(1) { tick() }
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        states.values.forEach { revertOnly(it) }
        states.clear()
    }

    fun isWandHeld(player: Player): Boolean = player.itemInMainHand.getTag(WAND_TAG) == true

    fun isGrabbed(player: Player): Boolean = states.containsKey(player.uuid)

    fun createWandItem(): ItemStack = ItemStack.builder(Material.BLAZE_ROD)
        .set(WAND_TAG, true)
        .customName(Component.text("Furniture Wand", NamedTextColor.LIGHT_PURPLE))
        .lore(
            Component.text("Right-click furniture to grab.", NamedTextColor.GRAY),
            Component.text("STEP mode: scroll = ±1°, sneak = cycle axis.", NamedTextColor.GRAY),
            Component.text("MOVE mode: aim to reposition, scroll = yaw.", NamedTextColor.GRAY),
            Component.text("Swap hand = toggle STEP/MOVE.", NamedTextColor.GRAY),
            Component.text("Right-click air = commit.", NamedTextColor.GREEN),
            Component.text("Drop = cancel.", NamedTextColor.RED),
        )
        .build()

    private fun grab(player: Player, furniture: FurnitureInstance) {
        val instance = furniture.instance
        val state = State(
            furnitureUuid = furniture.uuid,
            instanceRef = instance,
            entityId = furniture.displayEntityId,
            originalAnchorX = furniture.anchorX,
            originalAnchorY = furniture.anchorY,
            originalAnchorZ = furniture.anchorZ,
            originalYaw = furniture.yawDegrees,
            originalPitch = furniture.pitchDegrees,
            originalRoll = furniture.rollDegrees,
            yaw = furniture.yawDegrees,
            pitch = furniture.pitchDegrees,
            roll = furniture.rollDegrees,
            targetAnchorX = furniture.anchorX,
            targetAnchorY = furniture.anchorY,
            targetAnchorZ = furniture.anchorZ,
        )
        states[player.uuid] = state
        player.sendActionBar(buildHud(state))
    }

    private fun commit(player: Player, state: State) {
        val definition = FurnitureRegistry[getPieceDefinitionId(state) ?: return] ?: return
        val current = PlacedFurnitureStore.byUuid(state.instanceRef, state.furnitureUuid) ?: run {
            states.remove(player.uuid)
            player.sendActionBar(Component.text("Furniture gone — commit aborted.", NamedTextColor.RED))
            return
        }

        if (state.mode == Mode.MOVE) {
            if (!state.targetValid) {
                player.sendActionBar(Component.text(
                    "No valid placement target — aim at a surface first.",
                    NamedTextColor.RED,
                ))
                return
            }
            if (!definition.placement.allows(state.targetFace)) {
                player.sendActionBar(Component.text(
                    "${definition.id} can't be placed on the ${state.targetFace.name.lowercase()} face.",
                    NamedTextColor.RED,
                ))
                return
            }
            if (!relocate(state, current, definition, player)) return
        } else {
            PlacedFurnitureStore.updateTransform(state.instanceRef, state.furnitureUuid, current.copy(
                yawDegrees = state.yaw,
                pitchDegrees = state.pitch,
                rollDegrees = state.roll,
            ))
            applyRotation(state)
        }

        states.remove(player.uuid)
        player.sendActionBar(Component.text(
            "Committed: pos=(${state.targetAnchorX},${state.targetAnchorY},${state.targetAnchorZ}) " +
                "yaw=${format(state.yaw)} pitch=${format(state.pitch)} roll=${format(state.roll)}",
            NamedTextColor.GREEN,
        ))
    }

    private fun relocate(state: State, current: FurnitureInstance, definition: FurnitureDefinition, player: Player): Boolean {
        val facingYawRaw = (player.position.yaw() + 180f) % 360f
        val quarterTurns = FootprintRotation.yawToQuarterTurns(facingYawRaw)
        val rotatedFootprint = FootprintRotation.rotate(definition.footprint, quarterTurns)
        val instance = state.instanceRef
        val newCells = rotatedFootprint.cells.map {
            Triple(state.targetAnchorX + it.dx, state.targetAnchorY + it.dy, state.targetAnchorZ + it.dz)
        }

        val oldCellSet = current.cellKeys.map { FurnitureInstance.unpackKey(it) }.toSet()
        for ((cx, cy, cz) in newCells) {
            if (Triple(cx, cy, cz) in oldCellSet) continue
            val existing = instance.getBlock(cx, cy, cz)
            if (!existing.isAir) {
                player.sendActionBar(Component.text(
                    "Target blocked — ${existing.name()} at ($cx, $cy, $cz).",
                    NamedTextColor.RED,
                ))
                return false
            }
            if (PlacedFurnitureStore.atCell(instance, cx, cy, cz) != null) {
                player.sendActionBar(Component.text(
                    "Furniture already at ($cx, $cy, $cz).",
                    NamedTextColor.RED,
                ))
                return false
            }
        }

        for (key in current.cellKeys) {
            val (x, y, z) = FurnitureInstance.unpackKey(key)
            instance.setBlock(x, y, z, Block.AIR)
        }
        for ((i, position) in newCells.withIndex()) {
            val baseCell = definition.footprint.cells[i]
            val block = blockForCell(definition, baseCell)
            instance.setBlock(position.first, position.second, position.third, block)
        }

        val newCellKeys = if (definition.collision == FurnitureCollision.Solid) {
            FurnitureInstance.cellKeysOf(definition, state.targetAnchorX, state.targetAnchorY, state.targetAnchorZ, quarterTurns)
        } else emptyList()

        val updated = current.copy(
            anchorX = state.targetAnchorX,
            anchorY = state.targetAnchorY,
            anchorZ = state.targetAnchorZ,
            yawDegrees = state.yaw,
            pitchDegrees = state.pitch,
            rollDegrees = state.roll,
            cellKeys = newCellKeys,
        )
        PlacedFurnitureStore.updateTransform(instance, state.furnitureUuid, updated)
        applyRotation(state)
        return true
    }

    private fun cancel(player: Player, state: State) {
        revertOnly(state)
        states.remove(player.uuid)
        player.sendActionBar(Component.text("Reverted.", NamedTextColor.YELLOW))
    }

    private fun revertOnly(state: State) {
        FurnitureDisplaySpawner.setRotation(
            state.instanceRef, state.entityId,
            state.originalYaw, state.originalPitch, state.originalRoll,
        )
        FurnitureDisplaySpawner.moveAnchor(
            state.instanceRef, state.entityId,
            state.originalAnchorX, state.originalAnchorY, state.originalAnchorZ,
        )
    }

    private fun toggleMode(player: Player, state: State) {
        state.mode = if (state.mode == Mode.STEPPED) Mode.MOVE else Mode.STEPPED
        if (state.mode == Mode.MOVE) {
            recomputeMoveTarget(player, state)
        } else {
            state.targetAnchorX = state.originalAnchorX
            state.targetAnchorY = state.originalAnchorY
            state.targetAnchorZ = state.originalAnchorZ
            state.targetValid = true
            FurnitureDisplaySpawner.moveAnchor(
                state.instanceRef, state.entityId,
                state.targetAnchorX, state.targetAnchorY, state.targetAnchorZ,
            )
        }
        player.sendActionBar(buildHud(state))
    }

    private fun applyScroll(player: Player, state: State, delta: Int) {
        if (delta == 0) return
        val step = STEP_DEGREES * delta
        when {
            state.mode == Mode.MOVE -> {
                state.yaw = wrap(state.yaw + step)
                recomputeMoveTarget(player, state)
            }
            state.axis == Axis.YAW -> state.yaw = wrap(state.yaw + step)
            state.axis == Axis.PITCH -> state.pitch = wrap(state.pitch + step)
            state.axis == Axis.ROLL -> state.roll = wrap(state.roll + step)
        }
        applyRotation(state)
        player.sendActionBar(buildHud(state))
    }

    private fun recomputeMoveTarget(player: Player, state: State) {
        val def = FurnitureRegistry[getPieceDefinitionId(state) ?: return] ?: return
        val instance = state.instanceRef
        val hit = raycastBlockFace(player, instance)
        if (hit == null) {
            state.targetValid = false
            player.sendActionBar(buildHud(state))
            return
        }
        val (hitBlockX, hitBlockY, hitBlockZ, face) = hit
        val (dx, dy, dz) = faceNormal(face)
        val anchorX = hitBlockX + dx
        val anchorY = hitBlockY + dy
        val anchorZ = hitBlockZ + dz

        state.targetAnchorX = anchorX
        state.targetAnchorY = anchorY
        state.targetAnchorZ = anchorZ
        state.targetFace = face
        state.targetValid = def.placement.allows(face)

        val euler = FurniturePlacementRotation.eulerFor(face, state.yaw, def.placement.autoOrient)
        state.yaw = euler.yawDegrees
        state.pitch = euler.pitchDegrees
        state.roll = euler.rollDegrees

        FurnitureDisplaySpawner.moveAnchor(instance, state.entityId, anchorX, anchorY, anchorZ)
        applyRotation(state)
        player.sendActionBar(buildHud(state))
    }

    private fun applyRotation(state: State) {
        FurnitureDisplaySpawner.setRotation(
            state.instanceRef, state.entityId,
            state.yaw, state.pitch, state.roll,
        )
    }

    private fun tick() {
        for ((uuid, state) in states) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
            if (state.mode == Mode.MOVE) recomputeMoveTarget(player, state)
            else player.sendActionBar(buildHud(state))
        }
    }

    private fun buildHud(state: State): Component {
        val modeLabel = if (state.mode == Mode.STEPPED) "STEP" else "MOVE"
        val modeColor = if (state.mode == Mode.STEPPED) NamedTextColor.AQUA else NamedTextColor.GOLD
        val detail = when (state.mode) {
            Mode.STEPPED -> "axis=${state.axis.name}"
            Mode.MOVE -> if (state.targetValid) "face=${state.targetFace.name.lowercase()} @ (${state.targetAnchorX},${state.targetAnchorY},${state.targetAnchorZ})"
                else "aim at a surface"
        }
        val detailColor = if (state.mode == Mode.MOVE && !state.targetValid) NamedTextColor.RED else NamedTextColor.GRAY
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text(modeLabel, modeColor))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(detail, detailColor))
            .append(Component.text(" | yaw=${format(state.yaw)} pitch=${format(state.pitch)} roll=${format(state.roll)}", NamedTextColor.WHITE))
    }

    private fun format(v: Float): String = "%.0f°".format(v)

    private fun scrollDelta(oldSlot: Int, newSlot: Int): Int {
        val raw = newSlot - oldSlot
        return when {
            raw == 0 -> 0
            raw == 1 || raw == -8 -> 1
            raw == -1 || raw == 8 -> -1
            else -> if (raw > 0) 1 else -1
        }
    }

    private fun wrap(v: Float): Float {
        var result = v % 360f
        if (result > 180f) result -= 360f
        if (result < -180f) result += 360f
        return result
    }

    private data class RayHit(val x: Int, val y: Int, val z: Int, val face: BlockFace)

    private fun raycastBlockFace(player: Player, instance: Instance): RayHit? {
        val origin = player.position.add(0.0, player.eyeHeight, 0.0)
        val dir = player.position.direction()
        var t = 0.0
        var lastX = origin.x().toInt()
        var lastY = origin.y().toInt()
        var lastZ = origin.z().toInt()
        while (t <= MOVE_RAY_MAX_DISTANCE) {
            val x = (origin.x() + dir.x() * t).toInt()
            val y = (origin.y() + dir.y() * t).toInt()
            val z = (origin.z() + dir.z() * t).toInt()
            val block = instance.getBlock(x, y, z)
            if (!block.isAir) {
                val face = approximateFace(lastX, lastY, lastZ, x, y, z)
                return RayHit(x, y, z, face)
            }
            lastX = x; lastY = y; lastZ = z
            t += MOVE_RAY_STEP
        }
        return null
    }

    private fun approximateFace(prevX: Int, prevY: Int, prevZ: Int, hitX: Int, hitY: Int, hitZ: Int): BlockFace {
        val dx = prevX - hitX
        val dy = prevY - hitY
        val dz = prevZ - hitZ
        return when {
            dy > 0 -> BlockFace.TOP
            dy < 0 -> BlockFace.BOTTOM
            dx > 0 -> BlockFace.EAST
            dx < 0 -> BlockFace.WEST
            dz > 0 -> BlockFace.SOUTH
            dz < 0 -> BlockFace.NORTH
            else -> BlockFace.TOP
        }
    }

    private fun faceNormal(face: BlockFace): Triple<Int, Int, Int> = when (face) {
        BlockFace.TOP -> Triple(0, 1, 0)
        BlockFace.BOTTOM -> Triple(0, -1, 0)
        BlockFace.NORTH -> Triple(0, 0, -1)
        BlockFace.SOUTH -> Triple(0, 0, 1)
        BlockFace.EAST -> Triple(1, 0, 0)
        BlockFace.WEST -> Triple(-1, 0, 0)
    }

    private fun getPieceDefinitionId(state: State): String? =
        PlacedFurnitureStore.byUuid(state.instanceRef, state.furnitureUuid)?.definitionId

    private fun blockForCell(definition: FurnitureDefinition, baseCell: FootprintCell): Block =
        when (val decision = definition.decisionFor(baseCell)) {
            is CellDecision.Barrier -> Block.BARRIER
            is CellDecision.Shaped -> FurnitureCollisionStates.resolve(decision.hitbox)
        }
}
