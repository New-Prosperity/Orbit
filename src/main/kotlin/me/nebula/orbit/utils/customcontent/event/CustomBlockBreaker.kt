package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlock
import me.nebula.orbit.utils.customcontent.block.CustomBlockDrops
import me.nebula.orbit.utils.particle.spawnParticleAt
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.particle.Particle
import net.minestom.server.timer.TaskSchedule
import java.time.Duration

object CustomBlockBreaker {

    fun execute(instance: Instance, pos: BlockVec, customBlock: CustomBlock) {
        instance.setBlock(pos, Block.AIR)

        val center = Pos(pos.x() + 0.5, pos.y() + 0.25, pos.z() + 0.5)
        val drops: List<ItemStack> = when (val d = customBlock.drops) {
            is CustomBlockDrops.SelfDrop -> customBlock.item()?.let { listOf(it.createStack()) } ?: emptyList()
            is CustomBlockDrops.LootTableDrop -> d.lootTable.roll()
        }

        drops.forEach { stack ->
            val itemEntity = ItemEntity(stack)
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(instance, center)
            itemEntity.scheduler().buildTask { itemEntity.remove() }
                .delay(TaskSchedule.minutes(5))
                .schedule()
        }

        CarrierSoundSuppressor.suppressBreak(instance)
        instance.playSound(
            Sound.sound(Key.key("minecraft", customBlock.breakSound), Sound.Source.BLOCK, 1f, 1f),
            pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
        )
        instance.spawnParticleAt(Particle.POOF, center, count = 8, spread = 0.3f, speed = 0.05f)
    }
}
