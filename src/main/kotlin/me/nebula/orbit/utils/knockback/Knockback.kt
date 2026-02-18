package me.nebula.orbit.utils.knockback

import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

data class KnockbackProfile(
    val name: String,
    val horizontal: Double = 0.4,
    val vertical: Double = 0.4,
    val extraHorizontal: Double = 0.0,
    val extraVertical: Double = 0.0,
    val friction: Double = 1.0,
)

object KnockbackManager {

    private val profiles = ConcurrentHashMap<String, KnockbackProfile>()
    private val playerOverrides = ConcurrentHashMap<java.util.UUID, String>()

    val DEFAULT = KnockbackProfile("default")

    init {
        register(DEFAULT)
    }

    fun register(profile: KnockbackProfile) {
        profiles[profile.name] = profile
    }

    fun get(name: String): KnockbackProfile? = profiles[name]

    fun setPlayerProfile(player: Player, profileName: String) {
        playerOverrides[player.uuid] = profileName
    }

    fun clearPlayerProfile(player: Player) {
        playerOverrides.remove(player.uuid)
    }

    fun getEffectiveProfile(player: Player): KnockbackProfile =
        playerOverrides[player.uuid]?.let { profiles[it] } ?: DEFAULT

    fun all(): Collection<KnockbackProfile> = profiles.values
}

fun Entity.applyKnockback(source: Entity, profile: KnockbackProfile = KnockbackManager.DEFAULT) {
    val dx = position.x() - source.position.x()
    val dz = position.z() - source.position.z()
    val dist = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
    val nx = dx / dist
    val nz = dz / dist
    velocity = Vec(
        nx * profile.horizontal * 20.0 + profile.extraHorizontal * nx,
        profile.vertical * 20.0 + profile.extraVertical,
        nz * profile.horizontal * 20.0 + profile.extraHorizontal * nz,
    ).mul(profile.friction)
}

fun Entity.applyDirectionalKnockback(yawDegrees: Float, profile: KnockbackProfile = KnockbackManager.DEFAULT) {
    val rad = Math.toRadians(yawDegrees.toDouble())
    val dx = -sin(rad)
    val dz = cos(rad)
    velocity = Vec(
        dx * profile.horizontal * 20.0 + profile.extraHorizontal * dx,
        profile.vertical * 20.0 + profile.extraVertical,
        dz * profile.horizontal * 20.0 + profile.extraHorizontal * dz,
    ).mul(profile.friction)
}

fun knockbackProfile(name: String, block: KnockbackProfileBuilder.() -> Unit): KnockbackProfile =
    KnockbackProfileBuilder(name).apply(block).build()

class KnockbackProfileBuilder(private val name: String) {
    var horizontal: Double = 0.4
    var vertical: Double = 0.4
    var extraHorizontal: Double = 0.0
    var extraVertical: Double = 0.0
    var friction: Double = 1.0

    fun build(): KnockbackProfile = KnockbackProfile(name, horizontal, vertical, extraHorizontal, extraVertical, friction)
}
