package me.nebula.orbit.utils.npc

import me.nebula.orbit.utils.modelengine.model.ModeledEntityBuilder
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val miniMessage = MiniMessage.miniMessage()
private val nextEntityId = AtomicInteger(-2_000_000)

private const val META_NO_GRAVITY = 5
private const val META_SKIN_PARTS = 17
private const val META_DISPLAY_SCALE = 12
private const val META_DISPLAY_BILLBOARD = 15
private const val META_DISPLAY_TEXT = 23
private const val META_DISPLAY_BACKGROUND = 25
private const val META_DISPLAY_OPACITY = 26
private const val META_DISPLAY_FLAGS = 27

sealed interface NpcVisual {
    data class SkinVisual(val skin: PlayerSkin?) : NpcVisual
    data class EntityVisual(
        val type: EntityType,
        val metadata: Map<Int, Metadata.Entry<*>> = emptyMap(),
    ) : NpcVisual
    data object ModelVisual : NpcVisual
}

class Npc internal constructor(
    val entityId: Int,
    val uuid: UUID,
    val name: Component,
    val nameRaw: String,
    val position: Pos,
    val visual: NpcVisual,
    private val onClick: ((Player) -> Unit)?,
    private val equipment: Map<EquipmentSlot, ItemStack>,
    private val nameDisplayEntityId: Int,
    private val nameDisplayUuid: UUID,
    private val lookAtPlayer: Boolean,
    private val nameOffset: Double,
) {
    internal var standaloneModel: StandaloneModelOwner? = null

    private val _viewers = ConcurrentHashMap.newKeySet<UUID>()
    val viewers: Set<UUID> get() = _viewers.toSet()

    fun show(player: Player) {
        if (!_viewers.add(player.uuid)) return

        when (visual) {
            is NpcVisual.SkinVisual -> showSkin(player, visual)
            is NpcVisual.EntityVisual -> showEntity(player, visual)
            is NpcVisual.ModelVisual -> showModelHitbox(player)
        }

        showNameDisplay(player)
        standaloneModel?.show(player)
    }

    fun hide(player: Player) {
        if (!_viewers.remove(player.uuid)) return
        standaloneModel?.hide(player)
        player.sendPacket(DestroyEntitiesPacket(listOf(entityId, nameDisplayEntityId)))
        if (visual is NpcVisual.SkinVisual) {
            player.sendPacket(PlayerInfoRemovePacket(listOf(uuid)))
        }
    }

    fun remove() {
        standaloneModel?.remove()
        standaloneModel = null
        val destroyPacket = DestroyEntitiesPacket(listOf(entityId, nameDisplayEntityId))
        val needsInfoRemove = visual is NpcVisual.SkinVisual
        forEachViewer { player ->
            player.sendPacket(destroyPacket)
            if (needsInfoRemove) player.sendPacket(PlayerInfoRemovePacket(listOf(uuid)))
        }
        _viewers.clear()
        NpcRegistry.unregister(this)
    }

    fun handleInteract(player: Player) {
        if (!_viewers.contains(player.uuid)) return
        onClick?.invoke(player)
    }

    fun lookAt(player: Player, target: Pos) {
        if (!_viewers.contains(player.uuid)) return
        if (visual is NpcVisual.ModelVisual) return
        val dx = target.x() - position.x()
        val dz = target.z() - position.z()
        val yaw = (-Math.toDegrees(Math.atan2(dx, dz))).toFloat()
        player.sendPacket(EntityHeadLookPacket(entityId, yaw))
    }

    internal fun tickLookAt() {
        if (!lookAtPlayer) return
        val hasEntity = visual !is NpcVisual.ModelVisual
        var lastYaw = 0f
        var lastPitch = 0f
        forEachViewer { player ->
            val dx = player.position.x() - position.x()
            val dz = player.position.z() - position.z()
            val distSq = dx * dx + dz * dz
            if (distSq <= 100.0) {
                val yaw = (-Math.toDegrees(Math.atan2(dx, dz))).toFloat()
                if (hasEntity) player.sendPacket(EntityHeadLookPacket(entityId, yaw))
                lastYaw = yaw
                val dy = (player.position.y() + 1.62) - (position.y() + 1.62)
                val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                if (dist > 0.01) lastPitch = (-Math.toDegrees(Math.atan2(dy, kotlin.math.sqrt(distSq)))).toFloat()
            }
        }
        standaloneModel?.modeledEntity?.let {
            it.headYaw = lastYaw
            it.headPitch = lastPitch
        }
    }

    private fun showSkin(player: Player, skin: NpcVisual.SkinVisual) {
        val property = skin.skin?.let {
            PlayerInfoUpdatePacket.Property("textures", it.textures(), it.signature())
        }

        player.sendPacket(PlayerInfoUpdatePacket(
            EnumSet.of(
                PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ),
            listOf(
                PlayerInfoUpdatePacket.Entry(
                    uuid, nameRaw,
                    if (property != null) listOf(property) else emptyList(),
                    false, 0, GameMode.CREATIVE, null, null, 0, true,
                )
            ),
        ))

        player.sendPacket(SpawnEntityPacket(
            entityId, uuid, EntityType.PLAYER,
            position, position.yaw(), 0, Vec.ZERO,
        ))

        player.sendPacket(EntityMetaDataPacket(entityId, mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_SKIN_PARTS to Metadata.Byte(0x7F),
        )))

        player.sendPacket(EntityHeadLookPacket(entityId, position.yaw()))

        if (equipment.isNotEmpty()) {
            player.sendPacket(EntityEquipmentPacket(entityId, equipment))
        }

        MinecraftServer.getSchedulerManager().buildTask {
            player.sendPacket(PlayerInfoRemovePacket(listOf(uuid)))
        }.delay(net.minestom.server.timer.TaskSchedule.tick(40)).schedule()
    }

    private fun showEntity(player: Player, entity: NpcVisual.EntityVisual) {
        player.sendPacket(SpawnEntityPacket(
            entityId, uuid, entity.type,
            position, position.yaw(), 0, Vec.ZERO,
        ))

        val meta = buildMap<Int, Metadata.Entry<*>> {
            put(META_NO_GRAVITY, Metadata.Boolean(true))
            putAll(entity.metadata)
        }
        player.sendPacket(EntityMetaDataPacket(entityId, meta))

        if (equipment.isNotEmpty()) {
            player.sendPacket(EntityEquipmentPacket(entityId, equipment))
        }
    }

    private fun showModelHitbox(player: Player) {
        player.sendPacket(SpawnEntityPacket(
            entityId, uuid, EntityType.INTERACTION,
            position, 0f, 0, Vec.ZERO,
        ))
        player.sendPacket(EntityMetaDataPacket(entityId, mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            8 to Metadata.Float(1.0f),
            9 to Metadata.Float(2.0f),
            10 to Metadata.Boolean(true),
        )))
    }

    private fun showNameDisplay(player: Player) {
        val displayPos = position.add(0.0, nameOffset, 0.0)
        player.sendPacket(SpawnEntityPacket(
            nameDisplayEntityId, nameDisplayUuid, EntityType.TEXT_DISPLAY,
            displayPos, displayPos.yaw(), 0, Vec.ZERO,
        ))
        val entries = mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_DISPLAY_SCALE to Metadata.Vector3(Vec(1.0, 1.0, 1.0)),
            META_DISPLAY_BILLBOARD to Metadata.Byte(AbstractDisplayMeta.BillboardConstraints.CENTER.ordinal.toByte()),
            META_DISPLAY_TEXT to Metadata.Component(name),
            META_DISPLAY_BACKGROUND to Metadata.VarInt(0),
            META_DISPLAY_OPACITY to Metadata.Byte((-1).toByte()),
            META_DISPLAY_FLAGS to Metadata.Byte(0x00),
        )
        player.sendPacket(EntityMetaDataPacket(nameDisplayEntityId, entries))
    }

    private inline fun forEachViewer(action: (Player) -> Unit) {
        _viewers.forEach { uuid ->
            MinecraftServer.getConnectionManager()
                .onlinePlayers.firstOrNull { it.uuid == uuid }?.let(action)
        }
    }
}

