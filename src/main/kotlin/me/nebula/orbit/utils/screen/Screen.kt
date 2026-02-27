package me.nebula.orbit.utils.screen

import me.nebula.orbit.utils.screen.animation.AnimationController
import me.nebula.orbit.utils.screen.canvas.MapCanvas
import me.nebula.orbit.utils.screen.display.MapDisplay
import me.nebula.orbit.utils.screen.encoder.MapEncoder
import me.nebula.orbit.utils.screen.widget.Widget
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.animal.CamelMeta
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.client.play.ClientAnimationPacket
import net.minestom.server.network.packet.client.play.ClientInputPacket
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket
import net.minestom.server.network.packet.client.play.ClientPlayerPositionStatusPacket
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket
import net.minestom.server.network.packet.client.play.ClientVehicleMovePacket
import net.minestom.server.network.packet.server.play.CameraPacket
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val nextCursorEntityId = AtomicInteger(-5_000_000)
private val nextViewpointEntityId = AtomicInteger(-7_000_000)

private const val META_INTERPOLATION_DELAY = 8
private const val META_TRANSFORM_DURATION = 9
private const val META_TRANSLATION = 11
private const val META_SCALE = 12
private const val META_ROTATION_LEFT = 13
private const val META_BILLBOARD = 15
private const val META_VIEW_RANGE = 17
private const val META_DISPLAYED_ITEM = 23
private const val META_DISPLAY_TYPE = 24

private const val ANCHOR_Y_OFFSET = 10.0
private const val CAMERA_DELAY_TICKS = 1
private const val REMOUNT_CHECK_TICKS = 2

data class ScreenCursor(
    val entityId: Int,
    val entityUuid: UUID,
    val item: ItemStack,
    val scale: Float,
    val interpolationTicks: Int,
)

data class ScreenButton(
    val id: String,
    val pixelX: Int,
    val pixelY: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val onClick: (() -> Unit)?,
    val onHover: ((Boolean) -> Unit)?,
    @Volatile var hovered: Boolean = false,
)

data class ScreenSession(
    val playerUuid: UUID,
    val basis: ScreenBasis,
    val config: ScreenConfig,
    val viewpointEntityId: Int,
    val anchorEntity: EntityCreature,
    val cursor: ScreenCursor,
    val buttons: List<ScreenButton>,
    val canvas: MapCanvas,
    val display: MapDisplay,
    val previousPosition: Pos,
    val sensitivity: Double,
    val closeOnSneak: Boolean,
    val onClose: (() -> Unit)?,
    val animations: AnimationController,
    val widgets: List<Widget>,
    val cursorHotspotOffset: Vec,
    @Volatile var cursorWorldX: Double = 0.0,
    @Volatile var cursorWorldY: Double = 0.0,
    @Volatile var lastYaw: Float = 0f,
    @Volatile var lastPitch: Float = 0f,
    @Volatile var calibrated: Boolean = false,
    @Volatile var cameraApplied: Boolean = false,
    @Volatile var hoveredWidget: Widget? = null,
)

object Screen {

    private val sessions = ConcurrentHashMap<UUID, ScreenSession>()

    private val globalNode = EventNode.all("screen-global").apply {
        addListener(PlayerPacketEvent::class.java) { event ->
            val session = sessions[event.player.uuid] ?: return@addListener
            handlePacket(event, session, event.player)
        }
        addListener(PlayerDisconnectEvent::class.java) { event ->
            cleanup(event.player)
        }
    }

    init {
        MinecraftServer.getGlobalEventHandler().addChild(globalNode)
        MinecraftServer.getSchedulerManager()
            .buildTask {
                for ((uuid, session) in sessions) {
                    val player = MinecraftServer.getConnectionManager()
                        .onlinePlayers.firstOrNull { it.uuid == uuid } ?: continue
                    if (player.vehicle?.entityId != session.anchorEntity.entityId) {
                        session.anchorEntity.addPassenger(player)
                        if (session.cameraApplied) {
                            player.sendPacket(CameraPacket(session.viewpointEntityId))
                        }
                    }
                }
            }
            .repeat(TaskSchedule.tick(REMOUNT_CHECK_TICKS))
            .schedule()
    }

