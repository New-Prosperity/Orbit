package me.nebula.orbit.utils.entityglow

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val META_SHARED_FLAGS = 0
private const val FLAG_ON_FIRE = 0x01
private const val FLAG_SNEAKING = 0x02
private const val FLAG_SPRINTING = 0x08
private const val FLAG_SWIMMING = 0x10
private const val FLAG_INVISIBLE = 0x20
private const val FLAG_GLOWING = 0x40
private const val FLAG_ELYTRA = 0x80

object EntityGlowManager {

    private val glowStates = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, Task>>()

    fun setGlowing(viewer: Player, target: Entity, glowing: Boolean) {
        val viewerMap = glowStates.computeIfAbsent(viewer.uuid) { ConcurrentHashMap() }
        if (glowing) {
            if (viewerMap.containsKey(target.entityId)) return
            sendGlowMetadata(viewer, target, true)
            val task = MinecraftServer.getSchedulerManager()
                .buildTask {
                    if (target.isRemoved || !viewer.isOnline) {
                        viewerMap.remove(target.entityId)?.cancel()
                        if (viewerMap.isEmpty()) glowStates.remove(viewer.uuid)
                        return@buildTask
                    }
                    sendGlowMetadata(viewer, target, true)
                }
                .repeat(TaskSchedule.tick(20))
                .schedule()
            viewerMap[target.entityId] = task
        } else {
            val task = viewerMap.remove(target.entityId) ?: return
            task.cancel()
            if (!target.isRemoved && viewer.isOnline) {
                sendGlowMetadata(viewer, target, false)
            }
            if (viewerMap.isEmpty()) glowStates.remove(viewer.uuid)
        }
    }

    fun isGlowing(viewer: Player, target: Entity): Boolean =
        glowStates[viewer.uuid]?.containsKey(target.entityId) == true

    fun clearViewer(viewer: Player) {
        val tasks = glowStates.remove(viewer.uuid) ?: return
        tasks.values.forEach { it.cancel() }
    }

    fun clearTarget(target: Entity) {
        glowStates.forEach { (viewerUuid, targets) ->
            val task = targets.remove(target.entityId) ?: return@forEach
            task.cancel()
            if (!target.isRemoved) {
                MinecraftServer.getConnectionManager()
                    .onlinePlayers.firstOrNull { it.uuid == viewerUuid }
                    ?.let { sendGlowMetadata(it, target, false) }
            }
        }
        glowStates.entries.removeIf { it.value.isEmpty() }
    }

    fun clearAll() {
        glowStates.values.forEach { targets ->
            targets.values.forEach { it.cancel() }
        }
        glowStates.clear()
    }

    fun setGlobalGlowing(target: Entity, glowing: Boolean) {
        target.isGlowing = glowing
    }

    fun setTimedGlowing(target: Entity, durationTicks: Int): Task {
        target.isGlowing = true
        return MinecraftServer.getSchedulerManager()
            .buildTask { target.isGlowing = false }
            .delay(TaskSchedule.tick(durationTicks))
            .schedule()
    }

    private fun sendGlowMetadata(viewer: Player, target: Entity, glowing: Boolean) {
        viewer.sendPacket(EntityMetaDataPacket(target.entityId, mapOf(
            META_SHARED_FLAGS to Metadata.Byte(buildSharedFlags(target, glowing))
        )))
    }

    private fun buildSharedFlags(entity: Entity, glowing: Boolean): Byte {
        val meta = entity.entityMeta
        var flags = 0
        if (meta.isOnFire) flags = flags or FLAG_ON_FIRE
        if (meta.isSneaking) flags = flags or FLAG_SNEAKING
        if (meta.isSprinting) flags = flags or FLAG_SPRINTING
        if (meta.isSwimming) flags = flags or FLAG_SWIMMING
        if (meta.isInvisible) flags = flags or FLAG_INVISIBLE
        if (glowing || meta.isHasGlowingEffect) flags = flags or FLAG_GLOWING
        if (meta.isFlyingWithElytra) flags = flags or FLAG_ELYTRA
        return flags.toByte()
    }
}

fun Player.setGlowingFor(target: Entity, glowing: Boolean) =
    EntityGlowManager.setGlowing(this, target, glowing)

fun Player.isGlowingFor(target: Entity): Boolean =
    EntityGlowManager.isGlowing(this, target)

fun Player.clearGlowViews() =
    EntityGlowManager.clearViewer(this)

fun Entity.setGlobalGlow(glowing: Boolean) =
    EntityGlowManager.setGlobalGlowing(this, glowing)

fun Entity.glowFor(durationTicks: Int): Task =
    EntityGlowManager.setTimedGlowing(this, durationTicks)
