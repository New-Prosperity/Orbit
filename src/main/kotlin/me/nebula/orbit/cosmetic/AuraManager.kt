package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task

object AuraManager {

    private var task: Task? = null

    fun install() {
        task = repeat(5) { tick() }
    }

    fun uninstall() {
        task?.cancel()
        task = null
    }

    private fun tick() {
        for (instance in MinecraftServer.getInstanceManager().instances) {
            for (player in instance.players) {
                val data = CosmeticDataCache.get(player.uuid) ?: continue
                val auraId = data.equipped[CosmeticCategory.AURA.name] ?: continue
                if (!CosmeticListener.isAllowed(CosmeticCategory.AURA, auraId)) continue
                val level = data.owned[auraId] ?: 1
                CosmeticApplier.spawnAuraParticles(instance, player.position, auraId, level, ownerUuid = player.uuid)
            }
        }
    }
}
