package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticDefinition
import me.nebula.gravity.messaging.CosmeticEquippedMessage
import me.nebula.gravity.messaging.CosmeticUnlockedMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.orbit.Orbit
import net.minestom.server.entity.Player

object CosmeticEvents {

    fun publishUnlock(player: Player, definition: CosmeticDefinition) {
        try {
            NetworkMessenger.publish(CosmeticUnlockedMessage(
                playerId = player.uuid,
                playerName = player.username,
                cosmeticId = definition.id,
                category = definition.category.name,
                rarity = definition.rarity.name,
                serverName = Orbit.serverName,
                unlockedAt = System.currentTimeMillis(),
            ))
        } catch (_: Throwable) {
        }
    }

    fun publishEquip(player: Player, definition: CosmeticDefinition) {
        try {
            NetworkMessenger.publish(CosmeticEquippedMessage(
                playerId = player.uuid,
                playerName = player.username,
                cosmeticId = definition.id,
                category = definition.category.name,
                serverName = Orbit.serverName,
                equippedAt = System.currentTimeMillis(),
            ))
        } catch (_: Throwable) {
        }
    }
}