    fun open(player: Player, config: ScreenConfig, builder: ScreenBuilder) {
        close(player)

        val basis = computeScreenBasis(config)
        val previousPosition = player.position
        val instance = player.instance ?: return

        val canvas = MapCanvas(config.canvasWidth, config.canvasHeight)
        val display = MapDisplay(config.tilesX, config.tilesY)

        canvas.clear(builder.backgroundColor)
        builder.backgroundImage?.let { img ->
            canvas.drawImage(0, 0, img, builder.backgroundImageWidth, builder.backgroundImageHeight)
        }
        for (widget in builder.widgets) widget.draw(canvas)
        builder.onDraw?.invoke(canvas)

        val cursor = ScreenCursor(
            entityId = nextCursorEntityId.getAndDecrement(),
            entityUuid = UUID.randomUUID(),
            item = builder.cursorBuilder.item,
            scale = builder.cursorBuilder.scale,
            interpolationTicks = builder.cursorBuilder.interpolationTicks,
        )

        val buttons = builder.buttons.map { b ->
            ScreenButton(
                id = b.id,
                pixelX = b.pixelX,
                pixelY = b.pixelY,
                pixelWidth = b.pixelWidth,
                pixelHeight = b.pixelHeight,
                onClick = b.onClick,
                onHover = b.onHover,
            )
        }

        val viewpointEntityId = spawnViewpoint(player, config)

        val anchor = createAnchor(instance, config)
        anchor.addViewer(player)

        player.teleport(config.feetPos)
        anchor.addPassenger(player)

        display.spawn(player, basis)

        val chunks = MapEncoder.encodeCanvas(canvas)
        val batchSize = 20
        val batches = chunks.chunked(batchSize)
        batches.forEachIndexed { index, batch ->
            if (index == 0) {
                display.update(player, batch)
            } else {
                MinecraftServer.getSchedulerManager()
                    .buildTask { display.update(player, batch) }
                    .delay(TaskSchedule.tick(index))
                    .schedule()
            }
        }

        val hotspotOffset = basis.right.mul(cursor.scale * 0.5)
            .add(basis.up.mul(-cursor.scale * 0.5))

        spawnCursorDisplay(player, cursor, basis, hotspotOffset)

        val session = ScreenSession(
            playerUuid = player.uuid,
            basis = basis,
            config = config,
            viewpointEntityId = viewpointEntityId,
            anchorEntity = anchor,
            cursor = cursor,
            buttons = buttons,
            canvas = canvas,
            display = display,
            previousPosition = previousPosition,
            sensitivity = builder.sensitivity,
            closeOnSneak = builder.closeOnSneak,
            onClose = builder.onClose,
            animations = AnimationController(),
            widgets = builder.widgets.toList(),
            cursorHotspotOffset = hotspotOffset,
        )

        sessions[player.uuid] = session

        MinecraftServer.getSchedulerManager()
            .buildTask {
                val s = sessions[player.uuid] ?: return@buildTask
                player.sendPacket(CameraPacket(viewpointEntityId))
                s.cameraApplied = true
            }
            .delay(TaskSchedule.tick(CAMERA_DELAY_TICKS))
            .schedule()
    }

    fun close(player: Player) {
        val session = sessions.remove(player.uuid) ?: return
        session.animations.clear()

        session.anchorEntity.removePassenger(player)
        player.sendPacket(CameraPacket(player.entityId))

        session.display.destroy(player)
        session.anchorEntity.remove()
        player.sendPacket(DestroyEntitiesPacket(listOf(session.viewpointEntityId, session.cursor.entityId)))

        MinecraftServer.getSchedulerManager()
            .buildTask { player.teleport(session.previousPosition) }
            .delay(TaskSchedule.tick(1))
            .schedule()
        session.onClose?.invoke()
    }

    fun update(player: Player, draw: (MapCanvas) -> Unit) {
        val session = sessions[player.uuid] ?: return
        session.canvas.clearDirty()
        session.animations.tick()
        for (widget in session.widgets) widget.draw(session.canvas)
        draw(session.canvas)
        val chunks = MapEncoder.encodeCanvas(session.canvas, dirtyOnly = true)
        session.display.update(player, chunks)
    }

    fun animations(player: Player): AnimationController? =
        sessions[player.uuid]?.animations

