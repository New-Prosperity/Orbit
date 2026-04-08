package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.time.Duration
import kotlin.math.min

private val GRAVITY_BLOCK_NAMES = setOf(
    "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
    "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
    "minecraft:white_concrete_powder", "minecraft:orange_concrete_powder",
    "minecraft:magenta_concrete_powder", "minecraft:light_blue_concrete_powder",
    "minecraft:yellow_concrete_powder", "minecraft:lime_concrete_powder",
    "minecraft:pink_concrete_powder", "minecraft:gray_concrete_powder",
    "minecraft:light_gray_concrete_powder", "minecraft:cyan_concrete_powder",
    "minecraft:purple_concrete_powder", "minecraft:blue_concrete_powder",
    "minecraft:brown_concrete_powder", "minecraft:green_concrete_powder",
    "minecraft:red_concrete_powder", "minecraft:black_concrete_powder",
)

private val CONCRETE_POWDER_TO_CONCRETE = mapOf(
    "minecraft:white_concrete_powder" to Block.WHITE_CONCRETE,
    "minecraft:orange_concrete_powder" to Block.ORANGE_CONCRETE,
    "minecraft:magenta_concrete_powder" to Block.MAGENTA_CONCRETE,
    "minecraft:light_blue_concrete_powder" to Block.LIGHT_BLUE_CONCRETE,
    "minecraft:yellow_concrete_powder" to Block.YELLOW_CONCRETE,
    "minecraft:lime_concrete_powder" to Block.LIME_CONCRETE,
    "minecraft:pink_concrete_powder" to Block.PINK_CONCRETE,
    "minecraft:gray_concrete_powder" to Block.GRAY_CONCRETE,
    "minecraft:light_gray_concrete_powder" to Block.LIGHT_GRAY_CONCRETE,
    "minecraft:cyan_concrete_powder" to Block.CYAN_CONCRETE,
    "minecraft:purple_concrete_powder" to Block.PURPLE_CONCRETE,
    "minecraft:blue_concrete_powder" to Block.BLUE_CONCRETE,
    "minecraft:brown_concrete_powder" to Block.BROWN_CONCRETE,
    "minecraft:green_concrete_powder" to Block.GREEN_CONCRETE,
    "minecraft:red_concrete_powder" to Block.RED_CONCRETE,
    "minecraft:black_concrete_powder" to Block.BLACK_CONCRETE,
)

private val ANVIL_NAMES = setOf("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil")

private fun isGravityBlock(block: Block): Boolean = block.name() in GRAVITY_BLOCK_NAMES

private fun canFallThrough(block: Block): Boolean = block.isAir || block.isLiquid

private fun isAdjacentToWater(instance: Instance, x: Int, y: Int, z: Int): Boolean {
    val offsets = intArrayOf(-1, 0, 0, 1, 0, 0, 0, -1, 0, 0, 1, 0, 0, 0, -1, 0, 0, 1)
    var i = 0
    while (i < offsets.size) {
        val block = instance.getBlock(x + offsets[i], y + offsets[i + 1], z + offsets[i + 2])
        if (block.compare(Block.WATER)) return true
        i += 3
    }
    return false
}

object GravityBlocksModule : VanillaModule {

    override val id = "gravity-blocks"
    override val description = "Sand, gravel, concrete powder, and anvils fall when unsupported. Concrete solidifies in water. Anvils damage entities."

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-gravity-blocks")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val block = event.block
            val blockName = block.name()

            val concreteForm = CONCRETE_POWDER_TO_CONCRETE[blockName]
            if (concreteForm != null) {
                val x = event.blockPosition.blockX()
                val y = event.blockPosition.blockY()
                val z = event.blockPosition.blockZ()
                if (isAdjacentToWater(event.instance, x, y, z)) {
                    event.block = concreteForm
                    return@addListener
                }
            }

            if (isGravityBlock(block)) {
                val x = event.blockPosition.blockX()
                val y = event.blockPosition.blockY()
                val z = event.blockPosition.blockZ()
                val below = event.instance.getBlock(x, y - 1, z)
                if (canFallThrough(below)) {
                    event.instance.scheduler().buildTask {
                        spawnFallingBlock(event.instance, x, y, z)
                    }.delay(Duration.ofMillis(50)).schedule()
                }
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val x = event.blockPosition.blockX()
            val z = event.blockPosition.blockZ()
            event.instance.scheduler().buildTask {
                checkColumnAbove(event.instance, x, event.blockPosition.blockY() + 1, z)
            }.delay(Duration.ofMillis(50)).schedule()
        }

        return node
    }

    private fun checkColumnAbove(instance: Instance, x: Int, startY: Int, z: Int) {
        for (y in startY..319) {
            val block = instance.getBlock(x, y, z)
            if (!isGravityBlock(block)) break
            spawnFallingBlock(instance, x, y, z)
        }
    }

    private fun spawnFallingBlock(instance: Instance, x: Int, y: Int, z: Int) {
        val block = instance.getBlock(x, y, z)
        if (!isGravityBlock(block)) return
        if (!canFallThrough(instance.getBlock(x, y - 1, z))) return

        instance.setBlock(x, y, z, Block.AIR)
        val isAnvil = block.name() in ANVIL_NAMES
        val startY = y

        val entity = Entity(EntityType.FALLING_BLOCK)
        val meta = entity.entityMeta
        if (meta is FallingBlockMeta) {
            meta.block = block
        }
        entity.setNoGravity(false)
        entity.setInstance(instance, Pos(x + 0.5, y.toDouble(), z + 0.5))

        var ticksAlive = 0
        entity.scheduler().buildTask {
            ticksAlive++
            if (ticksAlive > 600) {
                entity.remove()
                return@buildTask
            }
            if (entity.isOnGround || entity.position.y() < -64) {
                val landX = entity.position.blockX()
                val landY = entity.position.blockY()
                val landZ = entity.position.blockZ()

                val landBlock = resolveLandingBlock(instance, block, landX, landY, landZ)

                if (canFallThrough(instance.getBlock(landX, landY, landZ))) {
                    instance.setBlock(landX, landY, landZ, landBlock)
                }

                if (isAnvil) {
                    val fallDistance = startY - landY
                    if (fallDistance > 1) {
                        val damage = min(40f, (fallDistance - 1) * 2f)
                        val landPos = Vec(landX + 0.5, landY.toDouble(), landZ + 0.5)
                        instance.entityTracker.nearbyEntities(landPos, 1.5, EntityTracker.Target.ENTITIES) { nearby ->
                            if (nearby === entity) return@nearbyEntities
                            if (nearby !is LivingEntity) return@nearbyEntities
                            if (nearby.position.blockX() == landX && nearby.position.blockZ() == landZ) {
                                val entityY = nearby.position.blockY()
                                if (entityY in landY..(landY + 1)) {
                                    nearby.damage(DamageType.FALLING_ANVIL, damage)
                                }
                            }
                        }
                    }
                }

                entity.remove()
            }
        }.repeat(Duration.ofMillis(50)).schedule()
    }

    private fun resolveLandingBlock(instance: Instance, original: Block, x: Int, y: Int, z: Int): Block {
        val concreteName = original.name()
        val concrete = CONCRETE_POWDER_TO_CONCRETE[concreteName]
        if (concrete != null && isAdjacentToWater(instance, x, y, z)) {
            return concrete
        }
        return original
    }
}
