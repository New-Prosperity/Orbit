package me.nebula.orbit.utils.damage

import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.registry.RegistryKey
import java.util.concurrent.ConcurrentHashMap

@JvmInline
value class DamageElement internal constructor(val id: String)

object DamageElements {

    private val byId = ConcurrentHashMap<String, DamageElement>()
    private val vanillaMap = ConcurrentHashMap<RegistryKey<DamageType>, DamageElement>()

    val GENERIC = register("generic")
    val PHYSICAL = register("physical")
    val PROJECTILE = register("projectile")
    val FALL = register("fall")
    val FIRE = register("fire")
    val EXPLOSION = register("explosion")
    val MAGIC = register("magic")
    val WITHER = register("wither")
    val LIGHTNING = register("lightning")
    val COLD = register("cold")
    val DROWN = register("drown")
    val STARVE = register("starve")
    val SUFFOCATE = register("suffocate")
    val THORNS = register("thorns")
    val SONIC = register("sonic")
    val DRAGON_BREATH = register("dragon_breath")
    val VOID = register("void")
    val POISON = register("poison")
    val HOLY = register("holy")

    init {
        bind(DamageType.PLAYER_ATTACK, PHYSICAL)
        bind(DamageType.MOB_ATTACK, PHYSICAL)
        bind(DamageType.MOB_ATTACK_NO_AGGRO, PHYSICAL)
        bind(DamageType.MACE_SMASH, PHYSICAL)
        bind(DamageType.SWEET_BERRY_BUSH, PHYSICAL)
        bind(DamageType.CACTUS, PHYSICAL)
        bind(DamageType.STING, PHYSICAL)
        bind(DamageType.SPEAR, PHYSICAL)
        bind(DamageType.STALAGMITE, PHYSICAL)
        bind(DamageType.FALLING_BLOCK, PHYSICAL)
        bind(DamageType.FALLING_ANVIL, PHYSICAL)
        bind(DamageType.FALLING_STALACTITE, PHYSICAL)
        bind(DamageType.FLY_INTO_WALL, PHYSICAL)
        bind(DamageType.ARROW, PROJECTILE)
        bind(DamageType.TRIDENT, PROJECTILE)
        bind(DamageType.THROWN, PROJECTILE)
        bind(DamageType.SPIT, PROJECTILE)
        bind(DamageType.MOB_PROJECTILE, PROJECTILE)
        bind(DamageType.WIND_CHARGE, PROJECTILE)
        bind(DamageType.ENDER_PEARL, PROJECTILE)
        bind(DamageType.FALL, FALL)
        bind(DamageType.ON_FIRE, FIRE)
        bind(DamageType.IN_FIRE, FIRE)
        bind(DamageType.LAVA, FIRE)
        bind(DamageType.HOT_FLOOR, FIRE)
        bind(DamageType.CAMPFIRE, FIRE)
        bind(DamageType.UNATTRIBUTED_FIREBALL, FIRE)
        bind(DamageType.FIREBALL, FIRE)
        bind(DamageType.FIREWORKS, FIRE)
        bind(DamageType.EXPLOSION, EXPLOSION)
        bind(DamageType.PLAYER_EXPLOSION, EXPLOSION)
        bind(DamageType.BAD_RESPAWN_POINT, EXPLOSION)
        bind(DamageType.MAGIC, MAGIC)
        bind(DamageType.INDIRECT_MAGIC, MAGIC)
        bind(DamageType.WITHER_SKULL, MAGIC)
        bind(DamageType.WITHER, WITHER)
        bind(DamageType.LIGHTNING_BOLT, LIGHTNING)
        bind(DamageType.FREEZE, COLD)
        bind(DamageType.DROWN, DROWN)
        bind(DamageType.DRY_OUT, DROWN)
        bind(DamageType.STARVE, STARVE)
        bind(DamageType.IN_WALL, SUFFOCATE)
        bind(DamageType.CRAMMING, SUFFOCATE)
        bind(DamageType.THORNS, THORNS)
        bind(DamageType.SONIC_BOOM, SONIC)
        bind(DamageType.DRAGON_BREATH, DRAGON_BREATH)
        bind(DamageType.OUT_OF_WORLD, VOID)
        bind(DamageType.OUTSIDE_BORDER, VOID)
        bind(DamageType.GENERIC, GENERIC)
        bind(DamageType.GENERIC_KILL, GENERIC)
    }

    fun register(id: String): DamageElement {
        val existing = byId[id]
        if (existing != null) return existing
        val element = DamageElement(id)
        byId[id] = element
        return element
    }

    fun bind(vanilla: RegistryKey<DamageType>, element: DamageElement) {
        vanillaMap[vanilla] = element
    }

    fun byId(id: String): DamageElement? = byId[id]

    fun resolve(type: RegistryKey<DamageType>): DamageElement = vanillaMap[type] ?: GENERIC

    fun resolve(damage: Damage): DamageElement = resolve(damage.type)

    fun all(): Collection<DamageElement> = byId.values

    fun bindings(): Map<RegistryKey<DamageType>, DamageElement> = vanillaMap.toMap()
}