    fun isOpen(player: Player): Boolean = sessions.containsKey(player.uuid)

    fun closeAll() {
        val iter = sessions.entries.iterator()
        while (iter.hasNext()) {
            val (uuid, session) = iter.next()
            iter.remove()
            session.anchorEntity.remove()
            val player = MinecraftServer.getConnectionManager()
                .onlinePlayers.firstOrNull { it.uuid == uuid } ?: continue
            player.sendPacket(CameraPacket(player.entityId))
            session.display.destroy(player)
            player.sendPacket(DestroyEntitiesPacket(listOf(session.viewpointEntityId, session.cursor.entityId)))
            player.teleport(session.previousPosition)
        }
    }

    private fun spawnViewpoint(player: Player, config: ScreenConfig): Int {
        val id = nextViewpointEntityId.getAndDecrement()
        val uuid = UUID.randomUUID()
        player.sendPacket(SpawnEntityPacket(id, uuid, EntityType.VILLAGER, config.feetPos, 0f, 0, Vec.ZERO))
        player.sendPacket(EntityMetaDataPacket(id, mapOf(
            0 to Metadata.Byte(0x20.toByte()),
        )))
        return id
    }

    private fun createAnchor(instance: Instance, config: ScreenConfig): EntityCreature {
        val anchorPos = Pos(
            config.eyePos.x(),
            config.eyePos.y() + ANCHOR_Y_OFFSET,
            config.eyePos.z(),
            config.eyePos.yaw(),
            0f,
        )
        val anchor = EntityCreature(EntityType.CAMEL)
        anchor.setNoGravity(true)
        anchor.setInvisible(true)
        anchor.isAutoViewable = false
        anchor.editEntityMeta(CamelMeta::class.java) { meta ->
            meta.setBaby(false)
            meta.setTamed(true)
            val flags = meta.get(MetadataDef.AbstractHorse.ABSTRACT_HORSE_FLAGS).toInt() or 0x04
            meta.set(MetadataDef.AbstractHorse.ABSTRACT_HORSE_FLAGS, flags.toByte())
        }
        anchor.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.0
        anchor.setInstance(instance, anchorPos).join()
        return anchor
    }

    private fun cleanup(player: Player) {
        val session = sessions.remove(player.uuid) ?: return
        session.anchorEntity.remove()
    }

    private fun handlePacket(event: PlayerPacketEvent, session: ScreenSession, player: Player) {
        when (val packet = event.packet) {
            is ClientVehicleMovePacket -> {
                event.setCancelled(true)
                handleRotation(session, player, packet.position().yaw(), packet.position().pitch())
            }
            is ClientPlayerRotationPacket -> {
                event.setCancelled(true)
                handleRotation(session, player, packet.yaw(), packet.pitch())
            }
            is ClientPlayerPositionAndRotationPacket -> {
                event.setCancelled(true)
                handleRotation(session, player, packet.position().yaw(), packet.position().pitch())
            }
            is ClientPlayerPositionPacket -> event.setCancelled(true)
            is ClientPlayerPositionStatusPacket -> event.setCancelled(true)
            is ClientAnimationPacket -> {
                event.setCancelled(true)
                handleClick(session)
            }
            is ClientInputPacket -> {
                event.setCancelled(true)
                if (packet.shift() && session.closeOnSneak) {
                    close(player)
                }
            }
        }
    }

    private fun handleRotation(session: ScreenSession, player: Player, newYaw: Float, newPitch: Float) {
        if (!session.calibrated) {
            session.lastYaw = newYaw
            session.lastPitch = newPitch
            session.calibrated = true
            return
        }

        val dYaw = wrapDegrees((newYaw - session.lastYaw).toDouble())
        val dPitch = (newPitch - session.lastPitch).toDouble()
        session.lastYaw = newYaw
        session.lastPitch = newPitch

        val guiHalfW = session.basis.guiWidth / 2.0
        val guiHalfH = session.basis.guiHeight / 2.0

        val newWorldX = (session.cursorWorldX + dYaw / session.basis.horizontalDegree * session.sensitivity)
            .coerceIn(-guiHalfW, guiHalfW)
        val newWorldY = (session.cursorWorldY - dPitch / session.basis.verticalDegree * session.sensitivity)
            .coerceIn(-guiHalfH, guiHalfH)

        session.cursorWorldX = newWorldX
        session.cursorWorldY = newWorldY

        val offset = session.basis.right.mul(newWorldX)
            .add(session.basis.up.mul(newWorldY))
            .add(session.cursorHotspotOffset)

        player.sendPacket(EntityMetaDataPacket(session.cursor.entityId, mapOf(
            META_INTERPOLATION_DELAY to Metadata.VarInt(0),
            META_TRANSFORM_DURATION to Metadata.VarInt(session.cursor.interpolationTicks),
            META_TRANSLATION to Metadata.Vector3(offset),
        )))

        val pixelX = (newWorldX + guiHalfW) / session.basis.pixelToWorldRatio
        val pixelY = (guiHalfH - newWorldY) / session.basis.pixelToWorldRatio
        hitTest(session, player, pixelX, pixelY)
    }