object NpcRegistry {

    private val npcs = ConcurrentHashMap<Int, Npc>()
    @Volatile private var installed = false

    fun register(npc: Npc) {
        npcs[npc.entityId] = npc
        if (!installed) install()
    }

    fun unregister(npc: Npc) {
        npcs.remove(npc.entityId)
    }

    fun byEntityId(entityId: Int): Npc? = npcs[entityId]

    fun all(): Collection<Npc> = npcs.values

    fun clear() {
        npcs.values.forEach { it.remove() }
        npcs.clear()
    }

    private fun install() {
        installed = true

        val node = net.minestom.server.event.EventNode.all("npc-registry")

        node.addListener(net.minestom.server.event.entity.EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val npc = npcs[event.target.entityId] ?: return@addListener
            npc.handleInteract(player)
        }

        node.addListener(net.minestom.server.event.player.PlayerEntityInteractEvent::class.java) { event ->
            val npc = npcs[event.target.entityId] ?: return@addListener
            npc.handleInteract(event.player)
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)

        MinecraftServer.getSchedulerManager().buildTask {
            npcs.values.forEach { it.tickLookAt() }
        }.repeat(net.minestom.server.timer.TaskSchedule.tick(5)).schedule()
    }
}

class NpcBuilder @PublishedApi internal constructor(private val nameRaw: String) {

