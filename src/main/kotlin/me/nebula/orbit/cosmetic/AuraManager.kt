package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task

class AuraManager(private val listener: CosmeticListener) {

    private var task: Task? = null
    private var tickCounter = 0L

    fun install() {
        task = repeat(1) { tick() }
    }

    fun uninstall() {
        task?.cancel()
        task = null
    }

    private fun tick() {
        tickCounter++
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            if ((tickCounter + player.uuid.hashCode()) % AURA_INTERVAL_TICKS != 0L) continue
            val instance = player.instance ?: continue
            val data = CosmeticDataCache.get(player.uuid) ?: continue
            val auraId = data.equipped[CosmeticCategory.AURA.name] ?: continue
            if (!listener.isAllowed(CosmeticCategory.AURA, auraId)) continue
            val level = data.owned[auraId] ?: 1
            CosmeticApplier.spawnAuraParticles(instance, player.position, auraId, level, ownerUuid = player.uuid)
        }
    }

    private companion object {
        const val AURA_INTERVAL_TICKS = 5L
    }
}
