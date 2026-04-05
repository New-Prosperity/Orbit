package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.collision.Aerodynamics
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import java.util.UUID

private val IN_FLUID_TAG = Tag.Boolean("vanilla:in_fluid")

object SwimmingModule : VanillaModule {

    override val id = "swimming"
    override val description = "Water and lava slow player movement, reduce gravity, and change drag coefficients"
    override val configParams = listOf(
        ConfigParam.DoubleParam("waterGravity", "Gravity when submerged in water", 0.02, 0.0, 0.1),
        ConfigParam.DoubleParam("waterDrag", "Horizontal/vertical drag in water", 0.8, 0.1, 1.0),
        ConfigParam.DoubleParam("lavaGravity", "Gravity when submerged in lava", 0.02, 0.0, 0.1),
        ConfigParam.DoubleParam("lavaDrag", "Horizontal/vertical drag in lava", 0.5, 0.1, 1.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val waterGravity = config.getDouble("waterGravity", 0.02)
        val waterDrag = config.getDouble("waterDrag", 0.8)
        val lavaGravity = config.getDouble("lavaGravity", 0.02)
        val lavaDrag = config.getDouble("lavaDrag", 0.5)

        val waterAero = Aerodynamics(waterGravity, waterDrag, waterDrag)
        val lavaAero = Aerodynamics(lavaGravity, lavaDrag, lavaDrag)
        val defaultAero = HashMap<UUID, Aerodynamics>()

        val node = EventNode.all("vanilla-swimming")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.gameMode == GameMode.SPECTATOR) return@addListener

            val inst = player.instance ?: return@addListener
            val feetBlock = inst.getBlock(player.position.blockX(), player.position.blockY(), player.position.blockZ())
            val bodyBlock = inst.getBlock(player.position.blockX(), (player.position.y() + 0.4).toInt(), player.position.blockZ())

            val inWater = feetBlock.compare(Block.WATER) ||
                bodyBlock.compare(Block.WATER)
            val inLava = feetBlock.compare(Block.LAVA) ||
                bodyBlock.compare(Block.LAVA)

            val wasInFluid = player.getTag(IN_FLUID_TAG) ?: false
            val nowInFluid = inWater || inLava

            if (nowInFluid && !wasInFluid) {
                defaultAero[player.uuid] = player.aerodynamics
                player.aerodynamics = if (inLava) lavaAero else waterAero
                player.setTag(IN_FLUID_TAG, true)
            } else if (nowInFluid && wasInFluid) {
                player.aerodynamics = if (inLava) lavaAero else waterAero
            } else if (!nowInFluid && wasInFluid) {
                val original = defaultAero.remove(player.uuid)
                if (original != null) player.aerodynamics = original
                player.setTag(IN_FLUID_TAG, false)
            }
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            defaultAero.remove(event.player.uuid)
        }

        return node
    }
}
