package me.nebula.orbit.mechanic.lever

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class ButtonKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class LeverModule : OrbitModule("lever") {

    private val activeButtons = ConcurrentHashMap.newKeySet<ButtonKey>()

    override fun onEnable() {
        super.onEnable()
        activeButtons.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val name = block.name()
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            when {
                name == "minecraft:lever" -> {
                    val powered = block.getProperty("powered") == "true"
                    instance.setBlock(pos, block.withProperty("powered", (!powered).toString()))
                    playClickSound(instance, pos, !powered)
                }

                name.endsWith("_button") -> {
                    val key = ButtonKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
                    if (activeButtons.contains(key)) return@addListener

                    instance.setBlock(pos, block.withProperty("powered", "true"))
                    activeButtons.add(key)
                    playClickSound(instance, pos, true)

                    val ticks = if (name.contains("stone")) 20 else 30
                    MinecraftServer.getSchedulerManager().buildTask {
                        val current = instance.getBlock(pos)
                        if (current.name() == name) {
                            instance.setBlock(pos, current.withProperty("powered", "false"))
                            playClickSound(instance, pos, false)
                        }
                        activeButtons.remove(key)
                    }.delay(TaskSchedule.tick(ticks)).schedule()
                }

                name.endsWith("_pressure_plate") -> {
                    val powered = block.getProperty("powered") == "true"
                    if (!powered) {
                        instance.setBlock(pos, block.withProperty("powered", "true"))
                        playClickSound(instance, pos, true)
                    }
                }
            }
        }
    }

    override fun onDisable() {
        activeButtons.clear()
        super.onDisable()
    }

    private fun playClickSound(instance: Instance, pos: Point, on: Boolean) {
        val sound = if (on) SoundEvent.BLOCK_STONE_BUTTON_CLICK_ON else SoundEvent.BLOCK_STONE_BUTTON_CLICK_OFF
        instance.playSound(
            Sound.sound(sound.key(), Sound.Source.BLOCK, 0.3f, if (on) 0.6f else 0.5f),
            pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
        )
    }
}
