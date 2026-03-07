package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticStore
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.TaskSchedule

object AuraManager {

    fun install() {
        MinecraftServer.getSchedulerManager()
            .buildTask { tick() }
            .repeat(TaskSchedule.tick(5))
            .schedule()
    }

    private fun tick() {
        for (instance in MinecraftServer.getInstanceManager().instances) {
            for (player in instance.players) {
                val data = CosmeticStore.load(player.uuid) ?: continue
                val auraId = data.equipped[CosmeticCategory.AURA.name] ?: continue
                if (!CosmeticListener.isAllowed(CosmeticCategory.AURA, auraId)) continue
                val level = data.owned[auraId] ?: 1
                CosmeticApplier.spawnAuraParticles(instance, player.position, auraId, level, ownerUuid = player.uuid)
            }
        }
    }
}
