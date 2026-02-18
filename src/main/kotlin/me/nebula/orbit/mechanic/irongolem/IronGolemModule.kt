package me.nebula.orbit.mechanic.irongolem

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

private val PUMPKIN_BLOCKS = setOf("minecraft:carved_pumpkin", "minecraft:jack_o_lantern")

class IronGolemModule : OrbitModule("iron-golem") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in PUMPKIN_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val headPos = event.blockPosition

            checkIronGolemPattern(instance, headPos)
            checkSnowGolemPattern(instance, headPos)
        }
    }

    private fun checkIronGolemPattern(instance: Instance, headPos: net.minestom.server.coordinate.Point) {
        val bodyPos = Vec(headPos.x().toDouble(), headPos.y() - 1.0, headPos.z().toDouble())
        if (instance.getBlock(bodyPos).name() != "minecraft:iron_block") return

        val belowBody = Vec(bodyPos.x(), bodyPos.y() - 1.0, bodyPos.z())
        if (instance.getBlock(belowBody).name() != "minecraft:iron_block") return

        val xArm = listOf(
            Vec(bodyPos.x() - 1.0, bodyPos.y(), bodyPos.z()),
            Vec(bodyPos.x() + 1.0, bodyPos.y(), bodyPos.z()),
        )
        val zArm = listOf(
            Vec(bodyPos.x(), bodyPos.y(), bodyPos.z() - 1.0),
            Vec(bodyPos.x(), bodyPos.y(), bodyPos.z() + 1.0),
        )

        val xValid = xArm.all { instance.getBlock(it).name() == "minecraft:iron_block" }
        val zValid = zArm.all { instance.getBlock(it).name() == "minecraft:iron_block" }

        val armPositions = when {
            xValid -> xArm
            zValid -> zArm
            else -> return
        }

        instance.setBlock(headPos, Block.AIR)
        instance.setBlock(bodyPos, Block.AIR)
        instance.setBlock(belowBody, Block.AIR)
        armPositions.forEach { instance.setBlock(it, Block.AIR) }

        val golem = Entity(EntityType.IRON_GOLEM)
        golem.setInstance(instance, Vec(bodyPos.x(), belowBody.y(), bodyPos.z()))
    }

    private fun checkSnowGolemPattern(instance: Instance, headPos: net.minestom.server.coordinate.Point) {
        val body1 = Vec(headPos.x().toDouble(), headPos.y() - 1.0, headPos.z().toDouble())
        val body2 = Vec(headPos.x().toDouble(), headPos.y() - 2.0, headPos.z().toDouble())

        if (instance.getBlock(body1).name() != "minecraft:snow_block") return
        if (instance.getBlock(body2).name() != "minecraft:snow_block") return

        instance.setBlock(headPos, Block.AIR)
        instance.setBlock(body1, Block.AIR)
        instance.setBlock(body2, Block.AIR)

        val snowGolem = Entity(EntityType.SNOW_GOLEM)
        snowGolem.setInstance(instance, Vec(body2.x(), body2.y(), body2.z()))
    }
}