    private fun handleClick(session: ScreenSession) {
        session.hoveredWidget?.onClick()
        for (button in session.buttons) {
            if (button.hovered) {
                button.onClick?.invoke()
                return
            }
        }
    }

    private fun hitTest(session: ScreenSession, player: Player, pixelX: Double, pixelY: Double) {
        for (button in session.buttons) {
            val halfW = button.pixelWidth / 2.0
            val halfH = button.pixelHeight / 2.0
            val inside = pixelX >= button.pixelX - halfW
                    && pixelX <= button.pixelX + halfW
                    && pixelY >= button.pixelY - halfH
                    && pixelY <= button.pixelY + halfH

            if (inside && !button.hovered) {
                button.hovered = true
                button.onHover?.invoke(true)
            } else if (!inside && button.hovered) {
                button.hovered = false
                button.onHover?.invoke(false)
            }
        }

        val px = pixelX.toInt()
        val py = pixelY.toInt()
        var hit: Widget? = null
        for (widget in session.widgets) {
            hit = widget.hitTest(px, py) ?: continue
            break
        }
        val prev = session.hoveredWidget
        if (hit !== prev) {
            prev?.onHover(false)
            hit?.onHover(true)
            session.hoveredWidget = hit
        }
    }

    private fun spawnCursorDisplay(player: Player, cursor: ScreenCursor, basis: ScreenBasis, hotspotOffset: Vec) {
        val center = basis.center
        player.sendPacket(SpawnEntityPacket(
            cursor.entityId, cursor.entityUuid, EntityType.ITEM_DISPLAY,
            Pos(center.x(), center.y(), center.z()), 0f, 0, Vec.ZERO,
        ))
        player.sendPacket(EntityMetaDataPacket(cursor.entityId, buildMap {
            put(META_INTERPOLATION_DELAY, Metadata.VarInt(0))
            put(META_TRANSFORM_DURATION, Metadata.VarInt(cursor.interpolationTicks))
            put(META_TRANSLATION, Metadata.Vector3(hotspotOffset))
            put(META_SCALE, Metadata.Vector3(Vec(cursor.scale.toDouble(), cursor.scale.toDouble(), cursor.scale.toDouble())))
            put(META_ROTATION_LEFT, Metadata.Quaternion(basis.facingRotation))
            put(META_BILLBOARD, Metadata.Byte(AbstractDisplayMeta.BillboardConstraints.FIXED.ordinal.toByte()))
            put(META_VIEW_RANGE, Metadata.Float(1.0f))
            put(META_DISPLAYED_ITEM, Metadata.ItemStack(cursor.item))
            put(META_DISPLAY_TYPE, Metadata.Byte(0))
        }))
    }
}

inline fun screen(player: Player, eyePos: Pos, block: ScreenBuilder.() -> Unit) {
    val builder = ScreenBuilder().apply(block)
    val config = ScreenConfig(eyePos = eyePos)
    Screen.open(player, config, builder)
}

inline fun screen(player: Player, config: ScreenConfig, block: ScreenBuilder.() -> Unit) {
    Screen.open(player, config, ScreenBuilder().apply(block))
}

fun Player.openScreen(eyePos: Pos, block: ScreenBuilder.() -> Unit) = screen(this, eyePos, block)
fun Player.closeScreen() = Screen.close(this)
val Player.hasScreenOpen: Boolean get() = Screen.isOpen(this)
