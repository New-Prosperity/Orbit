package me.nebula.orbit.utils.condition

import me.nebula.orbit.utils.permissions.PermissionManager
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

fun interface Condition<T> {
    fun test(target: T): Boolean

    infix fun and(other: Condition<T>): Condition<T> =
        Condition { test(it) && other.test(it) }

    infix fun or(other: Condition<T>): Condition<T> =
        Condition { test(it) || other.test(it) }

    infix fun xor(other: Condition<T>): Condition<T> =
        Condition { test(it) xor other.test(it) }

    operator fun not(): Condition<T> =
        Condition { !test(it) }
}

fun <T> not(condition: Condition<T>): Condition<T> = !condition

fun <T> allOf(vararg conditions: Condition<T>): Condition<T> =
    Condition { target -> conditions.all { it.test(target) } }

fun <T> anyOf(vararg conditions: Condition<T>): Condition<T> =
    Condition { target -> conditions.any { it.test(target) } }

fun <T> noneOf(vararg conditions: Condition<T>): Condition<T> =
    Condition { target -> conditions.none { it.test(target) } }

inline fun condition(crossinline predicate: (Player) -> Boolean): Condition<Player> =
    Condition { predicate(it) }

fun isAlive(): Condition<Player> = Condition { !it.isDead }

fun hasHealth(min: Float): Condition<Player> = Condition { it.health >= min }

fun hasItem(material: Material): Condition<Player> = Condition { player ->
    (0 until player.inventory.size).any { slot ->
        player.inventory.getItemStack(slot).material() == material
    }
}

fun hasPermission(permission: String): Condition<Player> =
    Condition { PermissionManager.hasPermission(it.uuid, permission) }

fun isInRegion(region: me.nebula.orbit.utils.region.Region): Condition<Player> =
    Condition { region.contains(it.position) }

fun isOnGround(): Condition<Player> = Condition { it.isOnGround }

fun isSneaking(): Condition<Player> = Condition { it.isSneaking }

fun isSprinting(): Condition<Player> = Condition { it.isSprinting }

fun hasMinPlayers(count: Int): Condition<Player> = Condition { player ->
    val instance = player.instance ?: return@Condition false
    instance.players.size >= count
}

fun isInInstance(instance: net.minestom.server.instance.Instance): Condition<Player> =
    Condition { it.instance == instance }

fun hasExperience(min: Int): Condition<Player> = Condition { it.level >= min }

fun Player.meets(condition: Condition<Player>): Boolean = condition.test(this)