    @PublishedApi internal var visual: NpcVisual = NpcVisual.SkinVisual(null)
    @PublishedApi internal var position: Pos = Pos.ZERO
    @PublishedApi internal var onClickHandler: ((Player) -> Unit)? = null
    @PublishedApi internal var lookAtPlayer: Boolean = true
    @PublishedApi internal var nameOffset: Double = 2.05
    @PublishedApi internal val equipment: MutableMap<EquipmentSlot, ItemStack> = mutableMapOf()
    @PublishedApi internal val entityMetadata: MutableMap<Int, Metadata.Entry<*>> = mutableMapOf()
    @PublishedApi internal var modelBlock: (ModeledEntityBuilder.() -> Unit)? = null

    fun skin(skin: PlayerSkin) { visual = NpcVisual.SkinVisual(skin) }
    fun skin(textures: String, signature: String) { visual = NpcVisual.SkinVisual(PlayerSkin(textures, signature)) }
    fun entityType(type: EntityType) { visual = NpcVisual.EntityVisual(type) }
    fun modelOnly() { visual = NpcVisual.ModelVisual }

    fun position(pos: Pos) { this.position = pos }
    fun onClick(handler: (Player) -> Unit) { onClickHandler = handler }
    fun lookAtPlayer(enabled: Boolean) { lookAtPlayer = enabled }
    fun nameOffset(offset: Double) { nameOffset = offset }

    fun metadata(index: Int, entry: Metadata.Entry<*>) { entityMetadata[index] = entry }

    fun helmet(item: ItemStack) { equipment[EquipmentSlot.HELMET] = item }
    fun chestplate(item: ItemStack) { equipment[EquipmentSlot.CHESTPLATE] = item }
    fun leggings(item: ItemStack) { equipment[EquipmentSlot.LEGGINGS] = item }
    fun boots(item: ItemStack) { equipment[EquipmentSlot.BOOTS] = item }
    fun mainHand(item: ItemStack) { equipment[EquipmentSlot.MAIN_HAND] = item }
    fun offHand(item: ItemStack) { equipment[EquipmentSlot.OFF_HAND] = item }

    fun model(block: ModeledEntityBuilder.() -> Unit) { modelBlock = block }

    @PublishedApi internal fun build(): Npc {
        val resolvedVisual = when (val v = visual) {
            is NpcVisual.EntityVisual -> if (entityMetadata.isNotEmpty()) {
                v.copy(metadata = v.metadata + entityMetadata)
            } else v
            else -> v
        }

        val name = miniMessage.deserialize(nameRaw)
        val npc = Npc(
            entityId = nextEntityId.getAndDecrement(),
            uuid = UUID.randomUUID(),
            name = name,
            nameRaw = nameRaw,
            position = position,
            visual = resolvedVisual,
            onClick = onClickHandler,
            equipment = equipment.toMap(),
            nameDisplayEntityId = nextEntityId.getAndDecrement(),
            nameDisplayUuid = UUID.randomUUID(),
            lookAtPlayer = lookAtPlayer,
            nameOffset = nameOffset,
        )
        modelBlock?.let { block ->
            npc.standaloneModel = standAloneModel(position, block)
        }
        NpcRegistry.register(npc)
        return npc
    }
}

inline fun npc(name: String, block: NpcBuilder.() -> Unit = {}): Npc =
    NpcBuilder(name).apply(block).build()

fun Instance.spawnNpc(pos: Pos, block: NpcBuilder.() -> Unit): Npc {
    val npc = NpcBuilder("").apply {
        position(pos)
        block()
    }.build()
    players.forEach { npc.show(it) }
    return npc
}

fun Player.showNpc(npc: Npc) = npc.show(this)
fun Player.hideNpc(npc: Npc) = npc.hide(this)
