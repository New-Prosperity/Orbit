package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.blueprint.BoneBehaviorType
import me.nebula.orbit.utils.modelengine.bone.ModelBone
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object BoneBehaviorFactory {

    fun create(type: BoneBehaviorType, bone: ModelBone, config: Map<String, Any>): BoneBehavior = when (type) {
        BoneBehaviorType.HEAD -> HeadBehavior(
            bone = bone,
            smoothFactor = (config["smoothFactor"] as? Number)?.toFloat() ?: 0.3f,
            maxPitch = (config["maxPitch"] as? Number)?.toFloat() ?: 45f,
            maxYaw = (config["maxYaw"] as? Number)?.toFloat() ?: 70f,
        )

        BoneBehaviorType.MOUNT -> MountBehavior(
            bone = bone,
            seatOffset = config.readVec("seatOffsetX", "seatOffsetY", "seatOffsetZ"),
        )

        BoneBehaviorType.NAMETAG -> NameTagBehavior(
            bone = bone,
            text = Component.text(config["text"] as? String ?: ""),
            yOffset = (config["yOffset"] as? Number)?.toDouble() ?: 0.3,
            backgroundColor = (config["backgroundColor"] as? Number)?.toInt() ?: 0,
            seeThrough = config["seeThrough"] as? Boolean ?: false,
            scale = (config["scale"] as? Number)?.toFloat() ?: 1f,
        )

        BoneBehaviorType.HELD_ITEM -> HeldItemBehavior(
            bone = bone,
            item = ItemStack.of(
                Material.fromKey(config["material"] as? String ?: "minecraft:paper") ?: Material.PAPER,
            ),
        )

        BoneBehaviorType.GHOST -> GhostBehavior(bone)

        BoneBehaviorType.SEGMENT -> SegmentBehavior(
            bone = bone,
            angleLimit = (config["angleLimit"] as? Number)?.toFloat() ?: 45f,
            rollLock = config["rollLock"] as? Boolean ?: true,
        )

        BoneBehaviorType.SUB_HITBOX -> SubHitboxBehavior(
            bone = bone,
            halfExtents = config.readVec("halfExtentX", "halfExtentY", "halfExtentZ", default = 0.5),
            damageMultiplier = (config["damageMultiplier"] as? Number)?.toFloat() ?: 1f,
        )

        BoneBehaviorType.LEASH -> LeashBehavior(
            bone = bone,
            maxDistance = (config["maxDistance"] as? Number)?.toDouble() ?: 10.0,
        )

        BoneBehaviorType.PLAYER_LIMB -> {
            val skinTextures = config["skinTextures"] as? String
            val skinSignature = config["skinSignature"] as? String
            val skin = if (skinTextures != null) PlayerSkin(skinTextures, skinSignature) else null
            PlayerLimbBehavior(
                bone = bone,
                limbType = runCatching {
                    LimbType.valueOf((config["limbType"] as? String ?: "HEAD").uppercase())
                }.getOrDefault(LimbType.HEAD),
                skin = skin,
            )
        }
    }

    private fun Map<String, Any>.readVec(xKey: String, yKey: String, zKey: String, default: Double = 0.0): Vec = Vec(
        (this[xKey] as? Number)?.toDouble() ?: default,
        (this[yKey] as? Number)?.toDouble() ?: default,
        (this[zKey] as? Number)?.toDouble() ?: default,
    )
}
