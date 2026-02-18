package me.nebula.orbit.utils.permissions

import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PermissionManager {

    private val groups = ConcurrentHashMap<String, PermissionGroup>()
    private val playerGroups = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun defineGroup(name: String, block: PermissionGroupBuilder.() -> Unit): PermissionGroup {
        val builder = PermissionGroupBuilder(name).apply(block)
        val group = builder.build()
        groups[name] = group
        return group
    }

    fun group(name: String): PermissionGroup? = groups[name]

    fun applyGroup(player: Player, groupName: String) {
        val group = groups[groupName] ?: return
        playerGroups.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }.add(groupName)
    }

    fun removeGroup(player: Player, groupName: String) {
        playerGroups[player.uuid]?.remove(groupName)
    }

    fun hasPermission(uuid: UUID, permission: String): Boolean {
        val assignedGroups = playerGroups[uuid] ?: return false
        return assignedGroups.any { groupName ->
            val group = groups[groupName] ?: return@any false
            permission in group.permissions || group.inherits.any { hasGroupPermission(it, permission) }
        }
    }

    private fun hasGroupPermission(groupName: String, permission: String): Boolean {
        val group = groups[groupName] ?: return false
        return permission in group.permissions || group.inherits.any { hasGroupPermission(it, permission) }
    }

    fun cleanup(uuid: UUID) = playerGroups.remove(uuid)

    fun allGroups(): Map<String, PermissionGroup> = groups.toMap()
}

data class PermissionGroup(
    val name: String,
    val permissions: Set<String>,
    val inherits: List<String>,
    val prefix: String,
    val priority: Int,
)

class PermissionGroupBuilder @PublishedApi internal constructor(private val name: String) {

    private val permissions = mutableSetOf<String>()
    private val inherits = mutableListOf<String>()
    var prefix: String = ""
    var priority: Int = 0

    fun permission(perm: String) {
        permissions += perm
    }

    fun permissions(vararg perms: String) {
        permissions += perms
    }

    fun inherit(groupName: String) {
        inherits += groupName
    }

    fun build() = PermissionGroup(name, permissions.toSet(), inherits.toList(), prefix, priority)
}

fun Player.hasOrbitPermission(permission: String): Boolean =
    PermissionManager.hasPermission(uuid, permission)
