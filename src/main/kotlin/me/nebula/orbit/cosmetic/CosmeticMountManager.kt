package me.nebula.orbit.cosmetic

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.behavior.MountBehavior
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import me.nebula.orbit.utils.modelengine.modeledEntity
import me.nebula.orbit.utils.modelengine.mount.MountManager
import me.nebula.orbit.utils.modelengine.mount.WalkingController
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

private val MOUNT_TAG = Tag.String("cosmetic:mount")
private const val MOVE_THRESHOLD = 0.05
private const val TELEPORT_DISTANCE = 30.0

data class ActiveMount(
    val entity: EntityCreature,
    val modeled: ModeledEntity,
    val mountBehavior: MountBehavior,
    val cosmeticId: String,
    val level: Int,
    val speed: Double,
)

class CosmeticMountManager {

    private val logger = logger("CosmeticMountManager")
    private val mounts = ConcurrentHashMap<UUID, ActiveMount>()
    private var task: Task? = null
    private var eventNode: EventNode<*>? = null

    fun install() {
        MountManager.install()
        task = repeat(1) { tick() }
        val node = EventNode.all("cosmetic-mount-manager")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            despawn(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        task?.cancel()
        task = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        val iterator = mounts.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, mount) = iterator.next()
            MountManager.evictPlayer(uuid)
            mount.modeled.destroy()
            mount.entity.remove()
            iterator.remove()
        }
        MountManager.uninstall()
    }

    fun spawn(player: Player, cosmeticId: String, level: Int) {
        despawn(player.uuid)
        val definition = CosmeticRegistry[cosmeticId] ?: run {
            logger.warn { "Cosmetic definition not found: $cosmeticId for player ${player.uuid}" }
            return
        }
        val resolved = definition.resolveData(level)
        val modelId = resolved["modelId"] ?: return
        if (ModelEngine.blueprintOrNull(modelId) == null) {
            logger.warn { "Blueprint not found: $modelId for cosmetic $cosmeticId" }
            return
        }
        val scale = resolved["scale"]?.toFloatOrNull() ?: 1.0f
        val speed = resolved["speed"]?.toDoubleOrNull() ?: 0.2
        val seatBone = resolved["seatBone"] ?: "seat"
        val instance = player.instance ?: return

        val creature = EntityCreature(EntityType.ZOMBIE)
        creature.isInvisible = true
        creature.isSilent = true
        creature.setInstance(instance, player.position.add(2.0, 0.0, 0.0)).thenRun {
            val modeled = modeledEntity(creature) {
                model(modelId, autoPlayIdle = true) { scale(scale) }
            }

            val activeModel = modeled.models.values.firstOrNull() ?: run {
                modeled.destroy()
                creature.remove()
                return@thenRun
            }
            val bone = activeModel.bones[seatBone] ?: run {
                modeled.destroy()
                creature.remove()
                return@thenRun
            }
            val seatOffsetY = resolved["seatOffsetY"]?.toDoubleOrNull() ?: 0.0
            val mountBehavior = MountBehavior(bone, seatOffset = Vec(0.0, seatOffsetY, 0.0))
            bone.addBehavior(mountBehavior)
            mountBehavior.onAdd(modeled)

            modeled.show(player)

            val item = itemStack(Material.SADDLE) {
                name("<yellow>${player.translateRaw(definition.nameKey)}")
            }.withTag(MOUNT_TAG, cosmeticId)
            player.inventory.setItemStack(MOUNT_SLOT, item)

            mounts[player.uuid] = ActiveMount(creature, modeled, mountBehavior, cosmeticId, level, speed)
        }
    }

    fun despawn(playerId: UUID) {
        val mount = mounts.remove(playerId) ?: return
        MountManager.evictPlayer(playerId)
        mount.modeled.destroy()
        mount.entity.remove()
        MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId)
            ?.inventory?.setItemStack(MOUNT_SLOT, ItemStack.AIR)
    }

    fun toggleMount(player: Player) {
        val mount = mounts[player.uuid] ?: return
        if (MountManager.isMounted(player)) {
            MountManager.dismount(player)
            return
        }

        val distance = player.position.distance(mount.entity.position)
        if (distance > TELEPORT_DISTANCE) {
            mount.entity.teleport(player.position.add(2.0, 0.0, 0.0))
        }

        MountManager.mount(player, mount.modeled, mount.mountBehavior, WalkingController(speed = mount.speed))
    }

    fun isActive(playerId: UUID): Boolean = mounts.containsKey(playerId)

    private fun tick() {
        val connectionManager = MinecraftServer.getConnectionManager()

        val iterator = mounts.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, mount) = iterator.next()
            val player = connectionManager.getOnlinePlayerByUuid(uuid)
            if (player == null) {
                MountManager.evictPlayer(uuid)
                mount.modeled.destroy()
                mount.entity.remove()
                iterator.remove()
                continue
            }

            if (mount.entity.instance != player.instance) {
                val inst = player.instance ?: continue
                MountManager.evictPlayer(uuid)
                mount.entity.setInstance(inst, player.position.add(2.0, 0.0, 0.0))
                continue
            }

            updateAnimation(mount)

            CosmeticVisibility.updateViewers(
                mount.modeled,
                player.instance?.players ?: emptyList(),
                uuid,
                mount.entity.position,
            )
        }
    }

    private fun updateAnimation(mount: ActiveMount) {
        val definition = CosmeticRegistry[mount.cosmeticId] ?: return
        val resolved = definition.resolveData(mount.level)
        val walkAnim = resolved["walkAnimation"] ?: return
        val model = mount.modeled.models.values.firstOrNull() ?: return
        val velocity = mount.entity.velocity
        val speed = sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z())

        if (speed > MOVE_THRESHOLD) {
            if (!model.isPlayingAnimation(walkAnim)) {
                model.playAnimation(walkAnim, lerpIn = 0.2f, lerpOut = 0.2f, speed = 1.0f)
            }
        } else {
            if (model.isPlayingAnimation(walkAnim)) {
                model.stopAnimation(walkAnim)
            }
        }
    }

    companion object {
        const val MOUNT_SLOT = 8
    }
}
