package me.nebula.orbit.mechanic.villagerprofession

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val PROFESSION_TAG = Tag.String("mechanic:villager_profession:profession").defaultValue("none")
private val WORKSTATION_TAG = Tag.Boolean("mechanic:villager_profession:has_workstation").defaultValue(false)

private const val SCAN_RANGE = 5
private const val SCAN_INTERVAL_TICKS = 40

private val WORKSTATION_PROFESSIONS: Map<Block, String> = mapOf(
    Block.SMITHING_TABLE to "toolsmith",
    Block.LOOM to "shepherd",
    Block.CARTOGRAPHY_TABLE to "cartographer",
    Block.BREWING_STAND to "cleric",
    Block.COMPOSTER to "farmer",
    Block.BARREL to "fisherman",
    Block.BLAST_FURNACE to "armorer",
    Block.SMOKER to "butcher",
    Block.FLETCHING_TABLE to "fletcher",
    Block.CAULDRON to "leatherworker",
    Block.LECTERN to "librarian",
    Block.STONECUTTER to "mason",
    Block.GRINDSTONE to "weaponsmith",
)

class VillagerProfessionModule : OrbitModule("villager-profession") {

    private var tickTask: Task? = null
    private val trackedVillagers: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedVillagers.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedVillagers.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.VILLAGER) return@entityLoop
                trackedVillagers.add(entity)
            }
        }

        trackedVillagers.forEach { villager ->
            if (villager.isRemoved) return@forEach
            if (villager.getTag(WORKSTATION_TAG)) return@forEach

            val instance = villager.instance ?: return@forEach
            val profession = findNearbyWorkstation(villager, instance) ?: return@forEach

            villager.setTag(PROFESSION_TAG, profession)
            villager.setTag(WORKSTATION_TAG, true)
        }
    }

    private fun findNearbyWorkstation(villager: Entity, instance: Instance): String? {
        val pos = villager.position
        for (dx in -SCAN_RANGE..SCAN_RANGE) {
            for (dy in -2..2) {
                for (dz in -SCAN_RANGE..SCAN_RANGE) {
                    val block = instance.getBlock(pos.blockX() + dx, pos.blockY() + dy, pos.blockZ() + dz)
                    for ((workstation, prof) in WORKSTATION_PROFESSIONS) {
                        if (block.name() == workstation.name()) return prof
                    }
                }
            }
        }
        return null
    }
}
