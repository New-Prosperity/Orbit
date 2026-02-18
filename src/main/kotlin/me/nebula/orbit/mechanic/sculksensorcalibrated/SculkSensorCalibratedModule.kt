package me.nebula.orbit.mechanic.sculksensorcalibrated

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.blockindex.BlockPositionIndex
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule

private val LAST_TRIGGER_TAG = Tag.Long("mechanic:calibrated_sculk:last_trigger").defaultValue(0L)
private const val COOLDOWN_MS = 2000L

private val VIBRATION_FREQUENCIES = mapOf(
    "step" to 1,
    "swim" to 3,
    "item_interact_finish" to 5,
    "block_place" to 8,
    "block_destroy" to 10,
    "entity_damage" to 12,
    "explode" to 15,
)

class SculkSensorCalibratedModule : OrbitModule("sculk-sensor-calibrated") {

    private val index = BlockPositionIndex(setOf("minecraft:calibrated_sculk_sensor"), eventNode).install()

    override fun onEnable() {
        super.onEnable()

        index.instancePositions.cleanOnInstanceRemove { it }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:calibrated_sculk_sensor") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val currentPower = block.getProperty("power")?.toIntOrNull() ?: 0
            val newPower = (currentPower + 1).let { if (it > 15) 0 else it }
            val updated = block.withProperty("power", newPower.toString())
            instance.setBlock(pos, updated)

            instance.playSound(
                Sound.sound(SoundEvent.BLOCK_SCULK_SENSOR_CLICKING.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            val now = System.currentTimeMillis()
            val lastTrigger = player.getTag(LAST_TRIGGER_TAG)
            if (now - lastTrigger < COOLDOWN_MS) return@addListener

            val stepFrequency = VIBRATION_FREQUENCIES["step"] ?: return@addListener

            val nearby = index.positionsNear(instance, player.position.asVec(), 8.0)
            for (vec in nearby) {
                val x = vec.x().toInt()
                val y = vec.y().toInt()
                val z = vec.z().toInt()

                val block = instance.getBlock(x, y, z)
                if (block.name() != "minecraft:calibrated_sculk_sensor") continue

                val sensorPower = block.getProperty("power")?.toIntOrNull() ?: 0
                if (sensorPower != stepFrequency) continue

                player.setTag(LAST_TRIGGER_TAG, now)
                val active = block.withProperty("sculk_sensor_phase", "active")
                instance.setBlock(x, y, z, active)

                instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_SCULK_SENSOR_CLICKING.key(), Sound.Source.BLOCK, 1f, 1f),
                    x + 0.5, y + 0.5, z + 0.5,
                )

                MinecraftServer.getSchedulerManager().buildTask {
                    val current = instance.getBlock(x, y, z)
                    if (current.name() == "minecraft:calibrated_sculk_sensor") {
                        instance.setBlock(x, y, z, current.withProperty("sculk_sensor_phase", "inactive"))
                    }
                }.delay(TaskSchedule.tick(40)).schedule()

                return@addListener
            }
        }
    }

    override fun onDisable() {
        index.clear()
        super.onDisable()
    }
}
